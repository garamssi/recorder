package io.rami.screenrecorder.presentation.library

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import io.rami.screenrecorder.domain.model.Recording
import io.rami.screenrecorder.domain.model.RecordingId
import io.rami.screenrecorder.domain.model.SortOrder
import io.rami.screenrecorder.presentation.R

/** 녹화 목록 화면 (기능명세서 7절, DESIGN_GUIDE 1f/1h/1g). */
@Composable
fun LibraryScreen(
    onBack: () -> Unit,
    onPlay: (Recording) -> Unit,
    viewModel: LibraryViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHost = remember { SnackbarHostState() }
    var renameTarget by remember { mutableStateOf<Recording?>(null) }
    var detailTarget by remember { mutableStateOf<Recording?>(null) }
    var deleteSingleTarget by remember { mutableStateOf<RecordingId?>(null) }
    var showDeleteConfirm by rememberSaveable { mutableStateOf(false) }

    ObserveLibraryEvents(viewModel, snackbarHost)

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHost) },
        topBar = {
            LibraryTopBar(
                uiState = uiState,
                viewModel = viewModel,
                onBack = onBack,
                onDeleteClick = { showDeleteConfirm = true },
            )
        },
    ) { padding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 32.dp),
        ) {
            OutlinedTextField(
                value = uiState.query,
                onValueChange = viewModel::onQueryChanged,
                label = { Text(stringResource(R.string.library_search_hint)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            if (uiState.recordings.isEmpty() && !uiState.isLoading) {
                Text(
                    text = stringResource(R.string.home_recent_empty),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(top = 32.dp),
                )
            } else {
                LibraryContent(
                    uiState = uiState,
                    viewModel = viewModel,
                    onPlay = onPlay,
                    onRename = { renameTarget = it },
                    onDetail = { detailTarget = it },
                    onDelete = { deleteSingleTarget = it.id },
                )
            }
        }
    }

    renameTarget?.let { target ->
        RenameDialog(
            initialName = target.displayName,
            onConfirm = { newName -> viewModel.onRenameConfirmed(target.id, newName) },
            onDismiss = { renameTarget = null },
        )
    }
    detailTarget?.let { target ->
        DetailDialog(recording = target, onDismiss = { detailTarget = null })
    }
    if (showDeleteConfirm) {
        DeleteConfirmDialog(
            count = uiState.selectedIds.size,
            onConfirm = viewModel::onDeleteConfirmed,
            onDismiss = { showDeleteConfirm = false },
        )
    }
    deleteSingleTarget?.let { target ->
        DeleteConfirmDialog(
            count = 1,
            onConfirm = { viewModel.onDeleteSingleConfirmed(target) },
            onDismiss = { deleteSingleTarget = null },
        )
    }
}

@Composable
private fun ObserveLibraryEvents(
    viewModel: LibraryViewModel,
    snackbarHost: SnackbarHostState,
) {
    val invalidNameMessage = stringResource(R.string.rename_invalid)
    val failedMessage = stringResource(R.string.operation_failed)
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is LibraryEvent.RenameRejected -> snackbarHost.showSnackbar(invalidNameMessage)
                is LibraryEvent.OperationFailed -> snackbarHost.showSnackbar(failedMessage)
            }
        }
    }
}

/** 상단 바: 일반 모드(정렬/레이아웃) ↔ 선택 모드(선택 수/전체 선택/삭제) 전환 (기능명세서 7.3절). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LibraryTopBar(
    uiState: LibraryUiState,
    viewModel: LibraryViewModel,
    onBack: () -> Unit,
    onDeleteClick: () -> Unit,
) {
    if (uiState.isSelectionMode) {
        TopAppBar(
            title = { Text(stringResource(R.string.library_selected_count, uiState.selectedIds.size)) },
            navigationIcon = {
                IconButton(onClick = viewModel::onClearSelection) {
                    Icon(Icons.Default.Close, contentDescription = stringResource(R.string.dialog_cancel))
                }
            },
            actions = {
                IconButton(onClick = viewModel::onSelectAll) {
                    Icon(
                        Icons.Default.Menu,
                        contentDescription = stringResource(R.string.library_select_all),
                    )
                }
                IconButton(onClick = onDeleteClick) {
                    Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.menu_delete))
                }
            },
        )
    } else {
        TopAppBar(
            title = { Text(stringResource(R.string.library_title)) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.navigate_back),
                    )
                }
            },
            actions = {
                SortMenuAction(current = uiState.sortOrder, onSortChanged = viewModel::onSortChanged)
                IconButton(onClick = viewModel::onToggleLayout) {
                    Icon(
                        Icons.AutoMirrored.Filled.List,
                        contentDescription = stringResource(R.string.library_toggle_layout),
                    )
                }
            },
        )
    }
}

@Composable
private fun SortMenuAction(
    current: SortOrder,
    onSortChanged: (SortOrder) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    IconButton(onClick = { expanded = true }) {
        Icon(Icons.Default.Menu, contentDescription = stringResource(R.string.library_sort))
    }
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

@Composable
internal fun sortLabel(order: SortOrder): String =
    when (order) {
        SortOrder.NEWEST_FIRST -> stringResource(R.string.library_sort_newest)
        SortOrder.OLDEST_FIRST -> stringResource(R.string.library_sort_oldest)
        SortOrder.NAME -> stringResource(R.string.library_sort_name)
        SortOrder.LARGEST_FIRST -> stringResource(R.string.library_sort_size)
    }
