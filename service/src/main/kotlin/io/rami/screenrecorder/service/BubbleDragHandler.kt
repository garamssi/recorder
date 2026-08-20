package io.rami.screenrecorder.service

import android.annotation.SuppressLint
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * 플로팅 버블의 드래그 이동과 가장자리 스냅 (기능명세서 11.1절).
 *
 * 터치를 소비하되, 이동량이 시스템 터치 슬롭보다 작으면 탭으로 간주해 [onTap]을 부른다.
 * 버블은 다른 앱 위에 떠 있으므로 손가락을 떼면 좌우 가장자리에 붙여 콘텐츠를 최대한 덜 가린다.
 */
internal class BubbleDragHandler(
    private val windowManager: WindowManager,
    private val layoutParams: WindowManager.LayoutParams,
    private val onTap: () -> Unit,
    private val onSnapped: (toRight: Boolean) -> Unit,
) {
    private var initialX = 0
    private var initialY = 0
    private var touchStartX = 0f
    private var touchStartY = 0f
    private var dragging = false

    /** [view]를 드래그 손잡이로 삼는다. 터치를 소비하므로 자식 버튼이 없는 뷰에 붙여야 한다. */
    @SuppressLint("ClickableViewAccessibility")
    fun attachTo(view: View) {
        val touchSlop = ViewConfiguration.get(view.context).scaledTouchSlop
        view.setOnTouchListener { handle, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> onDown(event)
                MotionEvent.ACTION_MOVE -> onMove(handle, event, touchSlop)
                MotionEvent.ACTION_UP -> onUp(handle)
                MotionEvent.ACTION_CANCEL -> dragging = false
                else -> Unit
            }
            true
        }
    }

    private fun onDown(event: MotionEvent) {
        initialX = layoutParams.x
        initialY = layoutParams.y
        touchStartX = event.rawX
        touchStartY = event.rawY
        dragging = false
    }

    private fun onMove(
        view: View,
        event: MotionEvent,
        touchSlop: Int,
    ) {
        val deltaX = event.rawX - touchStartX
        val deltaY = event.rawY - touchStartY
        if (!dragging && abs(deltaX) < touchSlop && abs(deltaY) < touchSlop) return
        dragging = true
        layoutParams.x = initialX + deltaX.roundToInt()
        layoutParams.y = initialY + deltaY.roundToInt()
        applyLayout(view)
    }

    private fun onUp(view: View) {
        if (!dragging) {
            onTap()
            return
        }
        // 가장자리 판정만 하고 실제 배치는 상위(BubbleWindowPosition)가 한다 —
        // 창 크기를 아는 곳이 한 군데여야 펼침 상태에서도 화면 밖으로 나가지 않는다.
        val bounds = windowManager.currentWindowMetrics.bounds
        val root = view.rootView
        onSnapped(layoutParams.x + root.width / 2 >= bounds.width() / 2)
    }

    private fun applyLayout(view: View) {
        val attached = view.rootView.takeIf { it.isAttachedToWindow } ?: return
        windowManager.updateViewLayout(attached, layoutParams)
    }
}
