package io.rami.screenrecorder.service

import android.content.Context
import android.view.View
import android.view.WindowManager

/**
 * 플로팅 버블 창의 위치를 관리한다 (기능명세서 11.1절).
 *
 * 펼치면 창이 넓고 길어지므로, 붙어 있던 좌우 변과 "아래쪽 기준선"을 기억해 다시 계산한다.
 * 덕분에 가장자리·최상단·최하단에서 펼쳐도 메뉴가 화면 밖으로 잘려 나가지 않고,
 * 토글 버튼은 제자리에 머문다.
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
    ) {
        snappedToRight = toRight
        val view = container ?: return
        anchorBottom = layoutParams.y + view.height
        keepOnScreen(view)
    }

    /**
     * 창이 화면 안에 완전히 들어오도록 위치를 맞춘다. 측정이 끝난 뒤(post) 호출해야 한다.
     *
     * 상태 바·제스처 바(시스템 바 인셋)는 피한다. 그 영역에 겹치면 그려지기는 해도
     * 터치를 SystemUI가 가져가 버블을 눌러도 반응하지 않는다.
     */
    fun keepOnScreen(container: View) {
        val metrics = windowManager.currentWindowMetrics
        val bounds = metrics.bounds
        val insets =
            metrics.windowInsets.getInsets(
                android.view.WindowInsets.Type
                    .systemBars(),
            )
        val margin = context.dpToPx(EDGE_MARGIN_DP)
        if (anchorBottom == UNSET_ANCHOR) anchorBottom = layoutParams.y + container.height
        val minY = insets.top + margin
        val wantedX =
            if (snappedToRight) {
                bounds.width() - insets.right - container.width - margin
            } else {
                insets.left + margin
            }
        val maxY =
            (bounds.height() - insets.bottom - container.height - margin).coerceAtLeast(minY)
        val wantedY = (anchorBottom - container.height).coerceIn(minY, maxY)
        // 화면에 맞춰 잘려 나간 만큼 기준선도 옮겨 다음 펼침이 어긋나지 않게 한다.
        anchorBottom = wantedY + container.height
        if (wantedX == layoutParams.x && wantedY == layoutParams.y) return
        layoutParams.x = wantedX
        layoutParams.y = wantedY
        windowManager.updateViewLayout(container, layoutParams)
    }

    /** 버블을 닫을 때 기준선을 지운다. */
    fun reset() {
        anchorBottom = UNSET_ANCHOR
    }

    private companion object {
        const val EDGE_MARGIN_DP = 12f
        const val UNSET_ANCHOR = -1
    }
}
