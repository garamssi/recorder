package io.rami.screenrecorder.service

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.roundToInt

/**
 * 드래그 가능한 플로팅 컨트롤 버블 (기능명세서 11.1절 [결정]).
 *
 * 경과 시간과 일시정지/재개·중지 버튼을 화면 위에 띄운다.
 * 오버레이 특성상 버블 자체가 녹화에 찍힌다(명세에 명시).
 */
class FloatingControlBubble(
    private val context: Context,
) {
    private var root: LinearLayout? = null
    private var elapsedText: TextView? = null
    private var pauseResumeButton: Button? = null
    private var onPause: (() -> Unit)? = null
    private var onResume: (() -> Unit)? = null
    private var paused = false

    /** 버블을 표시한다. 이미 떠 있으면 무시한다. */
    fun show(
        onPause: () -> Unit,
        onResume: () -> Unit,
        onStop: () -> Unit,
    ) {
        if (root != null) return
        this.onPause = onPause
        this.onResume = onResume
        val windowManager = context.getSystemService(WindowManager::class.java)
        val layoutParams = overlayLayoutParams()
        val container = buildView(onStop, windowManager, layoutParams)
        root = container
        windowManager.addView(container, layoutParams)
    }

    /** 경과 시간과 일시정지 상태를 갱신한다. */
    fun update(
        elapsedText: String,
        isPaused: Boolean,
    ) {
        paused = isPaused
        this.elapsedText?.text = elapsedText
        pauseResumeButton?.text =
            context.getString(
                if (isPaused) R.string.floating_resume else R.string.floating_pause,
            )
    }

    /** 버블을 닫는다. */
    fun dismiss() {
        val container = root ?: return
        root = null
        context.getSystemService(WindowManager::class.java).removeView(container)
    }

    @SuppressLint("SetTextI18n")
    private fun buildView(
        onStop: () -> Unit,
        windowManager: WindowManager,
        layoutParams: WindowManager.LayoutParams,
    ): LinearLayout {
        val elapsed =
            TextView(context).apply {
                setTextColor(Color.WHITE)
                text = "00:00"
            }
        elapsedText = elapsed
        val pauseResume =
            Button(context).apply {
                text = context.getString(R.string.floating_pause)
                setOnClickListener { if (paused) onResume?.invoke() else onPause?.invoke() }
            }
        pauseResumeButton = pauseResume
        val stop =
            Button(context).apply {
                text = context.getString(R.string.floating_stop)
                setOnClickListener { onStop() }
            }
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background =
                GradientDrawable().apply {
                    setColor(BUBBLE_BACKGROUND)
                    cornerRadius = dp(CORNER_RADIUS_DP)
                }
            val padding = dp(PADDING_DP).roundToInt()
            setPadding(padding, padding, padding, padding)
            addView(elapsed)
            addView(pauseResume)
            addView(stop)
            attachDragHandler(this, windowManager, layoutParams)
        }
    }

    /** 루트 뷰 드래그로 버블 위치를 옮긴다. 버튼 탭과 구분하기 위해 이동 임계값을 둔다. */
    @SuppressLint("ClickableViewAccessibility")
    private fun attachDragHandler(
        view: View,
        windowManager: WindowManager,
        layoutParams: WindowManager.LayoutParams,
    ) {
        var initialX = 0
        var initialY = 0
        var touchStartX = 0f
        var touchStartY = 0f
        view.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = layoutParams.x
                    initialY = layoutParams.y
                    touchStartX = event.rawX
                    touchStartY = event.rawY
                    false
                }

                MotionEvent.ACTION_MOVE -> {
                    layoutParams.x = initialX + (event.rawX - touchStartX).roundToInt()
                    layoutParams.y = initialY + (event.rawY - touchStartY).roundToInt()
                    root?.let { windowManager.updateViewLayout(it, layoutParams) }
                    false
                }

                else -> false
            }
        }
    }

    private fun overlayLayoutParams() =
        WindowManager
            .LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT,
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = dp(INITIAL_MARGIN_DP).roundToInt()
                y = dp(INITIAL_MARGIN_DP).roundToInt()
            }

    private fun dp(value: Float): Float = value * context.resources.displayMetrics.density

    private companion object {
        const val BUBBLE_BACKGROUND = 0xCC000000.toInt()
        const val CORNER_RADIUS_DP = 24f
        const val PADDING_DP = 8f
        const val INITIAL_MARGIN_DP = 24f
    }
}
