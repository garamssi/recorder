package io.rami.screenrecorder.service

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import io.rami.screenrecorder.domain.model.TimeLimitField

// 시간 제한 입력 칸의 증감 버튼 (기능명세서 11.4절).
// 값을 계산하는 규칙은 domain 의 TimeLimitFields 가 갖고, 여기서는 누르는 방식만 다룬다.

/** 증감 버튼 두 개와 입력 칸을 세로로 쌓은 한 칸. */
internal fun Context.fieldColumn(
    field: TimeLimitField,
    initial: Int,
): TimeLimitFieldViews {
    val unit = getString(field.unitLabelRes())
    val increase =
        stepButton(STEP_UP_GLYPH, getString(R.string.floating_time_step_up, unit), above = true)
    val input = timeField(initial)
    val decrease =
        stepButton(STEP_DOWN_GLYPH, getString(R.string.floating_time_step_down, unit), above = false)
    val column =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            addView(increase)
            addView(input)
            addView(decrease)
            addView(unitLabel(unit))
        }
    return TimeLimitFieldViews(
        field = field,
        input = input,
        increase = increase,
        decrease = decrease,
        root = column,
    )
}

/**
 * 증감 버튼에 탭과 길게 누르기를 붙인다.
 *
 * 12시간처럼 큰 값을 한 번에 올리려면 길게 눌러 연속 증감할 수 있어야 한다
 * (기능명세서 11.4절). 탭은 [View.OnClickListener]가, 누르고 있는 동안의 반복은
 * 손을 뗄 때까지 스스로 다시 예약하는 [Runnable]이 맡는다.
 */
internal fun TimeLimitFieldViews.attachStepping(onStep: (TimeLimitField, Int) -> Unit) {
    increase.attachStep { onStep(field, 1) }
    decrease.attachStep { onStep(field, -1) }
}

@SuppressLint("ClickableViewAccessibility")
private fun View.attachStep(step: () -> Unit) {
    setOnClickListener { step() }
    val handler = Handler(Looper.getMainLooper())
    val repeat =
        object : Runnable {
            override fun run() {
                step()
                handler.postDelayed(this, REPEAT_INTERVAL_MS)
            }
        }
    setOnLongClickListener {
        handler.postDelayed(repeat, REPEAT_INTERVAL_MS)
        true
    }
    // 손을 떼거나 제스처가 취소되면 반복을 끊는다. 탭은 그대로 흘려보내야 하므로 false 를 돌려준다.
    setOnTouchListener { _, event ->
        if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
            handler.removeCallbacks(repeat)
        }
        false
    }
}

/**
 * 삼각형 글리프 하나짜리 작은 버튼.
 *
 * @param above 입력 칸 위에 놓이는 버튼인지. 간격을 입력 칸 쪽에만 준다.
 */
private fun Context.stepButton(
    glyph: String,
    description: String,
    above: Boolean,
): TextView =
    TextView(this).apply {
        text = glyph
        contentDescription = description
        isClickable = true
        isFocusable = true
        isLongClickable = true
        gravity = Gravity.CENTER
        setTextColor(BUBBLE_FOREGROUND)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, STEP_TEXT_SP)
        background =
            GradientDrawable().apply {
                setColor(STEP_BACKGROUND)
                cornerRadius = dpToPx(STEP_CORNER_DP).toFloat()
            }
        layoutParams =
            LinearLayout
                .LayoutParams(dpToPx(STEP_WIDTH_DP), dpToPx(STEP_HEIGHT_DP))
                .apply {
                    if (above) bottomMargin = dpToPx(STEP_GAP_DP) else topMargin = dpToPx(STEP_GAP_DP)
                }
    }

/** 칸에 붙는 단위 문구. */
private fun TimeLimitField.unitLabelRes(): Int =
    when (this) {
        TimeLimitField.HOURS -> R.string.floating_time_unit_hours
        TimeLimitField.MINUTES -> R.string.floating_time_unit_minutes
        TimeLimitField.SECONDS -> R.string.floating_time_unit_seconds
    }

private const val STEP_UP_GLYPH = "▲"
private const val STEP_DOWN_GLYPH = "▼"
private const val REPEAT_INTERVAL_MS = 90L
private const val STEP_WIDTH_DP = 64f
private const val STEP_HEIGHT_DP = 40f
private const val STEP_GAP_DP = 6f
private const val STEP_CORNER_DP = 10f
private const val STEP_TEXT_SP = 13f
private const val STEP_BACKGROUND = 0xFF27272A.toInt()
