package io.rami.screenrecorder.service

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.text.InputFilter
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import io.rami.screenrecorder.domain.model.TimeLimitFields

// 시간 제한 입력 창을 이루는 뷰 조각들. 색·치수는 DESIGN_GUIDE.md 1절 Kinetic 토큰을 따른다.
// 조립과 검증은 TimeLimitInputView.kt, 증감 버튼은 TimeLimitStepper.kt 참조.

/** 숫자 두 자리 입력 칸. 시/분/초 각 두 자리면 12시간을 표현하기에 넉넉하다. */
internal fun Context.timeField(initial: Int): EditText =
    EditText(this).apply {
        setText(initial.toString())
        inputType = InputType.TYPE_CLASS_NUMBER
        filters = arrayOf<InputFilter>(InputFilter.LengthFilter(TimeLimitFields.MAX_DIGITS))
        gravity = Gravity.CENTER
        setTextColor(BUBBLE_FOREGROUND)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, FIELD_TEXT_SP)
        val padding = dpToPx(FIELD_PADDING_DP)
        setPadding(padding, padding, padding, padding)
        background =
            GradientDrawable().apply {
                setColor(FIELD_BACKGROUND)
                cornerRadius = dpToPx(CARD_CORNER_DP).toFloat()
            }
        layoutParams =
            LinearLayout.LayoutParams(dpToPx(FIELD_WIDTH_DP), LinearLayout.LayoutParams.WRAP_CONTENT)
    }

/** 허용 범위 안내 겸 오류 사유를 보여 주는 줄. */
internal fun Context.errorLabel(): TextView =
    TextView(this).apply {
        setTextColor(BUBBLE_MUTED)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, HELPER_TEXT_SP)
    }

/** 하단 버튼. [accent]면 Kinetic 레드로 강조한다. */
internal fun Context.dialogButton(
    text: String,
    accent: Boolean,
): TextView =
    TextView(this).apply {
        this.text = text
        isClickable = true
        isFocusable = true
        gravity = Gravity.CENTER
        setTextColor(if (accent) BUBBLE_FOREGROUND else BUBBLE_MUTED)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, BUTTON_TEXT_SP)
        setPadding(
            dpToPx(BUTTON_PADDING_H_DP),
            dpToPx(BUTTON_PADDING_V_DP),
            dpToPx(BUTTON_PADDING_H_DP),
            dpToPx(BUTTON_PADDING_V_DP),
        )
        background =
            GradientDrawable().apply {
                setColor(if (accent) BUBBLE_ACCENT else Color.TRANSPARENT)
                cornerRadius = dpToPx(BUTTON_CORNER_DP).toFloat()
            }
    }

/** 칸에 붙는 단위 문구 ("시", "분", "초"). */
internal fun Context.unitLabel(text: String): TextView =
    TextView(this).apply {
        this.text = text
        setTextColor(BUBBLE_MUTED)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, HELPER_TEXT_SP)
        setPadding(0, dpToPx(UNIT_PADDING_DP), 0, 0)
    }

/** 제목·입력 칸·사유·버튼을 담은 카드. */
internal fun Context.inputCard(
    columns: List<TimeLimitFieldViews>,
    error: TextView,
    buttons: TimeLimitInputButtons,
): View =
    LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        val padding = dpToPx(CARD_PADDING_DP)
        setPadding(padding, padding, padding, padding)
        background =
            GradientDrawable().apply {
                setColor(BUBBLE_SURFACE)
                cornerRadius = dpToPx(CARD_CORNER_DP).toFloat()
            }
        addView(cardTitle(getString(R.string.floating_time_limit_title)))
        addView(fieldRow(columns), verticalGap())
        addView(error, verticalGap())
        addView(buttonRow(buttons), verticalGap())
    }

/** 시·분·초 칸을 가로로 나란히 둔 줄. */
private fun Context.fieldRow(columns: List<TimeLimitFieldViews>): LinearLayout =
    LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        columns.forEachIndexed { index, column ->
            addView(
                column.root,
                LinearLayout
                    .LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                    ).apply { marginStart = if (index > 0) dpToPx(COLUMN_GAP_DP) else 0 },
            )
        }
    }

private fun Context.buttonRow(buttons: TimeLimitInputButtons): LinearLayout =
    LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.END
        buttons.asList().forEach { button ->
            addView(
                button,
                LinearLayout
                    .LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                    ).apply { marginStart = dpToPx(BUTTON_GAP_DP) },
            )
        }
    }

private fun Context.verticalGap() =
    LinearLayout
        .LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
        ).apply { topMargin = dpToPx(CARD_GAP_DP) }

private fun Context.cardTitle(text: String): TextView =
    TextView(this).apply {
        this.text = text
        setTextColor(BUBBLE_FOREGROUND)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, TITLE_TEXT_SP)
    }

private const val FIELD_WIDTH_DP = 64f
private const val FIELD_PADDING_DP = 8f
private const val FIELD_BACKGROUND = 0xFF27272A.toInt()
private const val CARD_CORNER_DP = 12f
private const val CARD_PADDING_DP = 20f
private const val CARD_GAP_DP = 12f
private const val COLUMN_GAP_DP = 12f
private const val BUTTON_CORNER_DP = 10f
private const val BUTTON_PADDING_H_DP = 16f
private const val BUTTON_PADDING_V_DP = 10f
private const val BUTTON_GAP_DP = 8f
private const val UNIT_PADDING_DP = 4f
private const val TITLE_TEXT_SP = 18f
private const val FIELD_TEXT_SP = 20f
private const val HELPER_TEXT_SP = 13f
private const val BUTTON_TEXT_SP = 15f
