package io.rami.screenrecorder.service

import android.content.Context
import android.text.Editable
import android.text.TextWatcher
import android.widget.EditText
import android.widget.TextView
import io.rami.screenrecorder.domain.model.TimeLimit
import io.rami.screenrecorder.domain.model.TimeLimitField
import io.rami.screenrecorder.domain.model.TimeLimitFields
import io.rami.screenrecorder.domain.model.TimeLimitInput

// 버블에서 여는 녹화 시간 제한 입력 뷰의 조립과 검증 (기능명세서 11.4절).
// 뷰 조각은 TimeLimitInputViewParts.kt, 증감 버튼은 TimeLimitStepper.kt,
// 창 관리는 TimeLimitInputWindow.kt 참조.

/**
 * 시/분/초 직접 입력 뷰를 만든다 (기능명세서 11.4절).
 *
 * 키보드로 쳐 넣을 수도, 각 칸의 증감 버튼으로 1씩 옮길 수도 있다. 두 방식 모두
 * [TimeLimitFields]의 같은 규칙을 거치므로 칸별 범위와 총합 검증이 어긋나지 않는다.
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
    val initial = TimeLimitFields.of(current)
    val error = errorLabel()
    val buttons =
        TimeLimitInputButtons(
            clear = dialogButton(getString(R.string.floating_time_limit_clear), accent = false),
            cancel = dialogButton(getString(R.string.floating_time_limit_cancel), accent = false),
            confirm = dialogButton(getString(R.string.floating_time_limit_confirm), accent = true),
        )
    val columns = TimeLimitField.entries.map { field -> fieldColumn(field, initial.valueOf(field)) }

    val revalidate = {
        columns.clampInputs()
        applyValidation(columns.readFields().validate(), error, buttons.confirm)
    }
    columns.forEach { column ->
        column.input.onTextChanged(revalidate)
        column.attachStepping { field, delta -> columns.step(field, delta) }
    }
    revalidate()

    buttons.clear.setOnClickListener { onConfirm(TimeLimit.None) }
    buttons.cancel.setOnClickListener { onDismiss() }
    buttons.confirm.setOnClickListener {
        (columns.readFields().validate() as? TimeLimitInput.Valid)?.let { onConfirm(it.timeLimit) }
    }

    return TimeLimitInputViews(
        root = inputCard(columns, error, buttons),
        columns = columns,
        error = error,
        buttons = buttons,
    )
}

/**
 * [field] 칸을 [delta]만큼 옮기고 화면에 반영한다.
 *
 * 값을 넣으면 텍스트 감시자가 검증을 다시 돌리므로 여기서 따로 부르지 않는다.
 */
private fun List<TimeLimitFieldViews>.step(
    field: TimeLimitField,
    delta: Int,
) {
    val stepped = readFields().stepped(field, delta)
    first { it.field == field }.input.show(stepped.valueOf(field))
}

/**
 * 칸에 친 값이 칸별 범위를 넘으면 상한·하한으로 되돌린다 (기능명세서 11.4절).
 *
 * 값이 같으면 손대지 않는다 — "05"처럼 앞자리 0을 붙여 치는 중에 글자가 지워지면
 * 사용자가 이어 치던 자리를 잃는다.
 */
private fun List<TimeLimitFieldViews>.clampInputs() {
    val clamped = readFields()
    forEach { column ->
        val typed = column.input.typedNumber() ?: return@forEach
        val inRange = clamped.valueOf(column.field)
        if (typed != inRange) column.input.show(inRange)
    }
}

/** 값을 칸에 넣고 커서를 끝으로 보낸다. */
private fun EditText.show(value: Int) {
    setText(value.toString())
    setSelection(text.length)
}

/** 세 칸의 현재 입력을 도메인 값으로 읽는다. */
private fun List<TimeLimitFieldViews>.readFields(): TimeLimitFields =
    fold(TimeLimitFields(0, 0, 0)) { fields, column ->
        fields.withValue(column.field, column.input.digits())
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

/** 칸에 적힌 숫자. 비어 있거나 숫자가 아니면 null. */
private fun EditText.typedNumber(): Int? = text.toString().toIntOrNull()

/** 비어 있거나 숫자가 아니면 0으로 본다 — 지우는 중에도 검증이 멈추지 않아야 한다. */
private fun EditText.digits(): Int = typedNumber() ?: 0

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

private const val DISABLED_ALPHA = 0.4f
