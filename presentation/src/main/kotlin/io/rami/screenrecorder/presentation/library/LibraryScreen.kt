package io.rami.screenrecorder.presentation.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import io.rami.screenrecorder.core.designsystem.component.CircleIconButton
import io.rami.screenrecorder.core.designsystem.component.EmptyState
import io.rami.screenrecorder.core.designsystem.component.KineticSnackbarHost
import io.rami.screenrecorder.core.designsystem.component.PrimaryActionButton
import io.rami.screenrecorder.core.designsystem.component.SecondaryActionButton
import io.rami.screenrecorder.core.designsystem.theme.ControlCorner
import io.rami.screenrecorder.domain.model.Recording
import io.rami.screenrecorder.domain.model.SortOrder
import io.rami.screenrecorder.presentation.R
import io.rami.screenrecorder.presentation.home.ContentMaxWidth
import io.rami.screenrecorder.presentation.home.ScreenPadding

/** 녹화 목록 화면 (기능명세서 7절, DESIGN_GUIDE.md 4절 "Library"). */
@Composable
fun LibraryScreen(
    onOpenTrash: () -> Unit,
    onPlay: (Recording) -> Unit,
    viewModel: LibraryViewModel = hiltViewModel(),
    compressViewModel: CompressViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val compressState by compressViewModel.uiState.collectAsState()
    val snackbarHost = remember { SnackbarHostState() }
    val dialogs = rememberLibraryDialogState()

    ObserveLibraryEvents(viewModel, snackbarHost) { dialogs.duplicateSuggestion = it }
    ObserveCompressEvents(compressViewModel, snackbarHost)

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = ScreenPadding, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().widthIn(max = ContentMaxWidth),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                LibraryHeader(
                    uiState = uiState,
                    viewModel = viewModel,
                    onOpenTrash = onOpenTrash,
                    onDeleteClick = { dialogs.showDeleteConfirm = true },
                )
                LibraryToolbar(uiState = uiState, viewModel = viewModel)
                compressState.runningJob?.let { job ->
                    CompressProgressBar(job = job, onCancel = compressViewModel::onCancelTranscode)
                }
                LibraryBody(
                    uiState = uiState,
                    viewModel = viewModel,
                    onPlay = onPlay,
                    dialogs = dialogs,
                )
            }
        }
        KineticSnackbarHost(
            hostState = snackbarHost,
            modifier = Modifier.align(Alignment.BottomStart).padding(ScreenPadding),
        )
    }

    LibraryDialogHost(
        state = dialogs,
        uiState = uiState,
        viewModel = viewModel,
        compressViewModel = compressViewModel,
        compressState = compressState,
    )
}

@Composable
private fun LibraryBody(
    uiState: LibraryUiState,
    viewModel: LibraryViewModel,
    onPlay: (Recording) -> Unit,
    dialogs: LibraryDialogState,
) {
    if (uiState.recordings.isEmpty() && !uiState.isLoading) {
        EmptyState(
            icon = Icons.Default.Videocam,
            message =
                stringResource(
                    if (uiState.query.isBlank()) R.string.home_recent_empty else R.string.library_no_results,
                ),
        )
        return
    }
    LibraryContent(
        uiState = uiState,
        viewModel = viewModel,
        onPlay = onPlay,
        onRename = { dialogs.renameTarget = it },
        onDetail = { dialogs.detailTarget = it },
        onDelete = { dialogs.deleteSingleTarget = it.id },
        onCompress = { dialogs.compressTarget = it },
    )
}

/** 제목 + 선택 모드 액션 (기능명세서 7.3절). */
@Composable
private fun LibraryHeader(
    uiState: LibraryUiState,
    viewModel: LibraryViewModel,
    onOpenTrash: () -> Unit,
    onDeleteClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.library_title), style = MaterialTheme.typography.headlineLarge)
            Text(
                text = stringResource(R.string.library_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (uiState.isSelectionMode) {
            SelectionActions(uiState = uiState, viewModel = viewModel, onDeleteClick = onDeleteClick)
        } else {
            BrowseActions(uiState = uiState, viewModel = viewModel, onOpenTrash = onOpenTrash)
        }
    }
}

@Composable
private fun SelectionActions(
    uiState: LibraryUiState,
    viewModel: LibraryViewModel,
    onDeleteClick: () -> Unit,
) {
    val context = LocalContext.current
    val hasSelection = uiState.selectedIds.isNotEmpty()
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.library_selected_count, uiState.selectedIds.size),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SecondaryActionButton(
            text =
                stringResource(
                    if (uiState.isAllSelected) R.string.library_deselect_all else R.string.library_select_all,
                ),
            onClick = viewModel::onToggleSelectAll,
        )
        CircleIconButton(
            icon = Icons.Default.Share,
            contentDescription = stringResource(R.string.menu_share),
            enabled = hasSelection,
            onClick = {
                shareRecordings(context, uiState.recordings.filter { it.id in uiState.selectedIds })
            },
        )
        PrimaryActionButton(
            text = stringResource(R.string.menu_delete),
            icon = Icons.Default.Delete,
            enabled = hasSelection,
            onClick = onDeleteClick,
        )
        CircleIconButton(
            icon = Icons.Default.Close,
            contentDescription = stringResource(R.string.dialog_cancel),
            onClick = viewModel::onClearSelection,
        )
    }
}

@Composable
private fun BrowseActions(
    uiState: LibraryUiState,
    viewModel: LibraryViewModel,
    onOpenTrash: () -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SecondaryActionButton(
            text = stringResource(R.string.library_select_items),
            icon = Icons.Default.Check,
            enabled = uiState.recordings.isNotEmpty(),
            onClick = viewModel::onEnterSelectionMode,
        )
        CircleIconButton(
            icon = Icons.Default.Delete,
            contentDescription = stringResource(R.string.trash_title),
            onClick = onOpenTrash,
        )
    }
}

/** 검색어 + 정렬 + 레이아웃 전환 (기능명세서 7.1절). */
@Composable
private fun LibraryToolbar(
    uiState: LibraryUiState,
    viewModel: LibraryViewModel,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = uiState.query,
            onValueChange = viewModel::onQueryChanged,
            placeholder = { Text(stringResource(R.string.library_search_hint)) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            singleLine = true,
            shape = ControlCorner,
            modifier = Modifier.weight(1f),
        )
        SortMenuAction(current = uiState.sortOrder, onSortChanged = viewModel::onSortChanged)
        CircleIconButton(
            icon = if (uiState.isGrid) Icons.AutoMirrored.Filled.ViewList else Icons.Default.GridView,
            contentDescription = stringResource(R.string.library_toggle_layout),
            onClick = viewModel::onToggleLayout,
        )
    }
}

@Composable
private fun SortMenuAction(
    current: SortOrder,
    onSortChanged: (SortOrder) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        CircleIconButton(
            icon = Icons.AutoMirrored.Filled.Sort,
            contentDescription = stringResource(R.string.library_sort),
            onClick = { expanded = true },
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            SortOrder.entries.forEach { order ->
                DropdownMenuItem(
                    text = { Text(sortLabel(order) + if (order == current) " ✓" else "") },
                    onClick = {
                        onSortChanged(order)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
internal fun sortLabel(order: SortOrder): String =
    when (order) {
        SortOrder.NEWEST_FIRST -> stringResource(R.string.library_sort_newest)
        SortOrder.OLDEST_FIRST -> stringResource(R.string.library_sort_oldest)
        SortOrder.NAME -> stringResource(R.string.library_sort_name)
        SortOrder.LARGEST_FIRST -> stringResource(R.string.library_sort_size)
    }

/** 선택 모드에서 항목을 흐리게 하는 불투명도 (DESIGN_GUIDE.md 4절). */
internal const val UNSELECTED_ALPHA = 0.5f

/** 목록 격자/리스트의 항목 간격. */
internal val ItemSpacing = 16.dp
