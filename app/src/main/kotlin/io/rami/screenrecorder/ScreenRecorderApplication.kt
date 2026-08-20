package io.rami.screenrecorder

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.video.VideoFrameDecoder
import dagger.hilt.android.HiltAndroidApp

/** Hilt DI 그래프의 루트가 되는 Application. Coil 전역 로더(비디오 썸네일 지원)도 제공한다. */
@HiltAndroidApp
class ScreenRecorderApplication :
    Application(),
    SingletonImageLoader.Factory {
    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader
            .Builder(context)
            .components { add(VideoFrameDecoder.Factory()) }
            .build()
}
