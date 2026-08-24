package io.rami.screenrecorder.service

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * 플로팅 버블 좌표 계산 (기능명세서 11.1절).
 *
 * 값은 3배 밀도 태블릿을 가정한 픽셀이다 (12dp = 36px, 52dp 버튼 = 156px).
 */
class BubblePlacementTest {
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
    private val collapsed = BubbleLayout(width = 156, height = 156, isAnchorLayout = true)
    private val expanded = BubbleLayout(width = 720, height = 900, isAnchorLayout = false)

    private fun place(
        anchorBottom: Int,
        layout: BubbleLayout,
        snappedToRight: Boolean = false,
    ) = placeBubble(
        anchorBottom = anchorBottom,
        snappedToRight = snappedToRight,
        layout = layout,
        screen = screen,
        margin = margin,
    )

    @Test
    fun `접힘 버블은 기준선의 위쪽에 놓인다`() {
        val placement = place(anchorBottom = 636, layout = collapsed)

        assertEquals(480, placement.y)
        assertEquals(636, placement.anchorBottom)
    }

    @Test
    fun `펼침 메뉴가 화면 위로 넘치면 화면 안으로 밀어 넣는다`() {
        val placement = place(anchorBottom = 636, layout = expanded)

        assertEquals(96, placement.y)
    }

    @Test
    fun `화면 안으로 밀린 펼침 메뉴는 기준선을 옮기지 않는다`() {
        val opened = place(anchorBottom = 636, layout = expanded)

        assertEquals(636, opened.anchorBottom)
    }

    @Test
    fun `펼쳤다 접으면 펼치기 전 자리로 돌아온다`() {
        val opened = place(anchorBottom = 636, layout = expanded)
        val closed = place(anchorBottom = opened.anchorBottom, layout = collapsed)

        assertEquals(480, closed.y)
    }

    @Test
    fun `접힘 버블이 화면 아래로 넘치면 기준선까지 함께 끌어올린다`() {
        val placement = place(anchorBottom = 3000, layout = collapsed)

        assertEquals(2320, placement.y)
        assertEquals(2476, placement.anchorBottom)
    }

    @Test
    fun `왼쪽에 붙으면 왼쪽 인셋과 여백만큼 띄운다`() {
        val placement = place(anchorBottom = 636, layout = expanded)

        assertEquals(36, placement.x)
    }

    @Test
    fun `오른쪽에 붙으면 창 너비만큼 왼쪽으로 당겨 오른쪽 변을 맞춘다`() {
        val narrow = place(anchorBottom = 636, layout = collapsed, snappedToRight = true)
        val wide = place(anchorBottom = 636, layout = expanded, snappedToRight = true)

        assertEquals(1408, narrow.x)
        assertEquals(844, wide.x)
        assertEquals(narrow.x + collapsed.width, wide.x + expanded.width)
    }
}
