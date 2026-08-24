package io.rami.screenrecorder.service

import android.content.Context
import android.view.View
import android.view.WindowInsets
import android.view.WindowManager

/**
 * 플로팅 버블 창의 위치를 관리한다 (기능명세서 11.1절).
 *
 * 붙어 있던 좌우 변과 기준 요소의 아래 변("기준선")을 기억해, 펼쳐도 사용자가 놓아둔
 * 버튼이 제자리에 머물게 한다. 좌표와 펼침 방향 계산 자체는 [placeBubble]에 있다.
 */
internal class BubbleWindowPosition(
    private val context: Context,
    private val windowManager: WindowManager,
    private val layoutParams: WindowManager.LayoutParams,
) {
    /** 마지막으로 붙은 변. 메뉴 줄의 라벨 위치와 정렬이 이 값을 따른다. */
    var snappedToRight = false
        private set

    private var anchorBottom = UNSET_ANCHOR

    /**
     * 드래그가 끝나 가장자리에 붙었을 때 기준을 갱신하고 화면 안으로 맞춘다.
     *
     * @return [keepOnScreen]과 같다 — 메뉴를 기준 요소 아래에 두어야 하면 true.
     */
    fun onSnapped(
        toRight: Boolean,
        container: View,
        base: View,
    ): Boolean {
        snappedToRight = toRight
        // base는 스택의 첫 자식일 수도 마지막 자식일 수도 있으므로 컨테이너가 아니라 자신의 아래 변을 쓴다.
        anchorBottom = layoutParams.y + base.bottom
        return keepOnScreen(container, base)
    }

    /**
     * 창을 화면 안에 맞추고 메뉴를 그릴 방향을 돌려준다.
     *
     * 레이아웃 패스를 기다리지 않고 직접 측정한다. 다음 프레임에 고치면 그 한 프레임 동안
     * 버블이 엉뚱한 자리에 그려진다.
     *
     * @return 메뉴를 기준 요소 아래에 두어야 하면 true.
     */
    fun keepOnScreen(
        container: View,
        base: View,
    ): Boolean {
        container.measureWrapContent()
        val layout =
            BubbleLayout(
                width = container.measuredWidth,
                height = container.measuredHeight,
                baseHeight = base.measuredHeight,
            )
        if (layout.height <= 0) return false
        if (anchorBottom == UNSET_ANCHOR) anchorBottom = layoutParams.y + layout.baseHeight
        val placement =
            placeBubble(
                anchorBottom = anchorBottom,
                snappedToRight = snappedToRight,
                layout = layout,
                screen = currentScreen(),
                margin = context.dpToPx(EDGE_MARGIN_DP),
            )
        anchorBottom = placement.anchorBottom
        if (placement.x != layoutParams.x || placement.y != layoutParams.y) {
            layoutParams.x = placement.x
            layoutParams.y = placement.y
            windowManager.updateViewLayout(container, layoutParams)
        }
        return placement.menuBelowBase
    }

    /** 버블을 닫을 때 기준선을 지운다. */
    fun reset() {
        anchorBottom = UNSET_ANCHOR
    }

    private fun currentScreen(): BubbleScreen {
        val metrics = windowManager.currentWindowMetrics
        val bounds = metrics.bounds
        val insets = metrics.windowInsets.getInsets(WindowInsets.Type.systemBars())
        return BubbleScreen(
            width = bounds.width(),
            height = bounds.height(),
            insetLeft = insets.left,
            insetTop = insets.top,
            insetRight = insets.right,
            insetBottom = insets.bottom,
        )
    }

    private companion object {
        const val EDGE_MARGIN_DP = 12f
        const val UNSET_ANCHOR = -1
    }
}

/** 제약 없이 원하는 크기를 재게 한다. 창이 WRAP_CONTENT이므로 이 값이 곧 창 크기다. */
private fun View.measureWrapContent() {
    val unspecified = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
    measure(unspecified, unspecified)
}
