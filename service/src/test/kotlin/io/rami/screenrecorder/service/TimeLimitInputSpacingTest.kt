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
import kotlin.math.abs

/**
 * 시간 제한 입력 창의 간격 (DESIGN_GUIDE.md 1절).
 *
 * 눈대중으로 맞추면 값이 6·4·8·12로 흩어져 줄마다 리듬이 달라진다. 실제 여백을 재서
 * "칸 안·칸 사이·버튼 사이는 한 눈금, 섹션 사이는 두 눈금"을 고정한다.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "ko")
class TimeLimitInputSpacingTest {
    private val context: Context get() = RuntimeEnvironment.getApplication()

    private val views =
        context.buildTimeLimitInput(current = TimeLimit.None, onConfirm = {}, onDismiss = {})

    private val card get() = views.root as LinearLayout

    /** 세로로 쌓인 두 자식 사이의 실제 간격. */
    private fun LinearLayout.verticalGaps(): List<Int> =
        (1 until childCount).map { index ->
            getChildAt(index - 1).bottomMargin() + getChildAt(index).topMargin()
        }

    /** 가로로 놓인 두 자식 사이의 실제 간격. */
    private fun LinearLayout.horizontalGaps(): List<Int> =
        (1 until childCount).map { index ->
            getChildAt(index - 1).endMargin() + getChildAt(index).startMargin()
        }

    private fun View.params() = layoutParams as LinearLayout.LayoutParams

    private fun View.topMargin() = params().topMargin

    private fun View.bottomMargin() = params().bottomMargin

    private fun View.startMargin() = params().marginStart

    private fun View.endMargin() = params().marginEnd

    private fun laidOut(): LinearLayout {
        val unspecified = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        card.measure(unspecified, unspecified)
        card.layout(0, 0, card.measuredWidth, card.measuredHeight)
        return card
    }

    /** 화면에 그려진 뒤의 가로 중심. 부모가 다른 뷰끼리 견주려면 화면 좌표가 필요하다. */
    private fun View.centerXOnScreen(): Int {
        var center = left + width / 2
        var parent = this.parent
        while (parent is View) {
            center += parent.left
            parent = parent.parent
        }
        return center
    }

    @Test
    fun `카드 안 섹션 간격이 모두 같다`() {
        val gaps = card.verticalGaps()

        assertEquals("제목·입력·안내·버튼 사이가 같은 간격이어야 한다", 1, gaps.distinct().size)
    }

    @Test
    fun `칸 안의 요소 간격이 모두 같다`() {
        val column = views.columns.first().root as LinearLayout

        val gaps = column.verticalGaps()

        assertEquals("증가·입력·감소·단위가 같은 간격이어야 한다", 1, gaps.distinct().size)
    }

    @Test
    fun `칸 사이와 버튼 사이가 칸 안과 같은 눈금이다`() {
        val column = views.columns.first().root as LinearLayout
        val inColumn = column.verticalGaps().first()
        val betweenColumns = (card.getChildAt(FIELD_ROW_INDEX) as LinearLayout).horizontalGaps()
        val betweenButtons = (card.getChildAt(BUTTON_ROW_INDEX) as LinearLayout).horizontalGaps()

        assertEquals(listOf(inColumn, inColumn), betweenColumns)
        assertEquals(listOf(inColumn, inColumn), betweenButtons)
    }

    @Test
    fun `섹션 간격은 요소 간격의 두 배다`() {
        val column = views.columns.first().root as LinearLayout

        val inColumn = column.verticalGaps().first()
        val betweenSections = card.verticalGaps().first()

        assertEquals(inColumn * 2, betweenSections)
    }

    @Test
    fun `증감 버튼과 입력 칸의 너비가 같다`() {
        val column = views.columns.first()

        val widths =
            listOf(column.increase, column.input, column.decrease).map { it.params().width }

        assertEquals("세로로 쌓이는 세 뷰의 너비가 다르면 칸이 들쭉날쭉해 보인다", 1, widths.distinct().size)
    }

    @Test
    fun `단위 라벨이 입력 칸과 같은 세로축에 놓인다`() {
        laidOut()

        views.columns.forEach { column ->
            val unit = (column.root as LinearLayout).getChildAt(UNIT_LABEL_INDEX)
            val drift = abs(column.input.centerXOnScreen() - unit.centerXOnScreen())
            // 뷰 폭이 홀수면 가운데 정렬이 1px 어긋난다. 눈에 보이는 어긋남만 잡는다.
            assertTrue("단위 라벨이 입력 칸 가운데에서 $drift px 벗어났다", drift <= 1)
        }
    }

    @Test
    fun `카드 안의 모든 줄이 같은 세로축에 놓인다`() {
        laidOut()

        val centers = (0 until card.childCount).map { card.getChildAt(it).centerXOnScreen() }

        // 줄마다 폭이 다른데 정렬 축까지 다르면 카드 안이 기울어 보인다.
        assertTrue("줄들의 중심이 $centers 로 흩어졌다", centers.max() - centers.min() <= 1)
    }

    @Test
    fun `카드의 모든 줄이 제 폭만 차지한다`() {
        val widths = (0 until card.childCount).map { card.getChildAt(it).params().width }

        // 폭을 꽉 채운 줄은 카드의 가운데 정렬을 무시해 저 혼자 왼쪽에 붙는다.
        assertEquals(listOf(LinearLayout.LayoutParams.WRAP_CONTENT), widths.distinct())
    }

    @Test
    fun `세 칸이 같은 너비로 놓인다`() {
        laidOut()

        val widths = views.columns.map { it.root.width }

        assertEquals(1, widths.distinct().size)
    }

    private companion object {
        const val FIELD_ROW_INDEX = 1
        const val BUTTON_ROW_INDEX = 3
        const val UNIT_LABEL_INDEX = 3
    }
}
