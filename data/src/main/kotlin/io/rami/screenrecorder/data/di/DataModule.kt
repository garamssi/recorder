package io.rami.screenrecorder.data.di

import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.rami.screenrecorder.data.recorder.AndroidMonotonicClock
import io.rami.screenrecorder.data.recorder.DefaultFileNameProvider
import io.rami.screenrecorder.data.recorder.DisplayInfoProvider
import io.rami.screenrecorder.data.recorder.FileNameProvider
import io.rami.screenrecorder.data.recorder.ProjectionRecorderSessionFactory
import io.rami.screenrecorder.data.recorder.RecorderDependencies
import io.rami.screenrecorder.data.recorder.RecorderSessionFactory
import io.rami.screenrecorder.data.recorder.RecordingCoordinator
import io.rami.screenrecorder.data.recorder.RecordingFileStore
import io.rami.screenrecorder.data.recorder.WindowMetricsDisplayInfoProvider
import io.rami.screenrecorder.data.storage.DeviceStorageRepository
import io.rami.screenrecorder.data.storage.MediaStoreRecordingFileStore
import io.rami.screenrecorder.domain.repository.RecordingSessionRepository
import io.rami.screenrecorder.domain.repository.StorageRepository
import io.rami.screenrecorder.domain.session.MonotonicClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Singleton

/** data 계층 구현체 바인딩. */
@Module
@InstallIn(SingletonComponent::class)
internal abstract class DataBindsModule {
    @Binds
    abstract fun bindSessionFactory(implementation: ProjectionRecorderSessionFactory): RecorderSessionFactory

    @Binds
    abstract fun bindFileStore(implementation: MediaStoreRecordingFileStore): RecordingFileStore

    @Binds
    abstract fun bindFileNameProvider(implementation: DefaultFileNameProvider): FileNameProvider

    @Binds
    abstract fun bindDisplayInfoProvider(implementation: WindowMetricsDisplayInfoProvider): DisplayInfoProvider

    @Binds
    abstract fun bindMonotonicClock(implementation: AndroidMonotonicClock): MonotonicClock

    @Binds
    abstract fun bindStorageRepository(implementation: DeviceStorageRepository): StorageRepository

    @Binds
    abstract fun bindSettingsRepository(
        implementation: io.rami.screenrecorder.data.settings.DataStoreSettingsRepository,
    ): io.rami.screenrecorder.domain.repository.SettingsRepository

    @Binds
    abstract fun bindMediaLibraryRepository(
        implementation: io.rami.screenrecorder.data.storage.MediaStoreMediaLibraryRepository,
    ): io.rami.screenrecorder.domain.repository.MediaLibraryRepository

    @Binds
    abstract fun bindTranscodeRepository(
        implementation: io.rami.screenrecorder.data.transcode.WorkManagerTranscodeRepository,
    ): io.rami.screenrecorder.domain.repository.TranscodeRepository
}

/** 세션 오케스트레이터 프로바이더. */
@Module
@InstallIn(SingletonComponent::class)
internal object DataProvidesModule {
    @Provides
    @Singleton
    fun provideRecordingSessionRepository(
        sessionFactory: RecorderSessionFactory,
        dependencies: RecorderDependencies,
    ): RecordingSessionRepository =
        RecordingCoordinator(
            sessionFactory = sessionFactory,
            dependencies = dependencies,
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
        )
}
