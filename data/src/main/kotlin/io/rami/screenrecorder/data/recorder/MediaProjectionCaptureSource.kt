package io.rami.screenrecorder.data.recorder

import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.projection.MediaProjection
import android.os.Handler
import android.os.Looper
import android.view.Surface
import io.rami.screenrecorder.domain.model.Resolution

/**
 * MediaProjection + VirtualDisplay 기반 [ScreenCaptureSource] 구현.
 *
 * Android 14+ 규칙: 콜백 등록은 VirtualDisplay 생성 전에 해야 하며,
 * 시스템 중단(onStop)은 안전 마무리로 이어져야 한다 (CLAUDE.md 7절, 기능명세서 11.1절).
 */
class MediaProjectionCaptureSource(
    private val projection: MediaProjection,
    private val densityDpi: Int,
) : ScreenCaptureSource {
    private var virtualDisplay: VirtualDisplay? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    override fun start(
        encoderSurface: Surface,
        resolution: Resolution,
        listener: ScreenCaptureSource.Listener,
    ) {
        projection.registerCallback(projectionCallback(listener), mainHandler)
        virtualDisplay =
            projection.createVirtualDisplay(
                VIRTUAL_DISPLAY_NAME,
                resolution.width,
                resolution.height,
                densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                encoderSurface,
                null,
                mainHandler,
            )
    }

    override fun stop() {
        virtualDisplay?.release()
        virtualDisplay = null
        projection.stop()
    }

    private fun projectionCallback(listener: ScreenCaptureSource.Listener) =
        object : MediaProjection.Callback() {
            override fun onStop() {
                if (virtualDisplay != null) {
                    listener.onStoppedBySystem()
                }
            }

            override fun onCapturedContentResize(
                width: Int,
                height: Int,
            ) {
                listener.onContentResize(width, height)
            }
        }

    private companion object {
        const val VIRTUAL_DISPLAY_NAME = "ScreenRecorderCapture"
    }
}
