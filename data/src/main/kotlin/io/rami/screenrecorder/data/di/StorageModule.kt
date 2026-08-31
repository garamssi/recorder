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
     * 이 프로세스가 시작된 벽시계 시각(초).
     *
     * MediaStore 의 `DATE_ADDED` 가 벽시계 초 단위라 같은 축으로 환산해야 견줄 수 있다.
     * 싱글턴 생성 시각을 쓰면 안 된다 — 압축 워커가 이 싱글턴보다 먼저 레코드를 만들 수 있고,
     * 그러면 진행 중인 압축을 버려진 것으로 오판한다.
     */
    @Provides
    @Singleton
    fun provideAbandonedPublishCleaner(target: PublishTarget): AbandonedPublishCleaner =
        AbandonedPublishCleaner(target) {
            val uptimeMillis = SystemClock.elapsedRealtime() - Process.getStartElapsedRealtime()
            (System.currentTimeMillis() - uptimeMillis) / MILLIS_PER_SECOND
        }

    private const val MILLIS_PER_SECOND = 1_000L

    private const val PUBLISH_LOG_TAG = "RecordingPublish"
}
