package io.rami.screenrecorder.presentation.library

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.video.videoFrameMillis
import io.rami.screenrecorder.core.common.time.DurationFormatter
import io.rami.screenrecorder.core.designsystem.theme.CardCorner
import io.rami.screenrecorder.core.designsystem.theme.OverlayScrim
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
    onCompress: (Recording) -> Unit,
) {
    val actions = ItemActions(onPlay, onRename, onDetail, onDelete, onCompress)
    if (uiState.isGrid) {
        // 명세 7.1절: 그리드는 화면 폭 반응형이되 2~4열로 제한한다.
        BoxWithConstraints {
            val columns = (maxWidth / GRID_MIN_CELL).toInt().coerceIn(GRID_MIN_COLUMNS, GRID_MAX_COLUMNS)
            LazyVerticalGrid(
                columns = GridCells.Fixed(columns),
                verticalArrangement = Arrangement.spacedBy(ItemSpacing),
                horizontalArrangement = Arrangement.spacedBy(ItemSpacing),
            ) {
                items(uiState.recordings, key = { it.id.value }) { recording ->
                    GridCard(recording, uiState, viewModel, actions)
                }
            }
        }
    } else {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
    val onCompress: (Recording) -> Unit,
)

/** 선택 모드에서의 카드 상태 — 외곽선 색과 흐림 정도를 한 번에 계산한다. */
@Composable
private fun selectionAppearance(
    recording: Recording,
    uiState: LibraryUiState,
): Pair<Color, Float> {
    val isSelected = recording.id in uiState.selectedIds
    val border by animateColorAsState(
        targetValue =
            if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
        label = "cardBorder",
    )
    val dimmed = uiState.isSelectionMode && !isSelected
    return border to if (dimmed) UNSELECTED_ALPHA else 1f
}

/** 리스트 행 (DESIGN_GUIDE.md 4절: 썸네일 + 제목 + 보조 정보 + 더보기). */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ListRow(
    recording: Recording,
    uiState: LibraryUiState,
    viewModel: LibraryViewModel,
    actions: ItemActions,
) {
    val (borderColor, contentAlpha) = selectionAppearance(recording, uiState)
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .alpha(contentAlpha)
                .clip(CardCorner)
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .border(BorderStroke(1.dp, borderColor), CardCorner)
                .combinedClickable(
                    onClick = {
                        if (uiState.isSelectionMode) {
                            viewModel.onItemLongPress(recording.id)
                        } else {
                            actions.onPlay(recording)
                        }
                    },
                    onLongClick = { viewModel.onItemLongPress(recording.id) },
                ).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (uiState.isSelectionMode) {
            SelectionCheck(selected = recording.id in uiState.selectedIds)
        }
        Thumbnail(
            recording = recording,
            modifier = Modifier.size(width = LIST_THUMB_WIDTH, height = LIST_THUMB_HEIGHT),
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = recording.displayName,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = itemInfo(recording),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        ItemMenu(recording, actions)
    }
}

/** 그리드 카드 (DESIGN_GUIDE.md 4절: 16:9 썸네일 + 재생 오버레이 + 선택 체크). */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GridCard(
    recording: Recording,
    uiState: LibraryUiState,
    viewModel: LibraryViewModel,
    actions: ItemActions,
) {
    val (borderColor, contentAlpha) = selectionAppearance(recording, uiState)
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .alpha(contentAlpha)
                .clip(CardCorner)
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .border(BorderStroke(1.dp, borderColor), CardCorner)
                .combinedClickable(
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
        // 썸네일은 항상 16:9로 고정하고 프레임을 Crop해 채운다 → 원본 비율과 무관하게 균일한 규격.
        Box(modifier = Modifier.fillMaxWidth().aspectRatio(GRID_ASPECT_RATIO)) {
            Thumbnail(recording = recording, modifier = Modifier.fillMaxSize(), corner = 0.dp)
            if (uiState.isSelectionMode) {
                Box(modifier = Modifier.align(Alignment.TopStart).padding(12.dp)) {
                    SelectionCheck(selected = recording.id in uiState.selectedIds)
                }
            }
        }
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = recording.displayName,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = formatDateTime(recording.createdAtEpochMillis),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Text(
                    text = formatMegabytes(recording.sizeBytes),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** 선택 표시 — 선택되면 채워진 체크, 아니면 빈 원. */
@Composable
private fun SelectionCheck(selected: Boolean) {
    Icon(
        imageVector = if (selected) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
        contentDescription = null,
        tint = if (selected) MaterialTheme.colorScheme.primary else Color.White,
        modifier = Modifier.size(24.dp),
    )
}

/** 비디오 썸네일 (기능명세서 7.1절: 1초 지점 프레임, 재생시간 뱃지). */
@Composable
private fun Thumbnail(
    recording: Recording,
    modifier: Modifier = Modifier,
    corner: androidx.compose.ui.unit.Dp = THUMBNAIL_CORNER,
) {
    val context = LocalContext.current
    Box(
        modifier =
            modifier
                .clip(
                    androidx.compose.foundation.shape
                        .RoundedCornerShape(corner),
                ).background(MaterialTheme.colorScheme.surfaceContainerHighest),
    ) {
        // 전역 싱글턴 로더(Application의 SingletonImageLoader.Factory)가 비디오 프레임을 디코딩한다.
        AsyncImage(
            model =
                ImageRequest
                    .Builder(context)
                    .data(recording.contentUri.toUri())
                    .videoFrameMillis(THUMBNAIL_FRAME_MILLIS)
                    .build(),
            contentDescription = null,
            // 주어진 박스를 균일하게 채운다 — 원본 프레임 비율과 무관하게 크롭.
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        Text(
            text = DurationFormatter.formatElapsed(recording.duration),
            color = Color.White,
            style = MaterialTheme.typography.labelSmall,
            modifier =
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp)
                    .clip(
                        androidx.compose.foundation.shape
                            .RoundedCornerShape(4.dp),
                    ).background(OverlayScrim)
                    .padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

/** 목록 항목 보조 정보 한 줄 (생성일 · 해상도 · 용량). */
@Composable
private fun itemInfo(recording: Recording): String =
    stringResource(
        R.string.library_item_info_format,
        formatDateTime(recording.createdAtEpochMillis),
        recording.resolution.height,
        formatMegabytes(recording.sizeBytes),
    )

private val GRID_MIN_CELL = 300.dp
private val LIST_THUMB_WIDTH = 160.dp
private val LIST_THUMB_HEIGHT = 90.dp
private val THUMBNAIL_CORNER = 12.dp
private const val GRID_MIN_COLUMNS = 2
private const val GRID_MAX_COLUMNS = 4
private const val GRID_ASPECT_RATIO = 16f / 9f
private const val THUMBNAIL_FRAME_MILLIS = 1_000L
