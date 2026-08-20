package io.rami.screenrecorder.presentation.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.rami.screenrecorder.domain.model.TimeLimit
import io.rami.screenrecorder.domain.model.TimeLimitInput
import io.rami.screenrecorder.presentation.R

/**
 * 타이머 직접 입력 다이얼로그 (기능명세서 11.4절 [결정]: 시/분/초, 10초~12시간).
 *
 * 범위를 벗어나면 저장 버튼을 비활성화하고 사유를 표시한다 (입력 단계 차단).
 */
@Composable
fun CustomTimeLimitDialog(
    onConfirm: (TimeLimit.Limited) -> Unit,
    onDismiss: () -> Unit,
) {
    var hours by rememberSaveable { mutableStateOf("0") }
    var minutes by rememberSaveable { mutableStateOf("0") }
    var seconds by rememberSaveable { mutableStateOf("0") }

    val validation =
        TimeLimit.fromHoursMinutesSeconds(
            hours = hours.toIntOrNull() ?: 0,
            minutes = minutes.toIntOrNull() ?: 0,
            seconds = seconds.toIntOrNull() ?: 0,
        )
    val errorMessage =
        when (validation) {
            is TimeLimitInput.TooShort -> stringResource(R.string.options_time_limit_too_short)
            is TimeLimitInput.TooLong -> stringResource(R.string.options_time_limit_too_long)
            is TimeLimitInput.Valid -> null
        }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.options_time_limit_custom_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TimeField(
                        value = hours,
                        onValueChange = { hours = it.filterDigits() },
                        label = stringResource(R.string.options_time_unit_hours),
                    )
                    TimeField(
                        value = minutes,
                        onValueChange = { minutes = it.filterDigits() },
                        label = stringResource(R.string.options_time_unit_minutes),
                    )
                    TimeField(
                        value = seconds,
                        onValueChange = { seconds = it.filterDigits() },
                        label = stringResource(R.string.options_time_unit_seconds),
                    )
                }
                if (errorMessage != null) {
                    Text(
                        text = errorMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
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

@Composable
private fun TimeField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions =
            androidx.compose.foundation.text
                .KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.width(FIELD_WIDTH_DP.dp),
    )
}

/** 숫자만 남기고 최대 2자리로 제한한다 (시/분/초 각 최대 2자리면 12시간 표현 충분). */
private fun String.filterDigits(): String = filter { it.isDigit() }.take(MAX_FIELD_DIGITS)

private const val FIELD_WIDTH_DP = 96
private const val MAX_FIELD_DIGITS = 2
