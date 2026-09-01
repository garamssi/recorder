package io.rami.screenrecorder.foreground

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.rami.screenrecorder.service.AppForegroundState
import javax.inject.Singleton

/** 전면 여부를 세는 쪽은 액티비티를 아는 app 계층이다. */
@Module
@InstallIn(SingletonComponent::class)
abstract class ForegroundModule {
    @Binds
    @Singleton
    abstract fun bindAppForegroundState(tracker: ForegroundActivityTracker): AppForegroundState
}
