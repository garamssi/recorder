package io.rami.screenrecorder.data.recorder

import android.content.Context
import android.media.projection.MediaProjectionManager
import android.os.SystemClock
import dagger.hilt.android.qualifiers.ApplicationContext
import io.rami.screenrecorder.domain.model.FileNamePrefix
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
        override fun createVideoEncoder(): VideoEncoder = MediaCodecVideoEncoder()

        override fun createCaptureSource(): ScreenCaptureSource {
            val (resultCode, data) = tokenHolder.consume()
            val projection =
                checkNotNull(
                    context
                        .getSystemService(MediaProjectionManager::class.java)
                        .getMediaProjection(resultCode, data),
                ) { "MediaProjection 생성 실패" }
            return MediaProjectionCaptureSource(
                projection = projection,
                densityDpi = context.resources.configuration.densityDpi,
            )
        }

        override fun createMuxer(): MuxerWriter = Media3FragmentedMp4Writer()
    }
