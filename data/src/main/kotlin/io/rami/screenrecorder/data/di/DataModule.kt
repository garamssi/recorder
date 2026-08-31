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
import io.rami.screenrecorder.data.recorder.WindowMetricsDisplayInfoProvider
import io.rami.screenrecorder.data.storage.DeviceStorageRepository
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
    abstract fun bindCaptureConsentRepository(
        implementation: io.rami.screenrecorder.data.recorder.TokenHolderCaptureConsentRepository,
    ): io.rami.screenrecorder.domain.repository.CaptureConsentRepository

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

/** 화면 캡처·음성 녹음 구현체 바인딩 (기능명세서 12, 13절). */
@Module
@InstallIn(SingletonComponent::class)
internal abstract class QuickCaptureBindsModule {
    @Binds
    abstract fun bindMediaVolumeRepository(
        implementation: io.rami.screenrecorder.data.audio.SystemMediaVolumeRepository,
    ): io.rami.screenrecorder.domain.repository.MediaVolumeRepository

    @Binds
    abstract fun bindSystemVolumeGateway(
        implementation: io.rami.screenrecorder.data.audio.AudioManagerVolumeGateway,
    ): io.rami.screenrecorder.data.audio.SystemVolumeGateway

    @Binds
    abstract fun bindScreenshotRepository(
        implementation: io.rami.screenrecorder.data.recorder.MediaProjectionScreenshotRepository,
    ): io.rami.screenrecorder.domain.repository.ScreenshotRepository
}

/** 세션 오케스트레이터 프로바이더. */
@Module
@InstallIn(SingletonComponent::class)
internal object DataProvidesModule {
    /** 음성 전용 녹음 저장소 (기능명세서 13절). 자체 스코프에서 경과 시간 티커를 돌린다. */
    @Provides
    @Singleton
    fun provideVoiceRecordingRepository(
        @dagger.hilt.android.qualifiers.ApplicationContext context: android.content.Context,
        settingsRepository: io.rami.screenrecorder.domain.repository.SettingsRepository,
        quickCaptureStore: io.rami.screenrecorder.data.storage.MediaStoreQuickCaptureStore,
        clock: MonotonicClock,
    ): io.rami.screenrecorder.domain.repository.VoiceRecordingRepository =
        io.rami.screenrecorder.data.audio.AacVoiceRecordingRepository(
            context = context,
            settingsRepository = settingsRepository,
            quickCaptureStore = quickCaptureStore,
            clock = clock,
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
        )

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
