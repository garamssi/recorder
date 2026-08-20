package io.rami.screenrecorder.presentation.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.rami.screenrecorder.core.common.time.DurationFormatter
import io.rami.screenrecorder.core.designsystem.component.KineticCard
import io.rami.screenrecorder.core.designsystem.component.SecondaryActionButton
import io.rami.screenrecorder.domain.model.Recording
import io.rami.screenrecorder.presentation.R
import io.rami.screenrecorder.presentation.navigation.isLandscape

/** 최근 녹화 + 저장 공간 (기능명세서 2.1절). 가로에서는 나란히, 세로에서는 위아래로 놓는다. */
@Composable
internal fun HomeFooterRow(
    uiState: HomeUiState,
    actions: HomeActions,
    onPlay: (Recording) -> Unit,
) {
    if (isLandscape()) {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxWidth()) {
            RecentRecordingsCard(uiState, actions, onPlay, Modifier.weight(RECENT_WEIGHT))
            StorageCard(uiState, Modifier.weight(1f))
        }
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            RecentRecordingsCard(uiState, actions, onPlay, Modifier.fillMaxWidth())
            StorageCard(uiState, Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun RecentRecordingsCard(
    uiState: HomeUiState,
    actions: HomeActions,
    onPlay: (Recording) -> Unit,
    modifier: Modifier,
) {
    KineticCard(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.home_recent_title), style = MaterialTheme.typography.titleMedium)
            SecondaryActionButton(
                text = stringResource(R.string.home_view_all),
                onClick = actions.onOpenLibrary,
            )
        }
        if (uiState.recentRecordings.isEmpty()) {
            Text(
                text = stringResource(R.string.home_recent_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            uiState.recentRecordings.forEach { recording ->
                RecentRow(recording = recording, onPlay = onPlay)
            }
        }
    }
}

@Composable
private fun RecentRow(
    recording: Recording,
    onPlay: (Recording) -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(RECENT_ROW_HEIGHT)
                .clickable { onPlay(recording) },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = recording.displayName,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(end = 12.dp),
        )
        Text(
            text = DurationFormatter.formatElapsed(recording.duration),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun StorageCard(
    uiState: HomeUiState,
    modifier: Modifier,
) {
    KineticCard(modifier = modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(bottom = 12.dp),
        ) {
            Icon(
                imageVector = Icons.Default.Storage,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Text(stringResource(R.string.home_storage_title), style = MaterialTheme.typography.titleMedium)
        }
        Text(
            text = formatGigabytes(uiState.availableBytes),
            style = MaterialTheme.typography.headlineSmall,
        )
        Text(
            text =
                stringResource(
                    R.string.home_storage_estimate,
                    DurationFormatter.formatElapsed(uiState.estimatedRecordableTime),
                ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** 사용 가능한 저장 공간 표시 (GB). */
internal fun formatGigabytes(bytes: Long): String = "%.1fGB".format(bytes / BYTES_PER_GB)

private const val BYTES_PER_GB = 1_000_000_000f
private const val RECENT_WEIGHT = 1.4f
private val RECENT_ROW_HEIGHT = 44.dp
