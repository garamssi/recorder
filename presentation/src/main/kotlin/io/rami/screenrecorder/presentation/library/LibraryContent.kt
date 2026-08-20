package io.rami.screenrecorder.presentation.library

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.video.videoFrameMillis
import io.rami.screenrecorder.core.common.time.DurationFormatter
import io.rami.screenrecorder.domain.model.Recording
import io.rami.screenrecorder.presentation.R

/** 목록 본문: 리스트/그리드 (기능명세서 7.1절). */
@Composable
internal fun LibraryContent(
    uiState: LibraryUiState,
    viewModel: LibraryViewModel,
    onPlay: (Recording) -> Unit,
    onRename: (Recording) -> Unit,
    onDetail: (Recording) -> Unit,
    onDelete: (Recording) -> Unit,
) {
    val actions = ItemActions(onPlay, onRename, onDetail, onDelete)
    if (uiState.isGrid) {
        // 명세 7.1절: 그리드는 화면 폭 반응형이되 2~4열로 제한한다.
        androidx.compose.foundation.layout.BoxWithConstraints {
            val columns = (maxWidth / GRID_MIN_CELL_DP.dp).toInt().coerceIn(GRID_MIN_COLUMNS, GRID_MAX_COLUMNS)
            LazyVerticalGrid(
                columns = GridCells.Fixed(columns),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(uiState.recordings, key = { it.id.value }) { recording ->
                    GridCard(recording, uiState, viewModel, actions)
                }
            }
        }
    } else {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(uiState.recordings, key = { it.id.value }) { recording ->
                ListRow(recording, uiState, viewModel, actions)
            }
        }
    }
}

/** 항목별 액션 묶음 (기능명세서 7.2절 더보기 메뉴). */
internal class ItemActions(
    val onPlay: (Recording) -> Unit,
    val onRename: (Recording) -> Unit,
    val onDetail: (Recording) -> Unit,
    val onDelete: (Recording) -> Unit,
)

/** 리스트 행 (DESIGN_GUIDE 1f: 썸네일 168x94 + 제목 + 보조 + more_vert). */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ListRow(
    recording: Recording,
    uiState: LibraryUiState,
    viewModel: LibraryViewModel,
    actions: ItemActions,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = {
                        if (uiState.isSelectionMode) {
                            viewModel.onItemLongPress(recording.id)
                        } else {
                            actions.onPlay(recording)
                        }
                    },
                    onLongClick = { viewModel.onItemLongPress(recording.id) },
                ).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (uiState.isSelectionMode) {
            Checkbox(
                checked = recording.id in uiState.selectedIds,
                onCheckedChange = { viewModel.onItemLongPress(recording.id) },
            )
        }
        Thumbnail(recording, Modifier.size(width = 168.dp, height = 94.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = recording.displayName,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
            )
            Text(
                text =
                    stringResource(
                        R.string.library_item_info_format,
                        formatDateTime(recording.createdAtEpochMillis),
                        recording.resolution.height,
                        formatMegabytes(recording.sizeBytes),
                    ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        ItemMenu(recording, actions)
    }
}

/** 그리드 카드 (DESIGN_GUIDE 1h: 16:9 r12, 선택 시 체크). */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GridCard(
    recording: Recording,
    uiState: LibraryUiState,
    viewModel: LibraryViewModel,
    actions: ItemActions,
) {
    Column(
        modifier =
            Modifier.combinedClickable(
                onClick = {
                    if (uiState.isSelectionMode) {
                        viewModel.onItemLongPress(recording.id)
                    } else {
                        actions.onPlay(recording)
                    }
                },
                onLongClick = { viewModel.onItemLongPress(recording.id) },
            ),
    ) {
        Box {
            Thumbnail(
                recording,
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(GRID_ASPECT_RATIO),
            )
            if (recording.id in uiState.selectedIds) {
                Checkbox(checked = true, onCheckedChange = null, modifier = Modifier.align(Alignment.TopEnd))
            }
        }
        Text(text = recording.displayName, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
    }
}

/** 비디오 썸네일 (기능명세서 7.1절: 1초 지점 프레임, 재생시간 뱃지). */
@Composable
private fun Thumbnail(
    recording: Recording,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    Box(modifier = modifier.clip(RoundedCornerShape(10.dp))) {
        // 전역 싱글턴 로더(Application의 SingletonImageLoader.Factory)가 비디오 프레임을 디코딩한다.
        AsyncImage(
            model =
                ImageRequest
                    .Builder(context)
                    .data(recording.contentUri.toUri())
                    .videoFrameMillis(THUMBNAIL_FRAME_MILLIS)
                    .build(),
            contentDescription = null,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text = DurationFormatter.formatElapsed(recording.duration),
            color = androidx.compose.ui.graphics.Color.White,
            style = MaterialTheme.typography.labelSmall,
            modifier =
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(4.dp),
        )
    }
}

/** 더보기 메뉴 (기능명세서 7.2절: 재생/이름 변경/공유/상세 정보/삭제. 압축은 Stage 8). */
@Composable
private fun ItemMenu(
    recording: Recording,
    actions: ItemActions,
) {
    var expanded by remember { mutableStateOf(false) }
    val context = LocalContext.current
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.menu_more))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            MenuItem(R.string.menu_play) {
                expanded = false
                actions.onPlay(recording)
            }
            MenuItem(R.string.menu_rename) {
                expanded = false
                actions.onRename(recording)
            }
            MenuItem(R.string.menu_share) {
                expanded = false
                shareRecording(context, recording)
            }
            MenuItem(R.string.menu_details) {
                expanded = false
                actions.onDetail(recording)
            }
            MenuItem(R.string.menu_delete) {
                expanded = false
                actions.onDelete(recording)
            }
        }
    }
}

@Composable
private fun MenuItem(
    labelRes: Int,
    onClick: () -> Unit,
) {
    DropdownMenuItem(text = { Text(stringResource(labelRes)) }, onClick = onClick)
}

/** 다중 공유 (기능명세서 7.3절: 선택 모드 상단 공유). */
internal fun shareRecordings(
    context: Context,
    recordings: List<Recording>,
) {
    if (recordings.isEmpty()) return
    if (recordings.size == 1) {
        shareRecording(context, recordings.first())
        return
    }
    val uris = ArrayList(recordings.map { it.contentUri.toUri() })
    val sendIntent =
        Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = SHARE_MIME_TYPE
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    context.startActivity(Intent.createChooser(sendIntent, null))
}

/** 공유 (기능명세서 7.2절: ACTION_SEND + 읽기 권한 부여). */
internal fun shareRecording(
    context: Context,
    recording: Recording,
) {
    val sendIntent =
        Intent(Intent.ACTION_SEND).apply {
            type = SHARE_MIME_TYPE
            putExtra(Intent.EXTRA_STREAM, recording.contentUri.toUri())
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    context.startActivity(Intent.createChooser(sendIntent, null))
}

private const val GRID_MIN_CELL_DP = 280
private const val GRID_MIN_COLUMNS = 2
private const val GRID_MAX_COLUMNS = 4
private const val GRID_ASPECT_RATIO = 16f / 9f
private const val THUMBNAIL_FRAME_MILLIS = 1_000L
private const val SHARE_MIME_TYPE = "video/mp4"
