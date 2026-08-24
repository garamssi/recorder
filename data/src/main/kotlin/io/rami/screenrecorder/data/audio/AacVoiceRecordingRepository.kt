package io.rami.screenrecorder.data.audio

import android.content.Context
import android.media.AudioManager
import android.media.MediaFormat
import android.media.MediaMuxer
import dagger.hilt.android.qualifiers.ApplicationContext
import io.rami.screenrecorder.data.recorder.AudioRecorder
import io.rami.screenrecorder.data.recorder.EncodedSample
import io.rami.screenrecorder.data.recorder.PauseOffsetTracker
import io.rami.screenrecorder.data.storage.MediaStoreQuickCaptureStore
import io.rami.screenrecorder.domain.model.MicrophoneDevice
import io.rami.screenrecorder.domain.model.RecordingFileNameFactory
import io.rami.screenrecorder.domain.model.VoiceMemo
import io.rami.screenrecorder.domain.model.VoiceRecordingState
import io.rami.screenrecorder.domain.repository.SettingsRepository
import io.rami.screenrecorder.domain.repository.VoiceRecordingRepository
import io.rami.screenrecorder.domain.session.MonotonicClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * 마이크만 녹음해 m4a로 저장한다 (기능명세서 13절).
 *
 * 화면 녹화 파이프라인의 [AacAudioRecorder]를 그대로 재사용하고, 출력은 MediaMuxer로
 * 오디오 트랙 하나만 담은 MP4 컨테이너에 쓴다. 화면 캡처를 쓰지 않으므로
 * MediaProjection 동의는 필요 없다 (microphone 타입 포그라운드 서비스만 필요).
 */
@Singleton
class AacVoiceRecordingRepository
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val settingsRepository: SettingsRepository,
        private val quickCaptureStore: MediaStoreQuickCaptureStore,
        private val clock: MonotonicClock,
        private val scope: CoroutineScope,
    ) : VoiceRecordingRepository {
        private val mutableState = MutableStateFlow<VoiceRecordingState>(VoiceRecordingState.Idle)
        private var session: VoiceSession? = null
        private var tickerJob: Job? = null
        private val microphoneFallbacks =
            MutableSharedFlow<MicrophoneDevice>(extraBufferCapacity = FALLBACK_BUFFER)

        override fun observeState(): Flow<VoiceRecordingState> = mutableState.asStateFlow()

        override fun observeMicrophoneFallbacks(): Flow<MicrophoneDevice> = microphoneFallbacks

        override suspend fun start() {
            check(session == null) { "이미 음성 녹음 중이다" }
            val settings = settingsRepository.settings.first()
            val fileName =
                RecordingFileNameFactory.create(
                    prefix = settings.fileNamePrefix,
                    timestamp = LocalDateTime.now(),
                    existingNames = quickCaptureStore.existingAudioNames(),
                    extension = RecordingFileNameFactory.AUDIO_EXTENSION,
                )
            val tempFile = File(context.cacheDir, TEMP_DIRECTORY).apply { mkdirs() }.resolve(fileName)
            // 블루투스 헤드셋 마이크는 SCO/LE 링크가 열린 뒤에야 입력 장치로 존재한다.
            val router =
                MicrophoneRouter(
                    AndroidCommunicationDeviceController(
                        audioManager = context.getSystemService(AudioManager::class.java),
                        callbackExecutor = context.mainExecutor,
                    ),
                )
            val routing = router.activate(settings.recording.microphoneDevice)
            val requestedDevice =
                if (routing == MicrophoneRouting.Unavailable) {
                    MicrophoneDevice.AUTO
                } else {
                    settings.recording.microphoneDevice
                }
            val capture = createMicrophonePcmSource(context, requestedDevice)
            if (routing == MicrophoneRouting.Unavailable || capture.fellBackToSystemDefault) {
                microphoneFallbacks.emit(settings.recording.microphoneDevice)
            }
            val recorder =
                RoutedAudioRecorder(
                    delegate =
                        AacAudioRecorder(
                            internalSource = null,
                            microphoneSource = capture.source,
                            internalGain = 0f,
                            microphoneGain = settings.recording.microphoneVolume.asGain(),
                        ),
                    router = router,
                )
            val newSession = VoiceSession(tempFile, fileName, recorder, clock.elapsedRealtimeMillis())
            recorder.start(newSession, PauseOffsetTracker())
            session = newSession
            startTicker(newSession)
        }

        override suspend fun stop(): VoiceMemo? {
            val current = session ?: return null
            session = null
            tickerJob?.cancel()
            tickerJob = null
            mutableState.value = VoiceRecordingState.Stopping
            val memo =
                try {
                    current.finish()
                    quickCaptureStore.publishVoiceMemo(current.tempFile, current.fileName)
                } finally {
                    mutableState.value = VoiceRecordingState.Idle
                }
            return memo
        }

        /** 경과 시간을 1초 간격으로 반영한다 (UI 타이머용). */
        private fun startTicker(current: VoiceSession) {
            tickerJob =
                scope.launch {
                    while (isActive) {
                        mutableState.value = VoiceRecordingState.Recording(current.elapsed(clock))
                        delay(TICK_INTERVAL_MILLIS)
                    }
                }
        }

        private companion object {
            const val TEMP_DIRECTORY = "voice"
            const val FALLBACK_BUFFER = 4
            const val TICK_INTERVAL_MILLIS = 1_000L
        }
    }

/**
 * 한 번의 음성 녹음 세션 — AAC 샘플을 MediaMuxer로 쓴다.
 *
 * 콜백은 오디오 스레드에서 오므로 먹서 접근을 모두 동기화한다.
 */
private class VoiceSession(
    val tempFile: File,
    val fileName: String,
    private val recorder: AudioRecorder,
    private val startedAtMillis: Long,
) : AudioRecorder.Listener {
    private val muxer = MediaMuxer(tempFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
    private val muxerLock = Any()
    private var trackIndex = NO_TRACK
    private var started = false

    override fun onOutputFormatReady(format: MediaFormat) {
        synchronized(muxerLock) {
            if (started) return
            trackIndex = muxer.addTrack(format)
            muxer.start()
            started = true
        }
    }

    override fun onSample(sample: EncodedSample) {
        synchronized(muxerLock) {
            // 포맷 확정 전에 도착한 샘플은 쓸 트랙이 없다 — 코덱 설정 데이터이므로 버려도 된다.
            if (!started) return
            val info =
                android.media.MediaCodec.BufferInfo().apply {
                    set(
                        sample.buffer.position(),
                        sample.buffer.remaining(),
                        sample.presentationTimeUs,
                        if (sample.isKeyFrame) android.media.MediaCodec.BUFFER_FLAG_KEY_FRAME else 0,
                    )
                }
            muxer.writeSampleData(trackIndex, sample.buffer, info)
        }
    }

    override fun onError(error: Throwable) {
        android.util.Log.e(LOG_TAG, "음성 녹음 오류", error)
    }

    /** 시작 이후 경과 시간. */
    fun elapsed(clock: MonotonicClock): Duration = (clock.elapsedRealtimeMillis() - startedAtMillis).milliseconds

    /** 인코더를 멈추고 먹서를 닫는다. */
    fun finish() {
        recorder.stopAndRelease()
        synchronized(muxerLock) {
            if (started) muxer.stop()
            muxer.release()
            started = false
        }
    }

    private companion object {
        const val NO_TRACK = -1
        const val LOG_TAG = "VoiceSession"
    }
}
