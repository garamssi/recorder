package io.rami.screenrecorder.service

import android.view.View
import android.view.WindowManager
import io.rami.screenrecorder.domain.model.TimeLimit
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadow.api.Shadow
import org.robolectric.shadows.ShadowSettings
import org.robolectric.shadows.ShadowWindowManagerImpl

/**
 * 시간 제한 입력 창은 화면 전체를 덮어야 한다 (기능명세서 11.4절).
 *
 * 딤이 화면을 다 덮지 못하면 남는 자리로 아래 앱이 그대로 눌린다. 바깥 탭으로 닫는 판정도
 * 딤 뷰가 받으므로 덮이지 않은 자리는 취소도 되지 않는다.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "ko-w800dp-h1280dp")
class TimeLimitInputWindowTest {
    private val context get() = RuntimeEnvironment.getApplication()

    private val windowManager get() = context.getSystemService(WindowManager::class.java)

    private val windowShadow: ShadowWindowManagerImpl
        get() = Shadow.extract(windowManager)

    private fun attachedRoot(): View =
        windowShadow.views.singleOrNull() ?: error("입력 창이 붙어 있지 않다 (${windowShadow.views.size}개)")

    private fun params(): WindowManager.LayoutParams = attachedRoot().layoutParams as WindowManager.LayoutParams

    private fun openInput(): TimeLimitInputWindow {
        ShadowSettings.setCanDrawOverlays(true)
        val window = TimeLimitInputWindow(context)
        window.show(TimeLimit.None) {}
        shadowOf(context.mainLooper).idle()
        return window
    }

    @Test
    fun `회전해서 화면이 넓어져도 창이 화면을 덮는다`() {
        openInput()
        RuntimeEnvironment.setQualifiers("ko-w1280dp-h800dp")

        val width = params().width
        val screenWidth = windowManager.currentWindowMetrics.bounds.width()

        assertTrue(
            "창 크기를 띄울 때의 픽셀로 못 박아 두어 회전 뒤 화면을 덮지 못한다 — 창 폭=$width, 화면 폭=$screenWidth",
            width == WindowManager.LayoutParams.MATCH_PARENT || width >= screenWidth,
        )
    }

    @Test
    fun `회전해서 화면이 높아져도 창이 화면을 덮는다`() {
        ShadowSettings.setCanDrawOverlays(true)
        RuntimeEnvironment.setQualifiers("ko-w1280dp-h800dp")
        openInput()
        RuntimeEnvironment.setQualifiers("ko-w800dp-h1280dp")

        val height = params().height
        val screenHeight = windowManager.currentWindowMetrics.bounds.height()

        assertTrue(
            "창 크기를 띄울 때의 픽셀로 못 박아 두어 회전 뒤 화면을 덮지 못한다 — 창 높이=$height, 화면 높이=$screenHeight",
            height == WindowManager.LayoutParams.MATCH_PARENT || height >= screenHeight,
        )
    }
}
