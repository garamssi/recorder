package io.rami.screenrecorder.service

import android.content.Context
import android.view.View
import android.widget.LinearLayout
import io.rami.screenrecorder.domain.model.TimeLimit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import kotlin.time.Duration.Companion.minutes

/**
 * 시간 제한 줄이 늘어난 뒤에도 버블이 제자리에 머무는지 (기능명세서 11.1절).
 *
 * 메뉴 줄이 하나 늘면 펼침 창이 그만큼 높아지고 라벨이 길어 넓어진다. 기준 요소가
 * 화면에서 움직이면 사용자는 다음에 누를 자리를 매번 다시 찾아야 하므로,
 * 실제 메뉴로 재고 실제 좌표 계산까지 통과시켜 확인한다.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "ko")
class BubbleTimeLimitPlacementTest {
    private val context: Context get() = RuntimeEnvironment.getApplication()

    /** 3배 밀도 태블릿 (BubblePlacementTest와 같은 화면). */
    private val screen =
        BubbleScreen(
            width = 1600,
            height = 2560,
            insetLeft = 0,
            insetTop = 60,
            insetRight = 0,
            insetBottom = 48,
        )
    private val margin = 36

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

    /** 실제 유휴 메뉴로 스택을 꾸미고 측정까지 마친 결과. */
    private class MeasuredBubble(
        val layout: BubbleLayout,
        val baseLeftInStack: Int,
    )

    private fun measure(
        expanded: Boolean,
        snappedToRight: Boolean,
        menuBelowBase: Boolean = false,
        timeLimit: TimeLimit = TimeLimit.Limited(10.minutes),
    ): MeasuredBubble {
        val stack = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        val base = context.bubbleToggle(expanded) {}
        val rows =
            if (expanded) {
                val items = context.menuItemsFor(BubbleState.Idle(timeLimit), actions)
                context.bubbleMenuRows(items, snappedToRight) {}
            } else {
                emptyList()
            }
        stack.arrange(rows, base, menuBelowBase, snappedToRight)
        val unspecified = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        stack.measure(unspecified, unspecified)
        stack.layout(0, 0, stack.measuredWidth, stack.measuredHeight)
        return MeasuredBubble(
            layout =
                BubbleLayout(
                    width = stack.measuredWidth,
                    height = stack.measuredHeight,
                    baseHeight = base.measuredHeight,
                ),
            baseLeftInStack = base.left,
        )
    }

    private fun place(
        measured: MeasuredBubble,
        anchorBottom: Int,
        snappedToRight: Boolean,
    ) = placeBubble(
        anchorBottom = anchorBottom,
        snappedToRight = snappedToRight,
        layout = measured.layout,
        screen = screen,
        margin = margin,
    )

    /** 기준 요소의 화면 좌표 — 사용자가 놓아둔 버튼이 실제로 그려지는 자리다. */
    private fun BubblePlacement.baseTopOnScreen(measured: MeasuredBubble) =
        if (menuBelowBase) y else y + measured.layout.height - measured.layout.baseHeight

    private fun BubblePlacement.baseLeftOnScreen(measured: MeasuredBubble) = x + measured.baseLeftInStack

    /** 시간 제한 줄을 뺀 예전 메뉴 — 줄이 하나 늘어난 효과를 견주기 위한 기준. */
    private fun measureWithoutTimeLimitRow(): BubbleLayout {
        val stack = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        val base = context.bubbleToggle(true) {}
        val items =
            context
                .menuItemsFor(BubbleState.Idle(TimeLimit.None), actions)
                .filterNot { it.iconRes == R.drawable.ic_bubble_time_limit }
        stack.arrange(context.bubbleMenuRows(items, snappedToRight = true) {}, base, false, true)
        val unspecified = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        stack.measure(unspecified, unspecified)
        return BubbleLayout(stack.measuredWidth, stack.measuredHeight, base.measuredHeight)
    }

    @Test
    fun `시간 제한 줄이 늘어난 만큼 펼침 창이 높아진다`() {
        val withoutRow = measureWithoutTimeLimitRow()
        val withRow = measure(expanded = true, snappedToRight = true).layout

        assertTrue("줄이 하나 늘었는데 창 높이가 그대로면 메뉴가 잘린 것이다", withRow.height > withoutRow.height)
    }

    @Test
    fun `오른쪽에 붙어 위로 펼쳐도 기준 버튼이 움직이지 않는다`() {
        val collapsed = measure(expanded = false, snappedToRight = true)
        val expanded = measure(expanded = true, snappedToRight = true)

        val closed = place(collapsed, anchorBottom = 2000, snappedToRight = true)
        val opened = place(expanded, anchorBottom = closed.anchorBottom, snappedToRight = true)

        assertEquals(closed.baseTopOnScreen(collapsed), opened.baseTopOnScreen(expanded))
        assertEquals(closed.baseLeftOnScreen(collapsed), opened.baseLeftOnScreen(expanded))
    }

    @Test
    fun `왼쪽에 붙어 위로 펼쳐도 기준 버튼이 움직이지 않는다`() {
        val collapsed = measure(expanded = false, snappedToRight = false)
        val expanded = measure(expanded = true, snappedToRight = false)

        val closed = place(collapsed, anchorBottom = 2000, snappedToRight = false)
        val opened = place(expanded, anchorBottom = closed.anchorBottom, snappedToRight = false)

        assertEquals(closed.baseTopOnScreen(collapsed), opened.baseTopOnScreen(expanded))
        assertEquals(closed.baseLeftOnScreen(collapsed), opened.baseLeftOnScreen(expanded))
    }

    @Test
    fun `위쪽 공간이 모자라 아래로 펼쳐도 기준 버튼이 움직이지 않는다`() {
        val collapsed = measure(expanded = false, snappedToRight = true)
        val probe = measure(expanded = true, snappedToRight = true)

        val closed = place(collapsed, anchorBottom = 400, snappedToRight = true)
        val opened = place(probe, anchorBottom = closed.anchorBottom, snappedToRight = true)
        // 방향이 정해지면 그 순서로 다시 담기므로, 측정도 같은 순서로 다시 한다.
        val expanded = measure(expanded = true, snappedToRight = true, menuBelowBase = opened.menuBelowBase)
        val settled = place(expanded, anchorBottom = closed.anchorBottom, snappedToRight = true)

        assertTrue("위쪽이 모자라면 아래로 펼쳐야 한다", settled.menuBelowBase)
        assertEquals(closed.baseTopOnScreen(collapsed), settled.baseTopOnScreen(expanded))
        assertEquals(closed.baseLeftOnScreen(collapsed), settled.baseLeftOnScreen(expanded))
    }

    @Test
    fun `펼친 메뉴가 시스템 바 인셋을 침범하지 않는다`() {
        val expanded = measure(expanded = true, snappedToRight = true)

        val opened = place(expanded, anchorBottom = 2000, snappedToRight = true)

        assertTrue("상태 바에 겹치면 터치를 SystemUI가 가져간다", opened.y >= screen.insetTop + margin)
        assertTrue(
            "제스처 바에 겹치면 터치를 SystemUI가 가져간다",
            opened.y + expanded.layout.height <= screen.height - screen.insetBottom - margin,
        )
    }

    @Test
    fun `펼쳤다 접으면 펼치기 전 자리로 돌아온다`() {
        val collapsed = measure(expanded = false, snappedToRight = true)
        val expanded = measure(expanded = true, snappedToRight = true)

        val closed = place(collapsed, anchorBottom = 2000, snappedToRight = true)
        val opened = place(expanded, anchorBottom = closed.anchorBottom, snappedToRight = true)
        val reclosed = place(collapsed, anchorBottom = opened.anchorBottom, snappedToRight = true)

        assertEquals(closed.y, reclosed.y)
        assertEquals(closed.x, reclosed.x)
    }
}
