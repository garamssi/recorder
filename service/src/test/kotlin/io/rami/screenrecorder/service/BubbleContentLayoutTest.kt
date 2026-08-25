package io.rami.screenrecorder.service

import android.content.Context
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * 펼쳐도 기준 요소가 가로로 움직이지 않는지 (기능명세서 11.1절).
 *
 * 펼치면 라벨 때문에 창이 넓어진다. 붙어 있는 변 쪽에 기준 요소를 붙여 두지 않으면
 * 넓어진 만큼 버튼이 밀린다. 창 자체의 좌표는 [placeBubble]이 붙어 있는 변에 맞추므로,
 * 창 안에서 기준 요소가 그 변에 닿아 있으면 화면 좌표도 그대로다.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BubbleContentLayoutTest {
    private val context: Context get() = RuntimeEnvironment.getApplication()

    private fun menuItems() =
        listOf(
            BubbleMenuItem(R.drawable.ic_bubble_record, context.getString(R.string.floating_record), accent = true) {},
            BubbleMenuItem(R.drawable.ic_bubble_open_app, context.getString(R.string.floating_open_app)) {},
        )

    /** 실제 빌더로 스택을 꾸미고 측정·배치까지 마친다. */
    private fun buildStack(
        expanded: Boolean,
        snappedToRight: Boolean,
    ): Pair<LinearLayout, View> {
        val stack = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        val base = context.bubbleToggle(expanded) {}
        val rows = if (expanded) context.bubbleMenuRows(menuItems(), snappedToRight) {} else emptyList()
        stack.arrange(rows, base, menuBelowBase = false, snappedToRight = snappedToRight)
        val unspecified = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        stack.measure(unspecified, unspecified)
        stack.layout(0, 0, stack.measuredWidth, stack.measuredHeight)
        return stack to base
    }

    @Test
    fun `펼치면 라벨 때문에 창이 넓어진다`() {
        val (collapsed, _) = buildStack(expanded = false, snappedToRight = true)
        val (expanded, _) = buildStack(expanded = true, snappedToRight = true)

        assertTrue(expanded.measuredWidth > collapsed.measuredWidth)
    }

    @Test
    fun `오른쪽에 붙으면 펼쳐도 기준 요소가 오른쪽 변에 닿아 있다`() {
        val (collapsedStack, collapsedBase) = buildStack(expanded = false, snappedToRight = true)
        val (expandedStack, expandedBase) = buildStack(expanded = true, snappedToRight = true)

        assertEquals(collapsedStack.measuredWidth, collapsedBase.right)
        assertEquals(expandedStack.measuredWidth, expandedBase.right)
    }

    @Test
    fun `왼쪽에 붙으면 펼쳐도 기준 요소가 왼쪽 변에 닿아 있다`() {
        val (_, collapsedBase) = buildStack(expanded = false, snappedToRight = false)
        val (_, expandedBase) = buildStack(expanded = true, snappedToRight = false)

        assertEquals(0, collapsedBase.left)
        assertEquals(0, expandedBase.left)
    }

    @Test
    fun `오른쪽에 붙으면 메뉴 줄의 라벨이 버튼 왼쪽에 온다`() {
        val row = context.bubbleMenuRows(menuItems(), snappedToRight = true) {}.first() as LinearLayout

        assertTrue(row.getChildAt(0) is TextView)
        assertTrue(row.getChildAt(1) is ImageView)
    }

    @Test
    fun `왼쪽에 붙으면 메뉴 줄의 라벨이 버튼 오른쪽에 온다`() {
        val row = context.bubbleMenuRows(menuItems(), snappedToRight = false) {}.first() as LinearLayout

        assertTrue(row.getChildAt(0) is ImageView)
        assertTrue(row.getChildAt(1) is TextView)
    }
}
