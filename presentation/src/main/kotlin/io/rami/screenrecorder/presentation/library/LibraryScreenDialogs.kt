package io.rami.screenrecorder.presentation.library

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import io.rami.screenrecorder.presentation.R

// 목록 화면의 다이얼로그 상태와 이벤트 구독. 화면 골격은 LibraryScreen.kt 참조.

@Composable
internal fun rememberLibraryDialogState(): LibraryDialogState = remember { LibraryDialogState() }

@Composable
internal fun LibraryDialogHost(
    state: LibraryDialogState,
    uiState: LibraryUiState,
    viewModel: LibraryViewModel,
    compressViewModel: CompressViewModel,
    compressState: CompressUiState,
) {
    state.renameTarget?.let { target ->
        RenameDialog(
            initialName = target.displayName,
            onConfirm = { newName -> viewModel.onRenameConfirmed(target.id, newName) },
            onDismiss = { state.renameTarget = null },
        )
    }
    state.detailTarget?.let { target ->
        DetailDialog(recording = target, onDismiss = { state.detailTarget = null })
    }
    if (state.showDeleteConfirm) {
        DeleteConfirmDialog(
            count = uiState.selectedIds.size,
            onConfirm = viewModel::onDeleteConfirmed,
            onDismiss = { state.showDeleteConfirm = false },
        )
    }
    state.deleteSingleTarget?.let { target ->
        DeleteConfirmDialog(
            count = 1,
            onConfirm = { viewModel.onDeleteSingleConfirmed(target) },
            onDismiss = { state.deleteSingleTarget = null },
        )
    }
    state.duplicateSuggestion?.let { suggestion ->
        DuplicateNameDialog(
            suggestedName = suggestion.suggestedName,
            onConfirm = { viewModel.onRenameConfirmed(suggestion.id, suggestion.suggestedName) },
            onDismiss = { state.duplicateSuggestion = null },
        )
    }
    state.compressTarget?.let { target ->
        CompressDialog(
            onConfirm = { preset -> compressViewModel.onCompressConfirmed(target.id, preset) },
            onDismiss = { state.compressTarget = null },
        )
    }
    compressState.trashPromptFor?.let { originalId ->
        TrashOriginalDialog(
            onConfirm = { compressViewModel.onTrashOriginalConfirmed(originalId) },
            onDismiss = compressViewModel::onTrashPromptDismissed,
        )
    }
}

@Composable
internal fun ObserveCompressEvents(
    viewModel: CompressViewModel,
    snackbarHost: SnackbarHostState,
) {
    val blockedMessage = stringResource(R.string.compress_blocked_recording)
    val busyMessage = stringResource(R.string.compress_busy)
    val failedMessage = stringResource(R.string.operation_failed)
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is CompressEvent.BlockedByRecording -> snackbarHost.showSnackbar(blockedMessage)
                is CompressEvent.Busy -> snackbarHost.showSnackbar(busyMessage)
                is CompressEvent.Failed -> snackbarHost.showSnackbar(failedMessage)
            }
        }
    }
}

@Composable
internal fun ObserveLibraryEvents(
    viewModel: LibraryViewModel,
    snackbarHost: SnackbarHostState,
    onDuplicate: (LibraryEvent.RenameNeedsSuffix) -> Unit,
) {
    val invalidNameMessage = stringResource(R.string.rename_invalid)
    val failedMessage = stringResource(R.string.operation_failed)
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is LibraryEvent.RenameRejected -> snackbarHost.showSnackbar(invalidNameMessage)
                is LibraryEvent.OperationFailed -> snackbarHost.showSnackbar(failedMessage)
                is LibraryEvent.RenameNeedsSuffix -> onDuplicate(event)
            }
        }
    }
}
