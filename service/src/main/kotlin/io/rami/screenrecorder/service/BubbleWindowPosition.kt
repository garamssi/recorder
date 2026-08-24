package io.rami.screenrecorder.service

import android.content.Context
import android.view.View
import android.view.WindowInsets
import android.view.WindowManager

/**
 * 플로팅 버블 창의 위치를 관리한다 (기능명세서 11.1절).
 *
 * 펼치면 창이 넓고 길어지므로, 붙어 있던 좌우 변과 "아래쪽 기준선"을 기억해 다시 계산한다.
 * 덕분에 가장자리·최상단·최하단에서 펼쳐도 메뉴가 화면 밖으로 잘려 나가지 않고,
 * 토글 버튼은 제자리에 머문다.
 *
 * 좌표 계산 자체는 [placeBubble]에 있다.
 */
internal class BubbleWindowPosition(
    private val context: Context,
    private val windowManager: WindowManager,
    private val layoutParams: WindowManager.LayoutParams,
) {
    private var snappedToRight = false
    private var anchorBottom = UNSET_ANCHOR

    /** 드래그가 끝나 가장자리에 붙었을 때 기준을 갱신하고 화면 안으로 맞춘다. */
    fun onSnapped(
        toRight: Boolean,
        container: View?,
        isAnchorLayout: Boolean,
    ) {
        snappedToRight = toRight
        val view = container ?: return
        // 펼침 상태에서도 토글 버튼은 스택 맨 아래에 있으므로 창의 아래 변이 곧 기준선이다.
        anchorBottom = layoutParams.y + view.height
        keepOnScreen(view, isAnchorLayout)
    }

    /**
     * 창이 화면 안에 완전히 들어오도록 위치를 맞춘다. 측정이 끝난 뒤(post) 호출해야 한다.
     *
     * @param isAnchorLayout 접힘(기본) 레이아웃이면 true. 펼침 레이아웃은 기준선을 건드리지 않는다.
     */
    fun keepOnScreen(
        container: View,
        isAnchorLayout: Boolean,
    ) {
        if (anchorBottom == UNSET_ANCHOR) anchorBottom = layoutParams.y + container.height
        val placement =
            placeBubble(
                anchorBottom = anchorBottom,
                snappedToRight = snappedToRight,
                layout =
                    BubbleLayout(
                        width = container.width,
                        height = container.height,
                        isAnchorLayout = isAnchorLayout,
                    ),
                screen = currentScreen(),
                margin = context.dpToPx(EDGE_MARGIN_DP),
            )
        anchorBottom = placement.anchorBottom
        if (placement.x == layoutParams.x && placement.y == layoutParams.y) return
        layoutParams.x = placement.x
        layoutParams.y = placement.y
        windowManager.updateViewLayout(container, layoutParams)
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
