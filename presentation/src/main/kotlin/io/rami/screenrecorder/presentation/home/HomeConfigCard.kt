package io.rami.screenrecorder.presentation.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.rami.screenrecorder.core.common.time.DurationFormatter
import io.rami.screenrecorder.core.designsystem.component.ConfigBadge
import io.rami.screenrecorder.core.designsystem.component.KineticCard
import io.rami.screenrecorder.core.designsystem.component.SecondaryActionButton
import io.rami.screenrecorder.domain.model.TimeLimit
import io.rami.screenrecorder.presentation.R
import io.rami.screenrecorder.presentation.navigation.isLandscape

/** 현재 프리셋 요약 + 옵션 시트 진입 (기능명세서 2.1절). */
@Composable
internal fun ActiveConfigurationCard(
    uiState: HomeUiState,
    onOpenOptions: () -> Unit,
) {
    KineticCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.home_active_configuration),
                style = MaterialTheme.typography.titleMedium,
            )
            SecondaryActionButton(
                text = stringResource(R.string.home_edit_options),
                icon = Icons.Default.Tune,
                onClick = onOpenOptions,
            )
        }
        ConfigBadgeGrid(uiState = uiState)
    }
}

@Composable
private fun ConfigBadgeGrid(uiState: HomeUiState) {
    val preset = uiState.preset
    val badges =
        listOf(
            BadgeSpec(
                Icons.Default.HighQuality,
                stringResource(R.string.options_resolution),
                resolutionLabel(preset.resolution),
            ),
            BadgeSpec(
                Icons.Default.Speed,
                stringResource(R.string.options_frame_rate),
                stringResource(R.string.preset_fps_format, preset.frameRate.fps),
            ),
            BadgeSpec(Icons.Default.GraphicEq, stringResource(R.string.settings_bitrate), bitrateLabel(preset.bitrate)),
            BadgeSpec(
                Icons.Default.Mic,
                stringResource(R.string.options_audio_source),
                audioSourceLabel(preset.audioSource),
            ),
            BadgeSpec(
                Icons.Default.Timer,
                stringResource(R.string.options_time_limit),
                timeLimitLabel(preset.timeLimit),
            ),
        )
    val badgesPerRow = if (isLandscape()) BADGES_PER_ROW_WIDE else BADGES_PER_ROW_NARROW
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        badges.chunked(badgesPerRow).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                row.forEach { badge ->
                    ConfigBadge(
                        icon = badge.icon,
                        label = badge.label,
                        value = badge.value,
                        modifier = Modifier.weight(1f),
                    )
                }
                // 마지막 줄이 덜 찼을 때 남은 칸을 비워 뱃지 폭을 고르게 유지한다.
                repeat(badgesPerRow - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

private class BadgeSpec(
    val icon: ImageVector,
    val label: String,
    val value: String,
)

@Composable
private fun timeLimitLabel(timeLimit: TimeLimit): String =
    when (timeLimit) {
        is TimeLimit.None -> stringResource(R.string.options_time_limit_none)
        is TimeLimit.Limited -> DurationFormatter.formatElapsed(timeLimit.duration)
    }

private const val BADGES_PER_ROW_WIDE = 3
private const val BADGES_PER_ROW_NARROW = 2
