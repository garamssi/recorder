package io.rami.screenrecorder.data.di

import android.os.Process
import android.os.SystemClock
import android.util.Log
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.rami.screenrecorder.data.recorder.RecordingFileStore
import io.rami.screenrecorder.data.storage.AbandonedPublishCleaner
import io.rami.screenrecorder.data.storage.MediaMetadataRecordingReader
import io.rami.screenrecorder.data.storage.MediaStorePublishTarget
import io.rami.screenrecorder.data.storage.MediaStoreRecordingFileStore
import io.rami.screenrecorder.data.storage.PublishTarget
import io.rami.screenrecorder.data.storage.RecordingMetadataReader
import io.rami.screenrecorder.data.storage.RecordingPublisher
import io.rami.screenrecorder.data.storage.processStartEpochSeconds
import javax.inject.Singleton

/**
 * 녹화본 저장·발행 경계 바인딩 (기능명세서 6.1절).
 *
 * 발행은 정책([RecordingPublisher])과 플랫폼([PublishTarget], [RecordingMetadataReader])으로
 * 갈라져 있고, 그 조립을 여기서 한다. 구현을 코드에 박아 두면 계측 테스트에서 플랫폼 절반만
 * 갈아 끼울 수 없다 (CLAUDE.md 3절).
 */
@Module
@InstallIn(SingletonComponent::class)
internal abstract class StorageBindsModule {
    @Binds
    abstract fun bindFileStore(implementation: MediaStoreRecordingFileStore): RecordingFileStore

    @Binds
    abstract fun bindPublishTarget(implementation: MediaStorePublishTarget): PublishTarget

    @Binds
    abstract fun bindRecordingMetadataReader(implementation: MediaMetadataRecordingReader): RecordingMetadataReader
}

/** [RecordingPublisher] 조립. 기본 시계를 쓰므로 @Inject 대신 여기서 만든다 (Hilt 는 기본 파라미터를 다루지 못한다). */
@Module
@InstallIn(SingletonComponent::class)
internal object StorageProvidesModule {
    @Provides
    @Singleton
    fun provideRecordingPublisher(
        target: PublishTarget,
        metadataReader: RecordingMetadataReader,
    ): RecordingPublisher =
        RecordingPublisher(
            target = target,
            metadataReader = metadataReader,
            // 발행이 왜 2~4분씩 걸리는지는 단계를 갈라 재야 안다 (CLAUDE.md 8절).
            // release 빌드에서는 R8 규칙이 Log.i 를 걷어낸다.
            onPhaseMeasured = { phase, millis -> Log.i(PUBLISH_LOG_TAG, "발행 단계 $phase: ${millis}ms") },
        )

    /**
     * 정리기는 프로세스 시작 시각을 **한 번만** 재어 들고 있다.
     *
     * 정리할 때마다 다시 재면, 부팅 뒤 NTP 보정으로 벽시계가 앞으로 점프했을 때 기준선이 같이
     * 밀려 진행 중인 발행·압축을 지운다 (기능명세서 6.1절 [결정]). 싱글턴 생성 시점이 프로세스
     * 시작보다 늦더라도 환산은 Process.getStartElapsedRealtime() 기준이라 값이 흔들리지 않는다.
     */
    @Provides
    @Singleton
    fun provideAbandonedPublishCleaner(target: PublishTarget): AbandonedPublishCleaner =
        AbandonedPublishCleaner(
            target = target,
            processStartedAtEpochSeconds =
                processStartEpochSeconds(
                    nowEpochMillis = System.currentTimeMillis(),
                    elapsedRealtimeMillis = SystemClock.elapsedRealtime(),
                    processStartElapsedRealtimeMillis = Process.getStartElapsedRealtime(),
                ),
        )

    private const val PUBLISH_LOG_TAG = "RecordingPublish"
}
