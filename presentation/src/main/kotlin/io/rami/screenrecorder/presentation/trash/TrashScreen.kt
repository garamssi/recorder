package io.rami.screenrecorder.presentation.trash

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.RestoreFromTrash
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.video.videoFrameMillis
import io.rami.screenrecorder.core.designsystem.component.CircleIconButton
import io.rami.screenrecorder.core.designsystem.component.EmptyState
import io.rami.screenrecorder.core.designsystem.component.PrimaryActionButton
import io.rami.screenrecorder.core.designsystem.component.SecondaryActionButton
import io.rami.screenrecorder.core.designsystem.theme.CardCorner
import io.rami.screenrecorder.domain.model.RecordingId
import io.rami.screenrecorder.domain.model.TrashItem
import io.rami.screenrecorder.presentation.R
import io.rami.screenrecorder.presentation.home.ContentMaxWidth
import io.rami.screenrecorder.presentation.home.ScreenPadding
import io.rami.screenrecorder.presentation.library.ItemSpacing

/** 휴지통 화면 (기능명세서 9절, DESIGN_GUIDE.md 4절 "Trash Management"). */
@Composable
fun TrashScreen(
    onBack: () -> Unit,
    viewModel: TrashViewModel = hiltViewModel(),
) {
    val items by viewModel.items.collectAsState()
    var selectedIds by remember { mutableStateOf(emptySet<RecordingId>()) }
    var deleteTargets by remember { mutableStateOf<List<RecordingId>?>(null) }
    val currentItems = items.orEmpty()

    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = ScreenPadding, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().widthIn(max = ContentMaxWidth),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            TrashHeader(
                selectedIds = selectedIds,
                hasItems = currentItems.isNotEmpty(),
                onBack = onBack,
                onRestore = {
                    viewModel.onRestore(selectedIds.toList())
                    selectedIds = emptySet()
                },
                onDeleteSelected = { deleteTargets = selectedIds.toList() },
                onEmptyAll = { deleteTargets = currentItems.map { it.recording.id } },
            )
            if (currentItems.isEmpty()) {
                EmptyState(icon = Icons.Default.Delete, message = stringResource(R.string.trash_empty))
            } else {
                TrashGrid(
                    items = currentItems,
                    selectedIds = selectedIds,
                    onToggle = { id -> selectedIds = selectedIds.toggle(id) },
                )
            }
        }
    }

    deleteTargets?.let { targets ->
        PermanentDeleteDialog(
            count = targets.size,
            onConfirm = {
                viewModel.onPermanentlyDeleteConfirmed(targets)
                selectedIds = emptySet()
                deleteTargets = null
            },
            onDismiss = { deleteTargets = null },
        )
    }
}

private fun Set<RecordingId>.toggle(id: RecordingId): Set<RecordingId> = if (id in this) this - id else this + id

@Composable
private fun TrashHeader(
    selectedIds: Set<RecordingId>,
    hasItems: Boolean,
    onBack: () -> Unit,
    onRestore: () -> Unit,
    onDeleteSelected: () -> Unit,
    onEmptyAll: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            CircleIconButton(
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.navigate_back),
                onClick = onBack,
            )
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.trash_title), style = MaterialTheme.typography.headlineSmall)
                Text(
                    text = stringResource(R.string.trash_retention_notice),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        TrashActions(
            selectedIds = selectedIds,
            hasItems = hasItems,
            onRestore = onRestore,
            onDeleteSelected = onDeleteSelected,
            onEmptyAll = onEmptyAll,
        )
    }
}

@Composable
private fun TrashActions(
    selectedIds: Set<RecordingId>,
    hasItems: Boolean,
    onRestore: () -> Unit,
    onDeleteSelected: () -> Unit,
    onEmptyAll: () -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (selectedIds.isEmpty()) {
            SecondaryActionButton(
                text = stringResource(R.string.trash_empty_all),
                icon = Icons.Default.DeleteForever,
                enabled = hasItems,
                onClick = onEmptyAll,
            )
        } else {
            Text(
                text = stringResource(R.string.library_selected_count, selectedIds.size),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            SecondaryActionButton(
                text = stringResource(R.string.trash_restore),
                icon = Icons.Default.RestoreFromTrash,
                onClick = onRestore,
            )
            PrimaryActionButton(
                text = stringResource(R.string.trash_delete_forever),
                icon = Icons.Default.DeleteForever,
                onClick = onDeleteSelected,
            )
        }
    }
}

@Composable
private fun TrashGrid(
    items: List<TrashItem>,
    selectedIds: Set<RecordingId>,
    onToggle: (RecordingId) -> Unit,
) {
    BoxWithConstraints {
        val columns = (maxWidth / GRID_MIN_CELL).toInt().coerceIn(GRID_MIN_COLUMNS, GRID_MAX_COLUMNS)
        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            verticalArrangement = Arrangement.spacedBy(ItemSpacing),
            horizontalArrangement = Arrangement.spacedBy(ItemSpacing),
        ) {
            items(items, key = { it.recording.id.value }) { item ->
                TrashCard(
                    item = item,
                    selected = item.recording.id in selectedIds,
                    onClick = { onToggle(item.recording.id) },
                )
            }
        }
    }
}

/** 삭제된 항목 카드 — 썸네일은 흑백, 제목에는 취소선 (DESIGN_GUIDE.md 4절). */
@Composable
private fun TrashCard(
    item: TrashItem,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val recording = item.recording
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .alpha(if (selected) 1f else UNSELECTED_ALPHA)
                .clip(CardCorner)
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .border(
                    BorderStroke(
                        1.dp,
                        if (selected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.outlineVariant
                        },
                    ),
                    CardCorner,
                ).clickable(onClick = onClick),
    ) {
        Box(modifier = Modifier.fillMaxWidth().aspectRatio(GRID_ASPECT_RATIO)) {
            GrayscaleThumbnail(contentUri = recording.contentUri)
            Icon(
                imageVector =
                    if (selected) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                contentDescription = null,
                tint = if (selected) MaterialTheme.colorScheme.primary else Color.White,
                modifier = Modifier.align(Alignment.TopStart).padding(12.dp).size(24.dp),
            )
        }
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = recording.displayName,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textDecoration = TextDecoration.LineThrough,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = stringResource(R.string.trash_days_left, item.daysUntilDeletion),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

/** 휴지통 썸네일은 채도를 0으로 낮춰 활성 목록과 한눈에 구분한다. */
@Composable
private fun GrayscaleThumbnail(contentUri: String) {
    val context = LocalContext.current
    val grayscale = remember { ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) }) }
    AsyncImage(
        model =
            ImageRequest
                .Builder(context)
                .data(contentUri.toUri())
                .videoFrameMillis(THUMBNAIL_FRAME_MILLIS)
                .build(),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        colorFilter = grayscale,
        modifier =
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
    )
}

/** 영구 삭제 확인 (기능명세서 9절: 되돌릴 수 없음). */
@Composable
private fun PermanentDeleteDialog(
    count: Int,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.trash_delete_forever)) },
        text = { Text(stringResource(R.string.trash_delete_forever_message, count)) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(stringResource(R.string.trash_delete_forever)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.dialog_cancel)) }
        },
    )
}

private val GRID_MIN_CELL = 300.dp
private const val GRID_MIN_COLUMNS = 2
private const val GRID_MAX_COLUMNS = 4
private const val GRID_ASPECT_RATIO = 16f / 9f
private const val THUMBNAIL_FRAME_MILLIS = 1_000L
private const val UNSELECTED_ALPHA = 0.7f
