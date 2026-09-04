package io.rami.screenrecorder.service

import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadow.api.Shadow
import org.robolectric.shadows.ShadowWindowManagerImpl

/**
 * 화면이 회전하면 버블을 새 화면에 맞춰 다시 배치한다 (기능명세서 11.1절 [결정]).
 *
 * 창 좌표는 픽셀 절대값이고 오버레이 창에는 `FLAG_LAYOUT_NO_LIMITS`가 걸려 있어, 화면이
 * 좁아져도 시스템이 창을 안으로 밀어 넣어 주지 않는다. 가로에서 오른쪽 변에 붙여 둔 버블은
 * 세로로 돌리면 화면 밖에 남아 사용자에게는 그냥 사라진 것으로 보인다.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "ko-w1280dp-h800dp")
class BubbleRotationPlacementTest {
    private val context get() = RuntimeEnvironment.getApplication()

    private val windowManager get() = context.getSystemService(WindowManager::class.java)

    private val windowShadow: ShadowWindowManagerImpl
        get() = Shadow.extract(windowManager)

    private val actions =
        object : BubbleActions {
            override fun onStartRecording() = Unit

            override fun onStopRecording() = Unit

            override fun onPauseRecording() = Unit

            override fun onResumeRecording() = Unit

            override fun onCaptureScreenshot() = Unit

            override fun onStartVoiceRecording() = Unit

            override fun onStopVoiceRecording() = Unit

            override fun onEditTimeLimit() = Unit

            override fun onOpenApp() = Unit
        }

    private fun attachedRoot(): View =
        windowShadow.views.singleOrNull() ?: error("버블 창이 붙어 있지 않다 (${windowShadow.views.size}개)")

    private fun windowX(): Int = (attachedRoot().layoutParams as WindowManager.LayoutParams).x

    private fun screenWidth(): Int = windowManager.currentWindowMetrics.bounds.width()

    /** 붙어 있는 창의 실제 폭. WRAP_CONTENT 이므로 측정값이 곧 창 폭이다. */
    private fun windowWidth(): Int {
        val unspecified = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        return attachedRoot().apply { measure(unspecified, unspecified) }.measuredWidth
    }

    private fun screenHeight(): Int = windowManager.currentWindowMetrics.bounds.height()

    private fun windowY(): Int = (attachedRoot().layoutParams as WindowManager.LayoutParams).y

    /** [x], [y]까지 드래그한다. 손을 떼면 [BubbleDragHandler]가 가까운 좌우 변으로 스냅한다. */
    private fun dragTo(
        x: Int,
        y: Int,
    ) {
        val handle = attachedRoot().firstTouchable() ?: error("드래그 손잡이를 찾지 못했다")
        handle.touch(MotionEvent.ACTION_DOWN, 0f, 0f)
        handle.touch(MotionEvent.ACTION_MOVE, x.toFloat(), y.toFloat())
        handle.touch(MotionEvent.ACTION_UP, x.toFloat(), y.toFloat())
        shadowOf(context.mainLooper).idle()
    }

    /** 오른쪽 가장자리까지 드래그해 그 변에 붙인다. */
    private fun dragToRightEdge() {
        dragTo(x = screenWidth(), y = 0)
        // 스냅 직후에는 붙은 변 기준으로 배치돼 있어야 한다. 아니면 이 테스트의 전제가 깨진다.
        assertEquals(
            "드래그가 오른쪽 변 스냅으로 이어지지 않았다",
            screenWidth() - windowWidth() - context.dpToPx(EDGE_MARGIN_DP),
            windowX(),
        )
    }

    private fun View.touch(
        action: Int,
        x: Float,
        y: Float,
    ) {
        val now = SystemClock.uptimeMillis()
        val event = MotionEvent.obtain(now, now, action, x, y, 0)
        try {
            dispatchTouchEvent(event)
        } finally {
            event.recycle()
        }
    }

    /** 터치 리스너가 달린 첫 뷰 — 접힘 상태의 "+" 버튼이다. */
    private fun View.firstTouchable(): View? =
        when {
            this is ViewGroup -> (0 until childCount).firstNotNullOfOrNull { getChildAt(it).firstTouchable() }
            else -> this
        }

    @Test
    fun `세로로 돌리면 오른쪽에 붙어 있던 버블이 화면 안으로 들어온다`() {
        val bubble = FloatingCaptureBubble(context)
        bubble.show(actions)
        dragToRightEdge()
        val landscapeWidth = screenWidth()

        RuntimeEnvironment.setQualifiers("ko-w800dp-h1280dp")
        assertTrue("화면이 좁아지지 않아 이 테스트가 아무것도 재지 못한다", screenWidth() < landscapeWidth)

        bubble.reposition()

        assertTrue(
            "회전 뒤에도 창이 화면 밖에 남았다 — x=${windowX()}, 창 폭=${windowWidth()}, 화면 폭=${screenWidth()}",
            windowX() + windowWidth() <= screenWidth(),
        )
        assertTrue("창이 왼쪽 밖으로 밀렸다 — x=${windowX()}", windowX() >= 0)
    }

    @Test
    fun `세로로 돌려도 붙어 있던 오른쪽 변을 유지한다`() {
        val bubble = FloatingCaptureBubble(context)
        bubble.show(actions)
        dragToRightEdge()

        RuntimeEnvironment.setQualifiers("ko-w800dp-h1280dp")
        bubble.reposition()

        assertEquals(
            "붙어 있던 변을 잃고 반대쪽으로 옮겨 갔다",
            screenWidth() - windowWidth() - context.dpToPx(EDGE_MARGIN_DP),
            windowX(),
        )
    }

    @Test
    fun `감춰 둔 동안 회전해도 다시 보일 때 화면 안에 있다`() {
        val bubble = FloatingCaptureBubble(context)
        bubble.show(actions)
        dragToRightEdge()

        bubble.setHidden(true)
        RuntimeEnvironment.setQualifiers("ko-w800dp-h1280dp")
        bubble.reposition()
        bubble.setHidden(false)

        assertTrue(
            "감춰 둔 사이의 회전을 놓쳐 창이 화면 밖에 남았다 — x=${windowX()}, 화면 폭=${screenWidth()}",
            windowX() + windowWidth() <= screenWidth(),
        )
    }

    @Test
    fun `가로로 돌렸다 되돌리면 놓아둔 세로 자리로 돌아온다`() {
        RuntimeEnvironment.setQualifiers("ko-w800dp-h1280dp")
        val bubble = FloatingCaptureBubble(context)
        bubble.show(actions)
        dragTo(x = screenWidth(), y = screenHeight())
        val placed = windowY()

        // 가로는 세로보다 짧아 아래쪽에 둔 버블을 밀어 올려야 한다. 밀어 올린 만큼을 기억하면
        // 세로로 되돌아와도 그 자리에 남는다 (기능명세서 11.1절 [결정]).
        RuntimeEnvironment.setQualifiers("ko-w1280dp-h800dp")
        bubble.reposition()
        RuntimeEnvironment.setQualifiers("ko-w800dp-h1280dp")
        bubble.reposition()

        assertEquals("가로에서 밀어 올린 자리를 기억해 버렸다", placed, windowY())
    }

    private companion object {
        /** [BubbleWindowPosition]이 쓰는 가장자리 여백. */
        const val EDGE_MARGIN_DP = 12f
    }
}
