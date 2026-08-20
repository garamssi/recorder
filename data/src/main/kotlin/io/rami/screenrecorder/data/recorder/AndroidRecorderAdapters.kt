package io.rami.screenrecorder.data.recorder

import android.content.Context
import android.media.projection.MediaProjectionManager
import android.os.SystemClock
import dagger.hilt.android.qualifiers.ApplicationContext
import io.rami.screenrecorder.data.audio.AacAudioRecorder
import io.rami.screenrecorder.data.audio.createMicrophonePcmSource
import io.rami.screenrecorder.data.audio.createPlaybackCapturePcmSource
import io.rami.screenrecorder.domain.model.AudioSource
import io.rami.screenrecorder.domain.model.FileNamePrefix
import io.rami.screenrecorder.domain.model.RecordingConfig
import io.rami.screenrecorder.domain.model.RecordingFileNameFactory
import io.rami.screenrecorder.domain.model.Resolution
import io.rami.screenrecorder.domain.session.MonotonicClock
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton

/** [MonotonicClock]의 SystemClock 구현 (기능명세서 11.4절: elapsedRealtime 기준). */
class AndroidMonotonicClock
    @Inject
    constructor() : MonotonicClock {
        override fun elapsedRealtimeMillis(): Long = SystemClock.elapsedRealtime()
    }

/** 현재 디스플레이 해상도 제공자. */
class WindowMetricsDisplayInfoProvider
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : DisplayInfoProvider {
        override fun currentResolution(): Resolution {
            val bounds =
                context
                    .getSystemService(android.view.WindowManager::class.java)
                    .maximumWindowMetrics
                    .bounds
            return Resolution(bounds.width(), bounds.height())
        }
    }

/** 기본 접두어 기반 파일명 결정자. 설정 화면(Stage 6)에서 접두어 설정으로 대체된다. */
class DefaultFileNameProvider
    @Inject
    constructor(
        private val fileStore: RecordingFileStore,
    ) : FileNameProvider {
        override suspend fun nextFileName(): String =
            RecordingFileNameFactory.create(
                prefix = FileNamePrefix.DEFAULT,
                timestamp = LocalDateTime.now(),
                existingNames = fileStore.existingFileNames(),
            )
    }

/** 동의 토큰으로 세션별 어댑터를 생성하는 [RecorderSessionFactory] 구현. */
@Singleton
class ProjectionRecorderSessionFactory
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val tokenHolder: MediaProjectionTokenHolder,
    ) : RecorderSessionFactory {
        /** 현재 세션의 프로젝션. 내부 오디오 캡처가 공유한다 (메모리 전용). */
        private var currentSessionProjection: android.media.projection.MediaProjection? = null

        override fun createVideoEncoder(): VideoEncoder = MediaCodecVideoEncoder()

        override fun createCaptureSource(): ScreenCaptureSource {
            val (resultCode, data) = tokenHolder.consume()
            val projection =
                checkNotNull(
                    context
                        .getSystemService(MediaProjectionManager::class.java)
                        .getMediaProjection(resultCode, data),
                ) { "MediaProjection 생성 실패" }
            currentSessionProjection = projection
            return MediaProjectionCaptureSource(
                projection = projection,
                densityDpi = context.resources.configuration.densityDpi,
            )
        }

        override fun createMuxer(): MuxerWriter = Media3FragmentedMp4Writer()

        override fun createFrameProcessor(): FrameProcessor = GlFrameProcessor()

        override fun createAudioRecorder(config: RecordingConfig): AudioRecorder? {
            val needsInternal =
                config.audioSource == AudioSource.INTERNAL ||
                    config.audioSource == AudioSource.INTERNAL_AND_MICROPHONE
            val needsMicrophone =
                config.audioSource == AudioSource.MICROPHONE ||
                    config.audioSource == AudioSource.INTERNAL_AND_MICROPHONE
            if (!needsInternal && !needsMicrophone) return null
            val internalSource =
                if (needsInternal) {
                    val projection = checkNotNull(currentSessionProjection) { "세션 프로젝션이 없다" }
                    createPlaybackCapturePcmSource(projection)
                } else {
                    null
                }
            val microphoneSource =
                if (needsMicrophone) {
                    createMicrophonePcmSource(context, config.microphoneDevice)
                } else {
                    null
                }
            return AacAudioRecorder(
                internalSource = internalSource,
                microphoneSource = microphoneSource,
                internalGain = config.internalVolume.asGain(),
                microphoneGain = config.microphoneVolume.asGain(),
            )
        }
    }
