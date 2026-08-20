package io.rami.screenrecorder.presentation.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.rami.screenrecorder.domain.model.Recording
import io.rami.screenrecorder.presentation.R
import io.rami.screenrecorder.presentation.library.DeleteConfirmDialog
import io.rami.screenrecorder.presentation.library.DetailDialog
import io.rami.screenrecorder.presentation.library.RenameDialog
import io.rami.screenrecorder.presentation.library.shareRecording

// 플레이어의 배속 선택과 더보기 메뉴. 컨트롤 배치는 PlayerControls.kt 참조.

/** 배속 선택 (기능명세서 10절: 0.5x~2.0x, 0.1 단위). */
@Composable
internal fun SpeedSelector(
    playbackSpeed: Float,
    onSpeedSelected: (Float) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        Row(
            modifier =
                Modifier
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = GLASS_ALPHA))
                    .clickable { expanded = true }
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                imageVector = Icons.Default.Speed,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(16.dp),
            )
            Text(
                text = stringResource(R.string.player_speed_format, formatSpeed(playbackSpeed)),
                style = MaterialTheme.typography.labelMedium,
                color = Color.White,
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            PLAYBACK_SPEEDS.forEach { speed ->
                DropdownMenuItem(
                    text = {
                        Text(
                            stringResource(R.string.player_speed_format, formatSpeed(speed)) +
                                if (speed == playbackSpeed) "  ✓" else "",
                        )
                    },
                    onClick = {
                        onSpeedSelected(speed)
                        expanded = false
                    },
                )
            }
        }
    }
}

/** 더보기 메뉴 (기능명세서 10절: 이름 변경/공유/상세 정보/삭제). */
@Composable
internal fun PlayerOverflowMenu(
    recording: Recording,
    callbacks: PlayerCallbacks,
    onInteraction: () -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var expanded by remember { mutableStateOf(false) }
    var showRename by remember { mutableStateOf(false) }
    var showDetail by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }

    Box {
        GlassIconButton(
            icon = Icons.Default.MoreVert,
            contentDescription = stringResource(R.string.menu_more),
            onClick = {
                onInteraction()
                expanded = true
            },
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            PlayerMenuItem(R.string.menu_rename) {
                expanded = false
                showRename = true
            }
            PlayerMenuItem(R.string.menu_share) {
                expanded = false
                shareRecording(context, recording)
            }
            PlayerMenuItem(R.string.menu_details) {
                expanded = false
                showDetail = true
            }
            PlayerMenuItem(R.string.menu_delete) {
                expanded = false
                showDelete = true
            }
        }
    }

    if (showRename) {
        RenameDialog(
            initialName = recording.displayName,
            onConfirm = callbacks.onRename,
            onDismiss = { showRename = false },
        )
    }
    if (showDetail) {
        DetailDialog(recording = recording, onDismiss = { showDetail = false })
    }
    if (showDelete) {
        DeleteConfirmDialog(
            count = 1,
            onConfirm = callbacks.onDelete,
            onDismiss = { showDelete = false },
        )
    }
}

@Composable
private fun PlayerMenuItem(
    labelRes: Int,
    onClick: () -> Unit,
) {
    DropdownMenuItem(text = { Text(stringResource(labelRes)) }, onClick = onClick)
}

/** 항상 소수 첫째 자리까지 표시한다 (예: 0.5, 1.0, 1.5). */
private fun formatSpeed(speed: Float): String = "%.1f".format(speed)

/** 컨트롤 바 위 반투명 pill 배경의 불투명도. */
private const val GLASS_ALPHA = 0.15f
