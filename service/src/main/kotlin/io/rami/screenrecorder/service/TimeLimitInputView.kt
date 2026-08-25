package io.rami.screenrecorder.service

import android.content.Context
import android.text.Editable
import android.text.TextWatcher
import android.widget.EditText
import android.widget.TextView
import io.rami.screenrecorder.domain.model.TimeLimit
import io.rami.screenrecorder.domain.model.TimeLimitInput

// 버블에서 여는 녹화 시간 제한 입력 뷰의 조립과 검증 (기능명세서 11.4절).
// 뷰 조각은 TimeLimitInputViewParts.kt, 창 관리는 TimeLimitInputWindow.kt 참조.

/**
 * 시/분/초 직접 입력 뷰를 만든다 (기능명세서 11.4절).
 *
 * 옵션 시트의 직접 입력과 같은 규칙([TimeLimit.fromHoursMinutesSeconds])을 써서,
 * 범위를 벗어나면 저장을 막고 사유를 보여 준다.
 *
 * @param current 미리 채울 현재 설정값.
 * @param onConfirm 저장 또는 제한 해제로 확정된 값.
 * @param onDismiss 취소 등으로 값을 바꾸지 않고 닫을 때.
 */
internal fun Context.buildTimeLimitInput(
    current: TimeLimit,
    onConfirm: (TimeLimit) -> Unit,
    onDismiss: () -> Unit,
): TimeLimitInputViews {
    val initial = current.toHoursMinutesSeconds()
    val hours = timeField(initial.hours)
    val minutes = timeField(initial.minutes)
    val seconds = timeField(initial.seconds)
    val fields = listOf(hours, minutes, seconds)
    val error = errorLabel()
    val buttons =
        TimeLimitInputButtons(
            clear = dialogButton(getString(R.string.floating_time_limit_clear), accent = false),
            cancel = dialogButton(getString(R.string.floating_time_limit_cancel), accent = false),
            confirm = dialogButton(getString(R.string.floating_time_limit_confirm), accent = true),
        )

    val revalidate = { applyValidation(fields.toTimeLimitInput(), error, buttons.confirm) }
    fields.forEach { field -> field.onTextChanged(revalidate) }
    revalidate()

    buttons.clear.setOnClickListener { onConfirm(TimeLimit.None) }
    buttons.cancel.setOnClickListener { onDismiss() }
    buttons.confirm.setOnClickListener {
        (fields.toTimeLimitInput() as? TimeLimitInput.Valid)?.let { onConfirm(it.timeLimit) }
    }

    return TimeLimitInputViews(
        root = inputCard(fields, error, buttons),
        hours = hours,
        minutes = minutes,
        seconds = seconds,
        error = error,
        buttons = buttons,
    )
}

/** 검증 결과를 사유 문구와 저장 버튼 활성 상태로 옮긴다. */
private fun Context.applyValidation(
    input: TimeLimitInput,
    error: TextView,
    confirm: TextView,
) {
    val valid = input is TimeLimitInput.Valid
    error.text =
        getString(
            when (input) {
                is TimeLimitInput.TooShort -> R.string.floating_time_limit_too_short
                is TimeLimitInput.TooLong -> R.string.floating_time_limit_too_long
                is TimeLimitInput.Valid -> R.string.floating_time_limit_range
            },
        )
    error.setTextColor(if (valid) BUBBLE_MUTED else BUBBLE_ACCENT)
    confirm.isEnabled = valid
    confirm.alpha = if (valid) 1f else DISABLED_ALPHA
}

/** 시·분·초 순서로 담긴 입력 칸을 도메인 검증에 넘긴다. */
private fun List<EditText>.toTimeLimitInput(): TimeLimitInput =
    TimeLimit.fromHoursMinutesSeconds(
        hours = this[HOURS_INDEX].digits(),
        minutes = this[MINUTES_INDEX].digits(),
        seconds = this[SECONDS_INDEX].digits(),
    )

/** 시/분/초로 나눈 현재 값. */
private data class HoursMinutesSeconds(
    val hours: Int,
    val minutes: Int,
    val seconds: Int,
)

/** 제한이 없으면 0에서 시작한다. */
private fun TimeLimit.toHoursMinutesSeconds(): HoursMinutesSeconds =
    when (this) {
        is TimeLimit.None -> HoursMinutesSeconds(0, 0, 0)
        is TimeLimit.Limited ->
            duration.toComponents { hours, minutes, seconds, _ ->
                HoursMinutesSeconds(hours.toInt(), minutes, seconds)
            }
    }

/** 비어 있거나 숫자가 아니면 0으로 본다 — 지우는 중에도 검증이 멈추지 않아야 한다. */
private fun EditText.digits(): Int = text.toString().toIntOrNull() ?: 0

private fun EditText.onTextChanged(onChanged: () -> Unit) {
    addTextChangedListener(
        object : TextWatcher {
            override fun beforeTextChanged(
                text: CharSequence?,
                start: Int,
                count: Int,
                after: Int,
            ) = Unit

            override fun onTextChanged(
                text: CharSequence?,
                start: Int,
                before: Int,
                count: Int,
            ) = Unit

            override fun afterTextChanged(editable: Editable?) = onChanged()
        },
    )
}

private const val HOURS_INDEX = 0
private const val MINUTES_INDEX = 1
private const val SECONDS_INDEX = 2
private const val DISABLED_ALPHA = 0.4f
