package io.rami.screenrecorder.presentation.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.rami.screenrecorder.core.designsystem.component.SelectableTile
import io.rami.screenrecorder.domain.model.CaptureModeKind
import io.rami.screenrecorder.domain.model.RecordingState
import io.rami.screenrecorder.presentation.R

/** 화면 제목 — 녹화 중에는 문구가 상태를 반영한다. */
@Composable
internal fun HomeHeader(recordingState: RecordingState) {
    val isIdle = recordingState is RecordingState.Idle
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text =
                stringResource(
                    if (isIdle) R.string.home_ready_title else R.string.home_in_session_title,
                ),
            style = MaterialTheme.typography.headlineLarge,
        )
        Text(
            text =
                stringResource(
                    if (isIdle) R.string.home_ready_subtitle else R.string.home_in_session_subtitle,
                ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** 캡처 모드 선택 카드 3종 (기능명세서 2.1절: 마지막 선택 유지). */
@Composable
internal fun CaptureModeCards(
    selected: CaptureModeKind,
    onSelected: (CaptureModeKind) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxWidth()) {
        CaptureModeKind.entries.forEach { mode ->
            SelectableTile(
                icon = modeIcon(mode),
                title = stringResource(modeTitleRes(mode)),
                description = stringResource(modeDescriptionRes(mode)),
                selected = mode == selected,
                onClick = { onSelected(mode) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

private fun modeIcon(mode: CaptureModeKind): ImageVector =
    when (mode) {
        CaptureModeKind.FULL_SCREEN -> Icons.Default.Fullscreen
        CaptureModeKind.SINGLE_APP -> Icons.Default.Apps
        CaptureModeKind.REGION -> Icons.Default.Crop
    }

private fun modeTitleRes(mode: CaptureModeKind): Int =
    when (mode) {
        CaptureModeKind.FULL_SCREEN -> R.string.home_mode_full_screen
        CaptureModeKind.SINGLE_APP -> R.string.home_mode_single_app
        CaptureModeKind.REGION -> R.string.home_mode_region
    }

private fun modeDescriptionRes(mode: CaptureModeKind): Int =
    when (mode) {
        CaptureModeKind.FULL_SCREEN -> R.string.home_mode_full_screen_desc
        CaptureModeKind.SINGLE_APP -> R.string.home_mode_single_app_desc
        CaptureModeKind.REGION -> R.string.home_mode_region_desc
    }
