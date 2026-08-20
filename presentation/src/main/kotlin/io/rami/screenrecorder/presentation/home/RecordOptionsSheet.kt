package io.rami.screenrecorder.presentation.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.rami.screenrecorder.core.common.time.DurationFormatter
import io.rami.screenrecorder.domain.model.AudioSource
import io.rami.screenrecorder.domain.model.FrameRate
import io.rami.screenrecorder.domain.model.RecordingConfig
import io.rami.screenrecorder.domain.model.Resolution
import io.rami.screenrecorder.domain.model.ResolutionOption
import io.rami.screenrecorder.domain.model.TimeLimit
import io.rami.screenrecorder.presentation.R
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/** 녹화 옵션 시트 (기능명세서 2.1절 빠른 선택, DESIGN_GUIDE 1b: 칩 선택형). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordOptionsSheet(
    preset: RecordingConfig,
    onPresetChanged: ((RecordingConfig) -> RecordingConfig) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.padding(horizontal = 32.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = stringResource(R.string.options_sheet_title),
                style = MaterialTheme.typography.titleMedium,
            )
            OptionRow(label = stringResource(R.string.options_resolution)) {
                resolutionChoices.forEach { option ->
                    FilterChip(
                        selected = preset.resolution == option,
                        onClick = { onPresetChanged { it.copy(resolution = option) } },
                        label = { Text(resolutionLabel(option)) },
                    )
                }
            }
            OptionRow(label = stringResource(R.string.options_frame_rate)) {
                FrameRate.entries.forEach { frameRate ->
                    FilterChip(
                        selected = preset.frameRate == frameRate,
                        onClick = { onPresetChanged { it.copy(frameRate = frameRate) } },
                        label = { Text(stringResource(R.string.preset_fps_format, frameRate.fps)) },
                    )
                }
            }
            OptionRow(label = stringResource(R.string.options_audio_source)) {
                AudioSource.entries.forEach { source ->
                    FilterChip(
                        selected = preset.audioSource == source,
                        onClick = { onPresetChanged { it.copy(audioSource = source) } },
                        label = { Text(audioSourceLabel(source)) },
                    )
                }
            }
            TimeLimitRow(preset = preset, onPresetChanged = onPresetChanged)
            Button(
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.End),
            ) {
                Text(stringResource(R.string.options_confirm))
            }
        }
    }
}

/** 시간 제한 선택 행 (기능명세서 11.4절 프리셋 + 직접 입력). */
@Composable
private fun TimeLimitRow(
    preset: RecordingConfig,
    onPresetChanged: ((RecordingConfig) -> RecordingConfig) -> Unit,
) {
    var showCustomInput by remember { mutableStateOf(false) }
    // 프리셋 목록에 없는 Limited 값이면 직접 입력한 것으로 본다.
    val isCustom =
        preset.timeLimit is TimeLimit.Limited &&
            (preset.timeLimit as TimeLimit.Limited).duration !in timeLimitChoices

    OptionRow(label = stringResource(R.string.options_time_limit)) {
        FilterChip(
            selected = preset.timeLimit is TimeLimit.None,
            onClick = { onPresetChanged { it.copy(timeLimit = TimeLimit.None) } },
            label = { Text(stringResource(R.string.options_time_limit_none)) },
        )
        timeLimitChoices.forEach { duration ->
            FilterChip(
                selected = (preset.timeLimit as? TimeLimit.Limited)?.duration == duration,
                onClick = { onPresetChanged { it.copy(timeLimit = TimeLimit.Limited(duration)) } },
                label = { Text(DurationFormatter.formatElapsed(duration)) },
            )
        }
        FilterChip(
            selected = isCustom,
            onClick = { showCustomInput = true },
            label = {
                Text(
                    if (isCustom) {
                        DurationFormatter.formatElapsed((preset.timeLimit as TimeLimit.Limited).duration)
                    } else {
                        stringResource(R.string.options_time_limit_custom)
                    },
                )
            },
        )
    }

    if (showCustomInput) {
        CustomTimeLimitDialog(
            onConfirm = { limit ->
                onPresetChanged { it.copy(timeLimit = limit) }
                showCustomInput = false
            },
            onDismiss = { showCustomInput = false },
        )
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun OptionRow(
    label: String,
    content: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium)
        // 칩이 많으면(예: 시간 제한 9개) 줄바꿈되도록 FlowRow를 쓴다.
        androidx.compose.foundation.layout.FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            content()
        }
    }
}

/** 해상도 선택지 (기능명세서 4.1절). */
private val resolutionChoices =
    listOf(
        ResolutionOption.DeviceMax,
        ResolutionOption.Fixed(Resolution.FHD),
        ResolutionOption.Fixed(Resolution.HD),
    )

/** 시간 제한 프리셋 (기능명세서 11.4절, 직접 입력은 후속). */
private val timeLimitChoices: List<Duration> =
    listOf(30.seconds, 1.minutes, 3.minutes, 5.minutes, 10.minutes, 30.minutes, 1.hours)
