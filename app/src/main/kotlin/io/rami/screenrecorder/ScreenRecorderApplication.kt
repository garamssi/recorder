package io.rami.screenrecorder

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.video.VideoFrameDecoder
import dagger.hilt.android.HiltAndroidApp
import io.rami.screenrecorder.data.storage.ProcessStartTime
import javax.inject.Inject

/** Hilt DI 그래프의 루트가 되는 Application. Coil 전역 로더(비디오 썸네일 지원)도 제공한다. */
@HiltAndroidApp
class ScreenRecorderApplication :
    Application(),
    SingletonImageLoader.Factory {
    /**
     * 버려진 발행 판정의 폴백 기준 시각을 프로세스가 뜨자마자 고정한다 (기능명세서 6.1절 [결정]).
     *
     * 늦게 재면 그 사이 시계가 보정됐을 때 기준선이 밀려 진행 중인 발행을 지운다.
     */
    @Inject
    lateinit var processStartTime: ProcessStartTime

    override fun onCreate() {
        super.onCreate()
        processStartTime.epochSeconds
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader
            .Builder(context)
            .components { add(VideoFrameDecoder.Factory()) }
            .build()
}
