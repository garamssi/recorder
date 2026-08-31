package io.rami.screenrecorder.data.recorder

import android.media.MediaFormat
import android.util.Log
import io.rami.screenrecorder.domain.model.AutoBitratePolicy
import io.rami.screenrecorder.domain.model.AutoStopReason
import io.rami.screenrecorder.domain.model.BitrateOption
import io.rami.screenrecorder.domain.model.CaptureMode
import io.rami.screenrecorder.domain.model.CropGeometry
import io.rami.screenrecorder.domain.model.PauseTimeoutPolicy
import io.rami.screenrecorder.domain.model.RecordableTimeEstimator
import io.rami.screenrecorder.domain.model.Recording
import io.rami.screenrecorder.domain.model.RecordingConfig
import io.rami.screenrecorder.domain.model.RecordingSessionEvent
import io.rami.screenrecorder.domain.model.RecordingState
import io.rami.screenrecorder.domain.model.Resolution
import io.rami.screenrecorder.domain.model.TimeLimit
import io.rami.screenrecorder.domain.repository.RecordingSessionRepository
import io.rami.screenrecorder.domain.repository.StorageRepository
import io.rami.screenrecorder.domain.session.MonotonicClock
import io.rami.screenrecorder.domain.session.PauseAwareStopwatch
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/** 코디네이터가 쓰는 세션 무관 의존성 묶음 (DI 조립 단순화용). */
class RecorderDependencies
    @javax.inject.Inject
    constructor(
        val fileStore: RecordingFileStore,
        val fileNameProvider: FileNameProvider,
        val displayInfo: DisplayInfoProvider,
        val clock: MonotonicClock,
        val storageRepository: StorageRepository,
    )

/**
 * 녹화 세션 오케스트레이터 — [RecordingSessionRepository]의 구현.
 *
 * 캡처/인코더/먹서 어댑터를 조율하고 상태 전이, 카운트다운, 일시정지 PTS 보정,
 * 자동 안전 중지(타이머/저장 공간/일시정지 방치)와 안전 마무리를 담당한다 (기능명세서 3, 11절).
 */
class RecordingCoordinator(
    private val sessionFactory: RecorderSessionFactory,
    dependencies: RecorderDependencies,
    private val scope: CoroutineScope,
    /** 블로킹 어댑터 호출(코덱 정리 대기, 파일 IO)을 위임할 디스패처. */
    private val blockingDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : RecordingSessionRepository {
    private val fileStore = dependencies.fileStore
    private val fileNameProvider = dependencies.fileNameProvider
    private val displayInfo = dependencies.displayInfo
    private val clock = dependencies.clock
    private val storageRepository = dependencies.storageRepository

    private val mutableState = MutableStateFlow<RecordingState>(RecordingState.Idle)
    private val mutableCompleted = MutableSharedFlow<Recording>(extraBufferCapacity = 1)
    private val mutableEvents = MutableSharedFlow<RecordingSessionEvent>(extraBufferCapacity = EVENT_BUFFER)

    private val countdown = CountdownRunner()
    private var activeSession: ActiveSession? = null

    override val state: StateFlow<RecordingState> = mutableState

    override val completedRecordings: Flow<Recording> = mutableCompleted

    override val sessionEvents: Flow<RecordingSessionEvent> = mutableEvents

    override suspend fun start(config: RecordingConfig) {
        check(activeSession == null) { "이미 진행 중인 세션이 있다" }
        val aborted =
            try {
                countdown.run(config.countdown.seconds) { remaining ->
                    mutableState.value = RecordingState.CountingDown(remaining)
                }
            } catch (cancellation: kotlinx.coroutines.CancellationException) {
                // 호출 코루틴이 취소돼도 상태가 CountingDown에 고착되지 않게 복원한다.
                mutableState.value = RecordingState.Idle
                throw cancellation
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

    /**
     * 중지한다.
     *
     * 카운트다운 중이면 [finalizeSession] 은 아무 일도 하지 않는다 — 아직 세션이 없다. 대신
     * [countdown] 중단이 [start] 코루틴을 깨워 그쪽이 상태를 Idle 로 되돌린다. 이 원격 결합이
     * 없으면 카운트다운 중 중지가 상태를 CountingDown 에 고착시킨다.
     */
    override suspend fun stop() {
        countdown.abort()
        finalizeSession()
    }

    override suspend fun pause() {
        val session = activeSession ?: return
        // 회전 이벤트 등 비동기 경로와의 레이스에서 이중 일시정지를 막는다 (Recording에서만 유효).
        if (mutableState.value !is RecordingState.Recording) return
        session.encoder.setSuspended(true)
        session.audioRecorder?.setSuspended(true)
        session.pauseOffset.onPause(clock.elapsedRealtimeMillis() * US_PER_MS)
        session.stopwatch.pause()
        mutableState.value = RecordingState.Paused(session.stopwatch.elapsed(), session.timeLimit)
        session.pauseTimeout = scope.launch { runPauseTimeout() }
    }

    override suspend fun resume() {
        val session = activeSession ?: return
        session.pauseTimeout?.cancel()
        session.pauseTimeout = null
        session.pauseOffset.onResume(clock.elapsedRealtimeMillis() * US_PER_MS)
        session.stopwatch.resume()
        session.encoder.setSuspended(false)
        session.audioRecorder?.setSuspended(false)
        session.encoder.requestKeyFrame()
        mutableState.value = RecordingState.Recording(session.stopwatch.elapsed(), session.timeLimit)
    }

    /** 일시정지 방치 시 예고 후 자동 안전 중지한다 (기능명세서 11.2절 [결정]: 30분, 5분 전 예고). */
    private suspend fun runPauseTimeout() {
        delay(PauseTimeoutPolicy.AUTO_STOP_AFTER - PauseTimeoutPolicy.WARNING_BEFORE)
        mutableEvents.emit(RecordingSessionEvent.PauseTimeoutWarning(PauseTimeoutPolicy.WARNING_BEFORE))
        delay(PauseTimeoutPolicy.WARNING_BEFORE)
        mutableEvents.emit(RecordingSessionEvent.AutoStopped(AutoStopReason.PAUSE_TIMEOUT))
        finalizeSession()
    }

    private suspend fun startPipeline(config: RecordingConfig) {
        val displayResolution = displayInfo.currentResolution()
        val regionMode = config.captureMode as? CaptureMode.Region
        val resolution = encoderResolution(config, regionMode, displayResolution)
        val bitrateBps = bitrateBpsFor(config, resolution)
        val fileName = fileNameProvider.nextFileName()
        val tempFile = fileStore.createTempFile(fileName)
        val muxer = sessionFactory.createMuxer()
        var startedProcessor: FrameProcessor? = null
        var audioSetup: AudioSetup? = null
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
                                audioRecorder =
                                    sessionFactory
                                        .createAudioRecorder(config)
                                        ?.also { audioSetup = it }
                                        ?.recorder,
                                muxer = muxer,
                                frameProcessor =
                                    if (regionMode != null) sessionFactory.createFrameProcessor() else null,
                            ),
                        tempFile = tempFile,
                        fileName = fileName,
                        stopwatch = PauseAwareStopwatch(clock),
                        timeLimit = config.timeLimit,
                    )
                startedProcessor = session.frameProcessor
                session.startCapture(
                    CaptureWiring(config, resolution, displayResolution, bitrateBps, regionMode),
                )
                activeSession = session
            }
        } catch (
            // 어떤 시작 실패든 열린 자원(먹서/GPU 프로세서)을 정리하고 원인을 그대로 전파한다.
            @Suppress("TooGenericExceptionCaught") startFailure: Exception,
        ) {
            try {
                startedProcessor?.stop()
            } finally {
                muxer.close()
                mutableState.value = RecordingState.Idle
            }
            throw startFailure
        }
        val session = checkNotNull(activeSession)
        audioSetup?.microphoneFallback?.let {
            mutableEvents.emit(RecordingSessionEvent.MicrophoneFellBack(it))
        }
        mutableState.value = RecordingState.Recording(session.stopwatch.elapsed(), session.timeLimit)
        session.ticker = scope.launch { tickElapsed(session) }
        session.storageWatch = scope.launch { watchStorage() }
    }

    /** 한 세션의 캡처 배선 입력 묶음. */
    private class CaptureWiring(
        val config: RecordingConfig,
        val resolution: Resolution,
        val displayResolution: Resolution,
        val bitrateBps: Int,
        val regionMode: CaptureMode.Region?,
    )

    private suspend fun tickElapsed(session: ActiveSession) {
        val watcher = TimeLimitWatcher(session.timeLimit)
        while (true) {
            delay(ELAPSED_TICK_MS)
            if (mutableState.value !is RecordingState.Recording) continue
            val elapsed = session.stopwatch.elapsed()
            mutableState.value = RecordingState.Recording(elapsed, session.timeLimit)
            when (val verdict = watcher.onTick(elapsed)) {
                is TimeLimitWatcher.Verdict.Warn ->
                    mutableEvents.emit(RecordingSessionEvent.TimeLimitWarning(verdict.remaining))

                is TimeLimitWatcher.Verdict.Stop -> {
                    mutableEvents.emit(RecordingSessionEvent.AutoStopped(AutoStopReason.TIME_LIMIT_REACHED))
                    finalizeSession()
                    return
                }

                is TimeLimitWatcher.Verdict.Continue -> Unit
            }
        }
    }

    /** 저장 공간이 유지 임계 이하로 떨어지면 자동 안전 중지한다 (기능명세서 11.1절). */
    private suspend fun watchStorage() {
        val lowStorage =
            storageRepository.observeAvailableBytes().firstOrNull { availableBytes ->
                availableBytes <= RecordableTimeEstimator.MIN_FREE_BYTES_TO_CONTINUE
            }
        if (lowStorage == null) return
        mutableEvents.emit(RecordingSessionEvent.AutoStopped(AutoStopReason.STORAGE_LOW))
        finalizeSession()
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
        // 녹화가 끝난 시점의 길이를 붙들어 둔다. 저장 화면이 결과물의 길이를 보여 주고,
        // 진행률이 갱신될 때마다 같은 값을 다시 실어 보내야 한다 (기능명세서 2.1절 [결정]).
        val recordedLength = session.stopwatch.elapsed()
        mutableState.value = RecordingState.Stopping(recordedLength, session.fileName)
        session.ticker?.cancel()
        session.storageWatch?.cancel()
        session.pauseTimeout?.cancel()
        // 감시 코루틴(ticker/storageWatch/pauseTimeout) 스스로 finalize를 호출하는 경우
        // 위 cancel()이 자기 자신을 취소하므로, 이후 정리는 취소 불가로 보호한다.
        withContext(NonCancellable) {
            try {
                val recording =
                    withContext(blockingDispatcher) {
                        try {
                            session.capture.stop()
                        } finally {
                            try {
                                session.frameProcessor?.stop()
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
                        }
                        fileStore.publish(session.tempFile, session.fileName) { progress ->
                            mutableState.value =
                                RecordingState.Stopping(recordedLength, session.fileName, progress)
                        }
                    }
                // 프레임이 하나도 인코딩되지 않은 빈 세션은 저장할 내용이 없으므로 완료를 알리지 않는다.
                recording?.let { mutableCompleted.emit(it) }
                // 어떤 실패든 사용자에게는 알려야 하므로 넓게 받는다.
            } catch (
                @Suppress("TooGenericExceptionCaught") failure: Exception,
            ) {
                // 실패를 삼키면 게이지가 도중에 사라지고 사용자는 저장된 줄 안다
                // (기능명세서 2.1절 [결정]). 임시 파일은 남아 다음 실행에서 복구를 제안한다.
                Log.w(LOG_TAG, "녹화본 발행 실패 — 임시 파일을 남긴다", failure)
                mutableEvents.emit(RecordingSessionEvent.SaveFailed)
            } finally {
                activeSession = null
                mutableState.value = RecordingState.Idle
            }
        }
    }

    /** 한 세션의 미디어 파이프라인 구성 요소 묶음. */
    private class SessionMedia(
        val encoder: VideoEncoder,
        val capture: ScreenCaptureSource,
        val audioRecorder: AudioRecorder?,
        val muxer: MuxerWriter,
        val frameProcessor: FrameProcessor?,
    )

    private inner class ActiveSession(
        media: SessionMedia,
        val tempFile: File,
        val fileName: String,
        val stopwatch: PauseAwareStopwatch,
        // 세션을 멈출 시각은 시작할 때 정해진다. 설정이 그 뒤에 바뀌어도 이 값은 그대로다
        // (기능명세서 11.4절: 녹화 중 해제·연장은 1차 범위 제외).
        val timeLimit: TimeLimit,
    ) {
        val encoder = media.encoder
        val capture = media.capture
        val audioRecorder = media.audioRecorder
        val muxer = media.muxer
        val frameProcessor = media.frameProcessor
        val pauseOffset = PauseOffsetTracker()
        var ticker: Job? = null
        var storageWatch: Job? = null
        var pauseTimeout: Job? = null
        val finalizing = AtomicBoolean(false)

        private val videoCorrector = PresentationTimeCorrector(pauseOffset)
        private val audioCorrector = PresentationTimeCorrector(pauseOffset)
        private val trackGate =
            MuxerTrackGate(
                muxer = muxer,
                expectedTrackCount = if (audioRecorder != null) 2 else 1,
            )

        /** 인코더/캡처/오디오를 배선한다. 부분 영역이면 GPU 프로세서를 경유한다 (명세 2.2절). */
        fun startCapture(wiring: CaptureWiring) {
            val encoderSurface =
                encoder.prepare(
                    VideoEncoderConfig(
                        wiring.resolution,
                        wiring.config.frameRate.fps,
                        wiring.bitrateBps,
                        wiring.config.codec,
                    ),
                    encoderListener(),
                )
            encoder.start()
            val regionMode = wiring.regionMode
            if (regionMode != null && frameProcessor != null) {
                // 부분 영역: 디스플레이 전체를 프로세서 입력에 캡처하고 GPU에서 크롭한다
                val geometry =
                    CropGeometry.compute(
                        sourceSize = wiring.displayResolution,
                        cropRegion = regionMode.region,
                        outputSize = wiring.resolution,
                    )
                val processorInput =
                    frameProcessor.start(encoderSurface, wiring.displayResolution, geometry)
                capture.start(processorInput, wiring.displayResolution, captureListener())
            } else {
                capture.start(encoderSurface, wiring.resolution, captureListener())
            }
            audioRecorder?.start(audioListener(), pauseOffset)
            stopwatch.start()
        }

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
                    // 전체 화면/단일 앱은 VirtualDisplay 미러링이 스케일을 흡수한다 (명세 5절).
                    // 부분 영역은 회전 시 좌표가 무효화되므로 자동 일시정지한다 (명세 5절 [결정]).
                    if (frameProcessor == null) return
                    scope.launch {
                        if (mutableState.value is RecordingState.Recording) {
                            pause()
                            mutableEvents.emit(RecordingSessionEvent.RegionInvalidatedByRotation)
                        }
                    }
                }
            }
    }

    private companion object {
        private const val LOG_TAG = "RecordingCoordinator"

        const val ELAPSED_TICK_MS = 1_000L
        const val US_PER_MS = 1_000L
        const val EVENT_BUFFER = 16
    }
}

/** 인코더 출력 해상도. 부분 영역이면 짝수 정렬된 영역 크기다 (H.264 색차 정렬). */
private fun encoderResolution(
    config: RecordingConfig,
    regionMode: CaptureMode.Region?,
    displayResolution: Resolution,
): Resolution =
    if (regionMode != null) {
        Resolution(
            evenDown(regionMode.region.width),
            evenDown(regionMode.region.height),
        )
    } else {
        config.resolution.resolve(displayResolution)
    }

private fun bitrateBpsFor(
    config: RecordingConfig,
    resolution: Resolution,
): Int =
    when (val bitrate = config.bitrate) {
        is BitrateOption.Auto -> AutoBitratePolicy.bitrateBpsFor(resolution, config.frameRate)
        is BitrateOption.Fixed -> bitrate.megabitsPerSecond * BPS_PER_MBPS
    }

/** H.264/HEVC 색차 서브샘플링 정렬을 위한 짝수 내림. */
private fun evenDown(value: Int): Int = value - (value % 2)

private const val BPS_PER_MBPS = 1_000_000
