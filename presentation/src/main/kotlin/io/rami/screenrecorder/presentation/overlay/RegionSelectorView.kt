package io.rami.screenrecorder.presentation.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import io.rami.screenrecorder.domain.model.CaptureRegion
import kotlin.math.abs

/**
 * 부분 영역 선택 사각형 (기능명세서 2.2절: 드래그로 조정, 최소 320x240).
 *
 * 모서리 드래그 = 크기 조절, 내부 드래그 = 이동. 바깥은 딤 처리.
 */
class RegionSelectorView(
    context: Context,
) : View(context) {
    private val selection = RectF()
    private var dragMode = DragMode.NONE
    private var lastX = 0f
    private var lastY = 0f

    private val dimPaint = Paint().apply { color = DIM_COLOR }
    private val borderPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = BORDER_WIDTH_PX
            color = Color.WHITE
        }
    private val handlePaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.WHITE
        }

    /** 현재 선택 영역 (화면 픽셀 좌표, 최소 크기 보장). */
    fun currentRegion(): CaptureRegion =
        CaptureRegion(
            x = selection.left.toInt().coerceAtLeast(0),
            y = selection.top.toInt().coerceAtLeast(0),
            width = selection.width().toInt().coerceAtLeast(CaptureRegion.MIN_WIDTH),
            height = selection.height().toInt().coerceAtLeast(CaptureRegion.MIN_HEIGHT),
        )

    override fun onSizeChanged(
        w: Int,
        h: Int,
        oldw: Int,
        oldh: Int,
    ) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (selection.isEmpty) {
            // 초기값: 화면 중앙 절반 크기
            val width = (w / 2f).coerceAtLeast(CaptureRegion.MIN_WIDTH.toFloat())
            val height = (h / 2f).coerceAtLeast(CaptureRegion.MIN_HEIGHT.toFloat())
            selection.set(
                (w - width) / 2f,
                (h - height) / 2f,
                (w + width) / 2f,
                (h + height) / 2f,
            )
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        // 선택 영역 밖 4면 딤
        canvas.drawRect(0f, 0f, width.toFloat(), selection.top, dimPaint)
        canvas.drawRect(0f, selection.bottom, width.toFloat(), height.toFloat(), dimPaint)
        canvas.drawRect(0f, selection.top, selection.left, selection.bottom, dimPaint)
        canvas.drawRect(selection.right, selection.top, width.toFloat(), selection.bottom, dimPaint)
        canvas.drawRect(selection, borderPaint)
        cornerPoints().forEach { (cx, cy) -> canvas.drawCircle(cx, cy, HANDLE_RADIUS_PX, handlePaint) }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean =
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                dragMode = hitTest(event.x, event.y)
                lastX = event.x
                lastY = event.y
                dragMode != DragMode.NONE
            }

            MotionEvent.ACTION_MOVE -> {
                applyDrag(event.x - lastX, event.y - lastY)
                lastX = event.x
                lastY = event.y
                invalidate()
                true
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                dragMode = DragMode.NONE
                true
            }

            else -> super.onTouchEvent(event)
        }

    private fun hitTest(
        x: Float,
        y: Float,
    ): DragMode {
        cornerPoints().forEachIndexed { index, (cx, cy) ->
            if (abs(x - cx) <= TOUCH_SLOP_PX && abs(y - cy) <= TOUCH_SLOP_PX) {
                return DragMode.entries[index + 1]
            }
        }
        return if (selection.contains(x, y)) DragMode.MOVE else DragMode.NONE
    }

    private fun applyDrag(
        dx: Float,
        dy: Float,
    ) {
        when (dragMode) {
            DragMode.MOVE -> moveBy(dx, dy)
            DragMode.TOP_LEFT -> resize(left = dx, top = dy)
            DragMode.TOP_RIGHT -> resize(right = dx, top = dy)
            DragMode.BOTTOM_LEFT -> resize(left = dx, bottom = dy)
            DragMode.BOTTOM_RIGHT -> resize(right = dx, bottom = dy)
            DragMode.NONE -> Unit
        }
    }

    private fun moveBy(
        dx: Float,
        dy: Float,
    ) {
        val clampedDx = dx.coerceIn(-selection.left, width - selection.right)
        val clampedDy = dy.coerceIn(-selection.top, height - selection.bottom)
        selection.offset(clampedDx, clampedDy)
    }

    /** 최소 크기(320x240)와 화면 경계를 지키며 각 변을 조정한다. */
    private fun resize(
        left: Float = 0f,
        top: Float = 0f,
        right: Float = 0f,
        bottom: Float = 0f,
    ) {
        val minW = CaptureRegion.MIN_WIDTH.toFloat()
        val minH = CaptureRegion.MIN_HEIGHT.toFloat()
        selection.left = (selection.left + left).coerceIn(0f, selection.right - minW)
        selection.top = (selection.top + top).coerceIn(0f, selection.bottom - minH)
        selection.right = (selection.right + right).coerceIn(selection.left + minW, width.toFloat())
        selection.bottom = (selection.bottom + bottom).coerceIn(selection.top + minH, height.toFloat())
    }

    private fun cornerPoints() =
        listOf(
            selection.left to selection.top,
            selection.right to selection.top,
            selection.left to selection.bottom,
            selection.right to selection.bottom,
        )

    /** 엔트리 순서는 [cornerPoints]와 정렬된다 (NONE 제외). */
    private enum class DragMode { NONE, TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT, MOVE }

    private companion object {
        const val DIM_COLOR = 0x99000000.toInt()
        const val BORDER_WIDTH_PX = 4f
        const val HANDLE_RADIUS_PX = 24f
        const val TOUCH_SLOP_PX = 64f
    }
}
