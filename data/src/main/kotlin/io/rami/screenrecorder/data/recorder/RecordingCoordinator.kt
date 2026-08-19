package io.rami.screenrecorder.data.recorder

import android.media.MediaFormat
import io.rami.screenrecorder.domain.model.AutoBitratePolicy
import io.rami.screenrecorder.domain.model.BitrateOption
import io.rami.screenrecorder.domain.model.Recording
import io.rami.screenrecorder.domain.model.RecordingConfig
import io.rami.screenrecorder.domain.model.RecordingState
import io.rami.screenrecorder.domain.repository.RecordingSessionRepository
import io.rami.screenrecorder.domain.session.MonotonicClock
import io.rami.screenrecorder.domain.session.PauseAwareStopwatch
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 녹화 세션 오케스트레이터 — [RecordingSessionRepository]의 구현.
 *
 * 캡처/인코더/먹서 어댑터를 조율하고 상태 전이, 카운트다운, 일시정지 PTS 보정,
 * 안전 마무리(시스템 중단 포함)를 담당한다 (기능명세서 3, 11절).
 */
class RecordingCoordinator(
    private val sessionFactory: RecorderSessionFactory,
    private val fileStore: RecordingFileStore,
    private val fileNameProvider: FileNameProvider,
    private val displayInfo: DisplayInfoProvider,
    private val clock: MonotonicClock,
    private val scope: CoroutineScope,
    /** 블로킹 어댑터 호출(코덱 정리 대기, 파일 IO)을 위임할 디스패처. */
    private val blockingDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : RecordingSessionRepository {
    private val mutableState = MutableStateFlow<RecordingState>(RecordingState.Idle)
    private val mutableCompleted = MutableSharedFlow<Recording>(extraBufferCapacity = 1)

    private val countdown = CountdownRunner()
    private var activeSession: ActiveSession? = null

    override val state: StateFlow<RecordingState> = mutableState

    override val completedRecordings: Flow<Recording> = mutableCompleted

    override suspend fun start(config: RecordingConfig) {
        check(activeSession == null) { "이미 진행 중인 세션이 있다" }
        val aborted =
            countdown.run(config.countdown.seconds) { remaining ->
                mutableState.value = RecordingState.CountingDown(remaining)
            }
        if (aborted) {
            mutableState.value = RecordingState.Idle
            return
        }
        startPipeline(config)
    }

    override fun skipCountdown() {
        countdown.skip()
    }

    override suspend fun stop() {
        countdown.abort()
        finalizeSession()
    }

    override suspend fun pause() {
        val session = activeSession ?: return
        session.encoder.setSuspended(true)
        session.audioRecorder?.setSuspended(true)
        session.pauseOffset.onPause(nowUs())
        session.stopwatch.pause()
        mutableState.value = RecordingState.Paused(session.stopwatch.elapsed())
    }

    override suspend fun resume() {
        val session = activeSession ?: return
        session.pauseOffset.onResume(nowUs())
        session.stopwatch.resume()
        session.encoder.setSuspended(false)
        session.audioRecorder?.setSuspended(false)
        session.encoder.requestKeyFrame()
        mutableState.value = RecordingState.Recording(session.stopwatch.elapsed())
    }

    private suspend fun startPipeline(config: RecordingConfig) {
        val resolution = config.resolution.resolve(displayInfo.currentResolution())
        val bitrateBps =
            when (val bitrate = config.bitrate) {
                is BitrateOption.Auto -> AutoBitratePolicy.bitrateBpsFor(resolution, config.frameRate)
                is BitrateOption.Fixed -> bitrate.megabitsPerSecond * BPS_PER_MBPS
            }
        val fileName = fileNameProvider.nextFileName()
        val tempFile = fileStore.createTempFile(fileName)
        val muxer = sessionFactory.createMuxer()
        try {
            withContext(blockingDispatcher) {
                muxer.open(tempFile)
                // 캡처 소스가 세션 MediaProjection을 만들므로 오디오 레코더보다 먼저 생성한다.
                val captureSource = sessionFactory.createCaptureSource()
                val session =
                    ActiveSession(
                        media =
                            SessionMedia(
                                encoder = sessionFactory.createVideoEncoder(),
                                capture = captureSource,
                                audioRecorder = sessionFactory.createAudioRecorder(config),
                                muxer = muxer,
                            ),
                        tempFile = tempFile,
                        fileName = fileName,
                        stopwatch = PauseAwareStopwatch(clock),
                    )
                val surface =
                    session.encoder.prepare(
                        VideoEncoderConfig(resolution, config.frameRate.fps, bitrateBps, config.codec),
                        session.encoderListener(),
                    )
                session.encoder.start()
                session.capture.start(surface, resolution, session.captureListener())
                session.audioRecorder?.start(session.audioListener(), session.pauseOffset)
                session.stopwatch.start()
                activeSession = session
            }
        } catch (
            // 어떤 시작 실패든 열린 먹서를 정리하고 원인을 그대로 전파한다 (증상 은폐 아님).
            @Suppress("TooGenericExceptionCaught") startFailure: Exception,
        ) {
            muxer.close()
            mutableState.value = RecordingState.Idle
            throw startFailure
        }
        val session = checkNotNull(activeSession)
        mutableState.value = RecordingState.Recording(session.stopwatch.elapsed())
        session.ticker = scope.launch { tickElapsed(session) }
    }

    private suspend fun tickElapsed(session: ActiveSession) {
        while (true) {
            delay(ELAPSED_TICK_MS)
            if (mutableState.value is RecordingState.Recording) {
                mutableState.value = RecordingState.Recording(session.stopwatch.elapsed())
            }
        }
    }

    /**
     * 어떤 중지 경로(수동/시스템/오류)든 파일을 안전하게 마무리한다 (기능명세서 11.1절).
     *
     * [ActiveSession.finalizing] CAS로 동시 진입(onError + onStoppedBySystem 등)을 차단하고,
     * 중첩 finally로 개별 정리 실패에도 나머지 자원 해제와 상태 복귀를 보장한다.
     */
    private suspend fun finalizeSession() {
        val session = activeSession ?: return
        if (!session.finalizing.compareAndSet(false, true)) return
        mutableState.value = RecordingState.Stopping
        session.ticker?.cancel()
        try {
            val recording =
                withContext(blockingDispatcher) {
                    try {
                        session.capture.stop()
                    } finally {
                        try {
                            session.audioRecorder?.stopAndRelease()
                        } finally {
                            try {
                                session.encoder.stopAndRelease()
                            } finally {
                                session.muxer.close()
                            }
                        }
                    }
                    fileStore.publish(session.tempFile, session.fileName)
                }
            mutableCompleted.emit(recording)
        } finally {
            activeSession = null
            mutableState.value = RecordingState.Idle
        }
    }

    private fun nowUs(): Long = clock.elapsedRealtimeMillis() * US_PER_MS

    /** 한 세션의 미디어 파이프라인 구성 요소 묶음. */
    private class SessionMedia(
        val encoder: VideoEncoder,
        val capture: ScreenCaptureSource,
        val audioRecorder: AudioRecorder?,
        val muxer: MuxerWriter,
    )

    private inner class ActiveSession(
        media: SessionMedia,
        val tempFile: File,
        val fileName: String,
        val stopwatch: PauseAwareStopwatch,
    ) {
        val encoder = media.encoder
        val capture = media.capture
        val audioRecorder = media.audioRecorder
        val muxer = media.muxer
        val pauseOffset = PauseOffsetTracker()
        var ticker: Job? = null
        val finalizing = AtomicBoolean(false)

        private val videoCorrector = PresentationTimeCorrector(pauseOffset)
        private val audioCorrector = PresentationTimeCorrector(pauseOffset)
        private val trackGate =
            MuxerTrackGate(
                muxer = muxer,
                expectedTrackCount = if (audioRecorder != null) 2 else 1,
            )

        fun encoderListener() =
            object : VideoEncoder.Listener {
                override fun onOutputFormatReady(format: MediaFormat) {
                    trackGate.registerVideoTrack(format)
                }

                override fun onSample(sample: EncodedSample) {
                    val correctedPtsUs = videoCorrector.correct(sample.presentationTimeUs) ?: return
                    trackGate.writeVideo(sample.copy(presentationTimeUs = correctedPtsUs))
                }

                override fun onError(error: Throwable) {
                    scope.launch { finalizeSession() }
                }
            }

        fun audioListener() =
            object : AudioRecorder.Listener {
                override fun onOutputFormatReady(format: MediaFormat) {
                    trackGate.registerAudioTrack(format)
                }

                override fun onSample(sample: EncodedSample) {
                    val correctedPtsUs = audioCorrector.correct(sample.presentationTimeUs) ?: return
                    trackGate.writeAudio(sample.copy(presentationTimeUs = correctedPtsUs))
                }

                override fun onError(error: Throwable) {
                    scope.launch { finalizeSession() }
                }
            }

        fun captureListener() =
            object : ScreenCaptureSource.Listener {
                override fun onStoppedBySystem() {
                    scope.launch { finalizeSession() }
                }

                override fun onContentResize(
                    width: Int,
                    height: Int,
                ) {
                    // 회전/리사이즈 대응은 Stage 5(회전 정책) / Stage 8(부분 영역)에서 구현한다.
                }
            }
    }

    private companion object {
        const val ELAPSED_TICK_MS = 1_000L
        const val US_PER_MS = 1_000L
        const val BPS_PER_MBPS = 1_000_000
    }
}
