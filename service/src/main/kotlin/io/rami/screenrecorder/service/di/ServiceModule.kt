package io.rami.screenrecorder.service.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.rami.screenrecorder.service.SaveOverlay
import io.rami.screenrecorder.service.SaveOverlayWindow
import javax.inject.Singleton

/** service 계층의 프로세스 스코프 협력자 조립. */
@Module
@InstallIn(SingletonComponent::class)
object ServiceModule {
    /**
     * 저장 오버레이는 프로세스에 하나뿐이어야 한다.
     *
     * 배너를 띄운 녹화 서비스는 발행 직후 스스로 접히고, 다음 세션은 **새 서비스 인스턴스**에서
     * 시작된다. 인스턴스마다 창을 따로 들면 새 세션이 부르는 dismiss() 가 창을 한 번도 붙인 적
     * 없는 객체를 향하고, 이전 표시는 그대로 떠 있다가 다음 녹화의 첫 프레임에 찍힌다.
     * 창의 수명이 서비스보다 길다는 설계와도 이쪽이 맞는다.
     */
    @Provides
    @Singleton
    fun provideSaveOverlay(
        @ApplicationContext context: Context,
    ): SaveOverlay = SaveOverlayWindow(context)
}
