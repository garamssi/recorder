package io.rami.screenrecorder.presentation.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.rami.screenrecorder.core.designsystem.component.KineticSwitch
import io.rami.screenrecorder.core.designsystem.component.SettingRow
import io.rami.screenrecorder.core.designsystem.component.SettingValue
import io.rami.screenrecorder.core.designsystem.theme.ControlCorner
import io.rami.screenrecorder.domain.model.AppSettings
import io.rami.screenrecorder.domain.model.BitrateOption
import io.rami.screenrecorder.domain.model.FileNamePrefix
import io.rami.screenrecorder.domain.model.Resolution
import io.rami.screenrecorder.domain.model.ResolutionOption
import io.rami.screenrecorder.domain.model.VolumePercent
import io.rami.screenrecorder.presentation.R

// 설정 카드 안의 공용 행. 화면 골격은 SettingsScreen.kt, 섹션 구성은 SettingsSections.kt 참조.

/**
 * 선택지를 드롭다운으로 고르는 행 (DESIGN_GUIDE.md 4절: 값 + chevron).
 *
 * 현재 값을 오른쪽에 보여 주고, 탭하면 선택지 메뉴가 열린다.
 */
@Composable
internal fun <T> ChoiceRow(
    title: String,
    options: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    onSelected: (T) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        SettingRow(
            label = title,
            onClick = { expanded = true },
            trailing = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    SettingValue(label(selected))
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                }
            },
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(label(option) + if (option == selected) "  ✓" else "") },
                    onClick = {
                        onSelected(option)
                        expanded = false
                    },
                )
            }
        }
    }
}

/** 켬/끔 행 (DESIGN_GUIDE.md 5절 "Toggles"). */
@Composable
internal fun ToggleRow(
    title: String,
    checked: Boolean,
    onChanged: (Boolean) -> Unit,
    hint: String? = null,
    enabled: Boolean = true,
) {
    SettingRow(
        label = title,
        supportingText = hint,
        onClick = if (enabled) ({ onChanged(!checked) }) else null,
        trailing = { KineticSwitch(checked = checked, onCheckedChange = onChanged, enabled = enabled) },
    )
}

/** 볼륨 슬라이더 행 (기능명세서 4.2절: 0~200%). */
@Composable
internal fun VolumeRow(
    title: String,
    volume: VolumePercent,
    onChanged: (VolumePercent) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            SettingValue(stringResource(R.string.settings_volume_percent_format, volume.value))
        }
        Slider(
            value = volume.value.toFloat(),
            onValueChange = { raw -> onChanged(VolumePercent(raw.toInt())) },
            valueRange = VolumePercent.MIN.toFloat()..VolumePercent.MAX.toFloat(),
        )
    }
}

/** 값만 보여 주는 읽기 전용 행. */
@Composable
internal fun LabeledValue(
    title: String,
    value: String,
    hint: String? = null,
) {
    SettingRow(label = title, supportingText = hint, trailing = { SettingValue(value) })
}

/** 파일명 접두어 입력 (기능명세서 4.3절: 영문/숫자/언더스코어). 유효할 때만 저장한다. */
@Composable
internal fun FilePrefixField(
    current: FileNamePrefix,
    update: ((AppSettings) -> AppSettings) -> Unit,
) {
    var text by rememberSaveable(current.value) { mutableStateOf(current.value) }
    val isValid = runCatching { FileNamePrefix(text) }.isSuccess
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp)) {
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
            shape = ControlCorner,
            modifier = Modifier.fillMaxWidth(),
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
