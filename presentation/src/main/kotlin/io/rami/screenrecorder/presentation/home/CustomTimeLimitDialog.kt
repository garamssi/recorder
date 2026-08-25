package io.rami.screenrecorder.presentation.home

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import io.rami.screenrecorder.domain.model.TimeLimit
import io.rami.screenrecorder.domain.model.TimeLimitField
import io.rami.screenrecorder.domain.model.TimeLimitFields
import io.rami.screenrecorder.domain.model.TimeLimitInput
import io.rami.screenrecorder.presentation.R
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 녹화 시간 제한 입력 창 (기능명세서 11.4절 [결정]: 시/분/초, 10초~12시간).
 *
 * 플로팅 버블의 입력 창과 같은 창이다 — 제목·안내 문구·증감 버튼 모양·버튼 구성이 같고,
 * 카드 안의 모든 줄을 가운데 한 축에 맞춘다. 버블은 오버레이라 플랫폼 뷰로 그려야 해서
 * 코드를 공유할 수 없고, 문구와 치수를 같게 두어 맞춘다 (service 모듈 TimeLimitInputView.kt).
 *
 * 키보드로 쳐 넣을 수도, 각 칸의 증감 버튼으로 1씩 옮길 수도 있다. 두 방식 모두
 * [TimeLimitFields]의 같은 규칙을 거치므로 칸별 범위와 총합 검증이 어긋나지 않는다.
 *
 * @param current 미리 채울 현재 설정값.
 */
@Composable
fun CustomTimeLimitDialog(
    current: TimeLimit,
    onConfirm: (TimeLimit) -> Unit,
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

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(CARD_CORNER.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            Column(
                modifier = Modifier.padding(CARD_PADDING.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(SPACE_SECTION.dp),
            ) {
                Text(
                    text = stringResource(R.string.options_time_limit_custom_title),
                    style = MaterialTheme.typography.titleMedium,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(SPACE_TIGHT.dp)) {
                    TimeLimitField.entries.forEach { field ->
                        TimeFieldColumn(field = field, fields = fields, onChange = onChange)
                    }
                }
                ValidationMessage(validation)
                Row(horizontalArrangement = Arrangement.spacedBy(SPACE_TIGHT.dp)) {
                    MutedButton(stringResource(R.string.options_time_limit_none)) {
                        onConfirm(TimeLimit.None)
                    }
                    MutedButton(stringResource(R.string.dialog_cancel), onDismiss)
                    Button(
                        enabled = validation is TimeLimitInput.Valid,
                        onClick = {
                            (validation as? TimeLimitInput.Valid)?.let { onConfirm(it.timeLimit) }
                        },
                    ) { Text(stringResource(R.string.options_time_limit_save)) }
                }
            }
        }
    }
}

/** 강조하지 않는 하단 버튼 — 버블 창의 회색 글자 버튼에 해당한다. */
@Composable
private fun MutedButton(
    text: String,
    onClick: () -> Unit,
) {
    TextButton(
        onClick = onClick,
        colors =
            ButtonDefaults.textButtonColors(
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            ),
    ) { Text(text) }
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

/** 증감 버튼 두 개와 입력 칸, 단위를 세로로 쌓은 한 칸. */
@Composable
private fun TimeFieldColumn(
    field: TimeLimitField,
    fields: TimeLimitFields,
    onChange: (TimeLimitFields) -> Unit,
) {
    val unit = stringResource(field.unitLabelRes())
    Column(
        modifier = Modifier.width(FIELD_WIDTH.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(SPACE_TIGHT.dp),
    ) {
        StepButton(
            glyph = STEP_UP_GLYPH,
            description = stringResource(R.string.options_time_step_up, unit),
            onStep = { onChange(fields.stepped(field, 1)) },
        )
        NumberField(
            value = fields.valueOf(field),
            description = unit,
            onValueChange = { onChange(fields.withValue(field, it)) },
        )
        StepButton(
            glyph = STEP_DOWN_GLYPH,
            description = stringResource(R.string.options_time_step_down, unit),
            onStep = { onChange(fields.stepped(field, -1)) },
        )
        Text(
            text = unit,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** 숫자 두 자리 입력 칸. */
@Composable
private fun NumberField(
    value: Int,
    description: String,
    onValueChange: (Int) -> Unit,
) {
    BasicTextField(
        value = value.toString(),
        onValueChange = { typed -> onValueChange(typed.filterDigits().toIntOrNull() ?: 0) },
        singleLine = true,
        textStyle =
            TextStyle(
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = FIELD_TEXT_SP.sp,
                textAlign = TextAlign.Center,
            ),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier =
            Modifier
                .fillMaxWidth()
                .background(
                    MaterialTheme.colorScheme.surfaceContainerHighest,
                    RoundedCornerShape(CARD_CORNER.dp),
                ).padding(vertical = FIELD_PADDING.dp)
                .semantics { contentDescription = description },
    )
}

/**
 * 삼각형 글리프 하나짜리 증감 버튼.
 *
 * 길게 누르면 연속으로 증감한다 (기능명세서 11.4절). 누르고 있는 동안 반복하려면
 * 클릭 콜백만으로는 부족해 누름과 뗌을 직접 다룬다.
 */
@Composable
private fun StepButton(
    glyph: String,
    description: String,
    onStep: () -> Unit,
) {
    Text(
        text = glyph,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurface,
        textAlign = TextAlign.Center,
        modifier =
            Modifier
                .fillMaxWidth()
                .background(
                    MaterialTheme.colorScheme.surfaceContainerHighest,
                    RoundedCornerShape(STEP_CORNER.dp),
                ).padding(vertical = STEP_PADDING.dp)
                .semantics { contentDescription = description }
                .pointerInput(onStep) {
                    detectTapGestures(
                        onPress = {
                            onStep()
                            coroutineScope {
                                val repeat =
                                    launch {
                                        delay(REPEAT_START_MS)
                                        while (true) {
                                            onStep()
                                            delay(REPEAT_INTERVAL_MS)
                                        }
                                    }
                                tryAwaitRelease()
                                repeat.cancel()
                            }
                        },
                    )
                },
    )
}

/** 칸에 붙는 단위 문구. */
private fun TimeLimitField.unitLabelRes(): Int =
    when (this) {
        TimeLimitField.HOURS -> R.string.options_time_unit_hours
        TimeLimitField.MINUTES -> R.string.options_time_unit_minutes
        TimeLimitField.SECONDS -> R.string.options_time_unit_seconds
    }

/** 숫자만 남기고 상한 자릿수로 제한한다. */
private fun String.filterDigits(): String = filter { it.isDigit() }.take(TimeLimitFields.MAX_DIGITS)

/** 칸 안·칸 사이·버튼 사이의 간격. 플로팅 버블의 입력 창과 같은 눈금을 쓴다. */
private const val SPACE_TIGHT = 8

/** 제목·입력·안내·버튼을 가르는 간격. */
private const val SPACE_SECTION = 16

private const val FIELD_WIDTH = 64
private const val FIELD_PADDING = 8
private const val FIELD_TEXT_SP = 20
private const val CARD_CORNER = 12
private const val CARD_PADDING = 20
private const val STEP_CORNER = 10
private const val STEP_PADDING = 10
private const val STEP_UP_GLYPH = "▲"
private const val STEP_DOWN_GLYPH = "▼"
private const val REPEAT_START_MS = 400L
private const val REPEAT_INTERVAL_MS = 90L
