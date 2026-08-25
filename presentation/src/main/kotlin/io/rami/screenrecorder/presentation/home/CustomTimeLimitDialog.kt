package io.rami.screenrecorder.presentation.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.rami.screenrecorder.domain.model.TimeLimit
import io.rami.screenrecorder.domain.model.TimeLimitField
import io.rami.screenrecorder.domain.model.TimeLimitFields
import io.rami.screenrecorder.domain.model.TimeLimitInput
import io.rami.screenrecorder.presentation.R

/**
 * 타이머 직접 입력 다이얼로그 (기능명세서 11.4절 [결정]: 시/분/초, 10초~12시간).
 *
 * 키보드로 쳐 넣을 수도, 각 칸의 증감 버튼으로 1씩 옮길 수도 있다. 플로팅 버블의 입력 창과
 * 같은 규칙([TimeLimitFields])을 쓰므로 두 진입점의 조작 결과가 어긋나지 않는다.
 *
 * 범위를 벗어나면 저장 버튼을 비활성화하고 사유를 표시한다 (입력 단계 차단).
 *
 * @param current 미리 채울 현재 설정값.
 */
@Composable
fun CustomTimeLimitDialog(
    current: TimeLimit,
    onConfirm: (TimeLimit.Limited) -> Unit,
    onDismiss: () -> Unit,
) {
    // 회전으로 다시 그려져도 입력하던 값이 남아야 한다. 칸 세 개를 저장 가능한 정수로 들고
    // 있다가 그릴 때만 도메인 값으로 묶는다 — TimeLimitFields 자체는 저장 대상이 아니다.
    val initial = TimeLimitFields.of(current)
    var hours by rememberSaveable(current) { mutableIntStateOf(initial.hours) }
    var minutes by rememberSaveable(current) { mutableIntStateOf(initial.minutes) }
    var seconds by rememberSaveable(current) { mutableIntStateOf(initial.seconds) }
    val fields = TimeLimitFields(hours, minutes, seconds)
    val onChange = { next: TimeLimitFields ->
        hours = next.hours
        minutes = next.minutes
        seconds = next.seconds
    }
    val validation = fields.validate()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.options_time_limit_custom_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(SPACE_SECTION.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(SPACE_TIGHT.dp)) {
                    TimeLimitField.entries.forEach { field ->
                        TimeFieldColumn(field = field, fields = fields, onChange = onChange)
                    }
                }
                ValidationMessage(validation)
            }
        },
        confirmButton = {
            TextButton(
                enabled = validation is TimeLimitInput.Valid,
                onClick = { (validation as? TimeLimitInput.Valid)?.let { onConfirm(it.timeLimit) } },
            ) { Text(stringResource(R.string.options_confirm)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.dialog_cancel)) }
        },
    )
}

/** 허용 범위 안내 겸 오류 사유. 범위를 벗어나면 오류 색으로 보여 준다. */
@Composable
private fun ValidationMessage(validation: TimeLimitInput) {
    val valid = validation is TimeLimitInput.Valid
    val message =
        when (validation) {
            is TimeLimitInput.TooShort -> R.string.options_time_limit_too_short
            is TimeLimitInput.TooLong -> R.string.options_time_limit_too_long
            is TimeLimitInput.Valid -> R.string.options_time_limit_range
        }
    Text(
        text = stringResource(message),
        style = MaterialTheme.typography.bodySmall,
        color =
            if (valid) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.error
            },
    )
}

/** 증감 버튼 두 개와 입력 칸을 세로로 쌓은 한 칸. */
@Composable
private fun TimeFieldColumn(
    field: TimeLimitField,
    fields: TimeLimitFields,
    onChange: (TimeLimitFields) -> Unit,
) {
    val unit = stringResource(field.unitLabelRes())
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(SPACE_TIGHT.dp),
    ) {
        StepButton(
            icon = Icons.Filled.KeyboardArrowUp,
            description = stringResource(R.string.options_time_step_up, unit),
            onClick = { onChange(fields.stepped(field, 1)) },
        )
        OutlinedTextField(
            value = fields.valueOf(field).toString(),
            onValueChange = { typed ->
                onChange(fields.withValue(field, typed.filterDigits().toIntOrNull() ?: 0))
            },
            label = { Text(unit) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier =
                Modifier
                    .width(FIELD_WIDTH_DP.dp)
                    .semantics { contentDescription = unit },
        )
        StepButton(
            icon = Icons.Filled.KeyboardArrowDown,
            description = stringResource(R.string.options_time_step_down, unit),
            onClick = { onChange(fields.stepped(field, -1)) },
        )
    }
}

@Composable
private fun StepButton(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
) {
    FilledTonalIconButton(onClick = onClick) {
        Icon(imageVector = icon, contentDescription = description)
    }
}

/** 칸에 붙는 단위 문구. */
private fun TimeLimitField.unitLabelRes(): Int =
    when (this) {
        TimeLimitField.HOURS -> R.string.options_time_unit_hours
        TimeLimitField.MINUTES -> R.string.options_time_unit_minutes
        TimeLimitField.SECONDS -> R.string.options_time_unit_seconds
    }

/** 숫자만 남기고 최대 2자리로 제한한다 (시/분/초 각 최대 2자리면 12시간 표현 충분). */
private fun String.filterDigits(): String = filter { it.isDigit() }.take(TimeLimitFields.MAX_DIGITS)

/** 칸 안·칸 사이의 간격. 플로팅 버블의 입력 창과 같은 눈금을 쓴다. */
private const val SPACE_TIGHT = 8

/** 입력 묶음·안내 문구를 가르는 간격. */
private const val SPACE_SECTION = 16

private const val FIELD_WIDTH_DP = 84
