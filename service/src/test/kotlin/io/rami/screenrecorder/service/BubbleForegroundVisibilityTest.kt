package io.rami.screenrecorder.service

import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.TextView
import io.rami.screenrecorder.domain.model.TimeLimit
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
import kotlin.time.Duration.Companion.minutes

/**
 * 앱 화면이 전면인 동안 버블을 감추는 규칙 (기능명세서 11.1절 [결정]).
 *
 * 감추는 것과 없애는 것은 다르다. 창을 떼어 버리면 그 동안 도착한 상태를 잃고, 설정 스트림은
 * 값이 바뀔 때만 방출하므로 잃은 값은 다시 오지 않는다.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "ko-w1280dp-h800dp")
class BubbleForegroundVisibilityTest {
    private val context get() = RuntimeEnvironment.getApplication()

    private val windowShadow: ShadowWindowManagerImpl
        get() = Shadow.extract(context.getSystemService(WindowManager::class.java))

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

    private val tenMinutes = TimeLimit.Limited(10.minutes)

    private fun settle() = shadowOf(context.mainLooper).idle()

    /** 붙어 있는 버블 창의 루트. 없으면 실패한다. */
    private fun attachedRoot(): View =
        windowShadow.views.singleOrNull() ?: error("버블 창이 붙어 있지 않다 (${windowShadow.views.size}개)")

    /** 접힘 상태의 "+" 버튼을 눌러 메뉴를 펼친다. */
    private fun expandMenu() {
        val toggle =
            attachedRoot().firstWithDescription(context.getString(R.string.floating_expand))
                ?: error("접힘 토글을 찾지 못했다")
        toggle.performClick()
        settle()
    }

    private fun View.firstWithDescription(description: String): View? =
        when {
            contentDescription == description -> this
            this is ViewGroup ->
                (0 until childCount).firstNotNullOfOrNull {
                    getChildAt(it).firstWithDescription(description)
                }

            else -> null
        }

    private fun View.texts(): List<String> =
        when (this) {
            is TextView -> listOf(text.toString())
            is ViewGroup -> (0 until childCount).flatMap { getChildAt(it).texts() }
            else -> emptyList()
        }

    @Test
    fun `감춰 둔 사이에 바뀐 시간 제한이 다시 보일 때 반영돼 있다`() {
        val bubble = FloatingCaptureBubble(context)
        bubble.show(actions)

        // 앱에 들어가 감춰진 사이에 설정이 바뀐다. 설정 스트림은 값이 바뀔 때만 방출하므로
        // 이 방출이 유일한 기회다.
        bubble.setHidden(true)
        bubble.render(BubbleState.Idle(tenMinutes))
        bubble.setHidden(false)

        expandMenu()

        assertTrue(
            "감춰 둔 사이에 도착한 시간 제한을 잃었다 — 메뉴에 남은 글자: ${attachedRoot().texts()}",
            context.timeLimitLabel(tenMinutes) in attachedRoot().texts(),
        )
    }

    @Test
    fun `감춰 둔 사이에 시작된 녹화가 다시 보일 때 pill로 그려져 있다`() {
        val bubble = FloatingCaptureBubble(context)
        bubble.show(actions)

        bubble.setHidden(true)
        bubble.render(BubbleState.ScreenRecording(elapsed = "00:42", isPaused = false))
        bubble.setHidden(false)

        assertTrue(
            "감춰 둔 사이에 시작된 녹화를 잃고 유휴 버블로 돌아왔다 — 남은 글자: ${attachedRoot().texts()}",
            "00:42" in attachedRoot().texts(),
        )
    }

    @Test
    fun `창이 떼어진 동안 바뀐 시간 제한도 잃지 않는다`() {
        val bubble = FloatingCaptureBubble(context)
        bubble.show(actions)

        // 감추기와 달리 창이 아예 없는 경우다 (서비스가 죽었다 되살아나는 경로).
        // 그리지 못하는 것과 모르는 것은 다르다.
        bubble.dismiss()
        bubble.render(BubbleState.Idle(tenMinutes))
        bubble.show(actions)

        expandMenu()

        assertTrue(
            "창이 없는 동안 도착한 시간 제한을 잃었다 — 메뉴에 남은 글자: ${attachedRoot().texts()}",
            context.timeLimitLabel(tenMinutes) in attachedRoot().texts(),
        )
    }

    @Test
    fun `전면이면 창을 떼지 않고 보이지 않게만 한다`() {
        val bubble = FloatingCaptureBubble(context)
        bubble.show(actions)
        val attached = attachedRoot()

        bubble.setHidden(true)

        assertEquals("창을 떼어 버렸다", 1, windowShadow.views.size)
        assertTrue("창을 새로 붙였다", attached === attachedRoot())
        assertEquals(View.GONE, attachedRoot().visibility)
    }

    @Test
    fun `앱을 벗어나면 감춰 둔 창이 그대로 다시 보인다`() {
        val bubble = FloatingCaptureBubble(context)
        bubble.show(actions)
        bubble.setHidden(true)
        val hidden = attachedRoot()

        bubble.setHidden(false)

        assertTrue("창을 새로 붙였다", hidden === attachedRoot())
        assertEquals(View.VISIBLE, attachedRoot().visibility)
    }

    @Test
    fun `감춘 채로 띄우면 한 프레임도 보이지 않는다`() {
        val bubble = FloatingCaptureBubble(context)

        // 서비스는 앱 안에서 시작되므로, 붙이기 전에 이미 감춰야 한다.
        bubble.setHidden(true)
        bubble.show(actions)

        assertEquals(View.GONE, attachedRoot().visibility)
    }

    @Test
    fun `감춰 둔 사이에도 놓아둔 자리를 지킨다`() {
        val bubble = FloatingCaptureBubble(context)
        bubble.show(actions)
        val params = attachedRoot().layoutParams as WindowManager.LayoutParams
        val placed = params.x to params.y

        bubble.setHidden(true)
        bubble.render(BubbleState.Idle(tenMinutes))
        bubble.setHidden(false)

        val after = attachedRoot().layoutParams as WindowManager.LayoutParams
        assertEquals(placed, after.x to after.y)
    }

    @Test
    fun `창을 뗐다 다시 붙여도 감춰 둔 상태를 잊지 않는다`() {
        val bubble = FloatingCaptureBubble(context)
        bubble.setHidden(true)
        bubble.show(actions)

        // 감춤은 앱이 전면인지에 대한 사실이지 창의 사정이 아니다. 창을 뗀다고 앱이
        // 뒤로 가지는 않는다.
        bubble.dismiss()
        bubble.show(actions)

        assertEquals(View.GONE, attachedRoot().visibility)
    }

    @Test
    fun `같은 상태를 다시 그리라고 해도 창을 새로 붙이지 않는다`() {
        val bubble = FloatingCaptureBubble(context)
        bubble.show(actions)
        val first = attachedRoot()

        bubble.render(BubbleState.Idle(TimeLimit.None))

        assertEquals(1, windowShadow.views.size)
        assertTrue("창을 새로 붙였다", first === attachedRoot())
    }
}
