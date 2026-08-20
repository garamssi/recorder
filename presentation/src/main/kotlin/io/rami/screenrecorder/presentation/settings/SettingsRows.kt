package io.rami.screenrecorder.presentation.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.rami.screenrecorder.domain.model.AppSettings
import io.rami.screenrecorder.domain.model.BitrateOption
import io.rami.screenrecorder.domain.model.FileNamePrefix
import io.rami.screenrecorder.domain.model.Resolution
import io.rami.screenrecorder.domain.model.ResolutionOption
import io.rami.screenrecorder.domain.model.VolumePercent
import io.rami.screenrecorder.presentation.R

// 설정 화면 공용 행/필드와 선택지 상수.

@Composable
internal fun <T> ChoiceRow(
    title: String,
    options: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    onSelected: (T) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text = title, style = MaterialTheme.typography.bodyLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { option ->
                FilterChip(
                    selected = option == selected,
                    onClick = { onSelected(option) },
                    label = { Text(label(option)) },
                )
            }
        }
    }
}

@Composable
internal fun VolumeRow(
    title: String,
    volume: VolumePercent,
    onChanged: (VolumePercent) -> Unit,
) {
    Column {
        Text(
            text =
                "$title — " +
                    stringResource(R.string.settings_volume_percent_format, volume.value),
            style = MaterialTheme.typography.bodyLarge,
        )
        Slider(
            value = volume.value.toFloat(),
            onValueChange = { raw -> onChanged(VolumePercent(raw.toInt())) },
            valueRange = VolumePercent.MIN.toFloat()..VolumePercent.MAX.toFloat(),
        )
    }
}

@Composable
internal fun ToggleRow(
    title: String,
    checked: Boolean,
    onChanged: (Boolean) -> Unit,
    hint: String? = null,
    enabled: Boolean = true,
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color =
                    if (enabled) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
            )
            Switch(checked = checked, onCheckedChange = onChanged, enabled = enabled)
        }
        if (hint != null) {
            Text(
                text = hint,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** 파일명 접두어 입력 (기능명세서 4.3절: 영문/숫자/언더스코어). 유효할 때만 저장한다. */
@Composable
internal fun FilePrefixField(
    current: FileNamePrefix,
    update: ((AppSettings) -> AppSettings) -> Unit,
) {
    var text by rememberSaveable(current.value) { mutableStateOf(current.value) }
    val isValid = runCatching { FileNamePrefix(text) }.isSuccess
    Column {
        OutlinedTextField(
            value = text,
            onValueChange = { input ->
                text = input
                runCatching { FileNamePrefix(input) }.onSuccess { prefix ->
                    update { it.copy(fileNamePrefix = prefix) }
                }
            },
            label = { Text(stringResource(R.string.settings_file_prefix)) },
            supportingText = { Text(stringResource(R.string.settings_prefix_rule)) },
            isError = !isValid,
            singleLine = true,
        )
    }
}

/** 설정 화면 해상도 선택지 (기능명세서 4.1절). */
internal val resolutionSettingChoices =
    listOf(
        ResolutionOption.DeviceMax,
        ResolutionOption.Fixed(Resolution.FHD),
        ResolutionOption.Fixed(Resolution.HD),
    )

/** 설정 화면 비트레이트 선택지 (기능명세서 4.1절: 자동/8/12/15/20). */
internal val bitrateSettingChoices =
    listOf(
        BitrateOption.Auto,
        BitrateOption.Fixed(megabitsPerSecond = 8),
        BitrateOption.Fixed(megabitsPerSecond = 12),
        BitrateOption.Fixed(megabitsPerSecond = 15),
        BitrateOption.Fixed(megabitsPerSecond = 20),
    )

@Composable
internal fun LabeledValue(
    title: String,
    value: String,
) {
    Column {
        Text(text = title, style = MaterialTheme.typography.bodyLarge)
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
