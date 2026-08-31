package io.rami.screenrecorder.presentation.home

import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import io.rami.screenrecorder.core.designsystem.component.KineticSnackbarHost
import io.rami.screenrecorder.domain.model.PendingRecovery
import io.rami.screenrecorder.domain.model.Recording
import io.rami.screenrecorder.domain.model.RecordingState
import io.rami.screenrecorder.presentation.R
import io.rami.screenrecorder.presentation.library.RenameDialog

/** 화면 좌우 여백 (DESIGN_GUIDE.md 3절: 태블릿 그립 영역 확보). */
internal val ScreenPadding = 32.dp

/** 본문 최대 폭 — 넓은 태블릿에서 줄 길이가 지나치게 길어지지 않게 한다. */
internal val ContentMaxWidth = 1100.dp

/** 홈(녹화) 화면 (기능명세서 2절, DESIGN_GUIDE.md 5절). */
@Composable
fun HomeScreen(
    actions: HomeActions,
    onPlay: (Recording) -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    var showOptionsSheet by rememberSaveable { mutableStateOf(false) }
    val snackbarHost = remember { SnackbarHostState() }
    var renameTarget by remember { mutableStateOf<Recording?>(null) }

    ObserveSavedRecordings(viewModel, snackbarHost) { renameTarget = it }
    ObserveRecoveryFailures(viewModel, snackbarHost)

    Box(modifier = Modifier.fillMaxSize()) {
        HomeContent(
            uiState = uiState,
            actions = actions,
            onModeSelected = viewModel::onModeSelected,
            onOpenOptions = { showOptionsSheet = true },
            onPlay = onPlay,
        )
        KineticSnackbarHost(
            hostState = snackbarHost,
            modifier = Modifier.align(Alignment.BottomStart).padding(ScreenPadding),
        )
    }

    HomeOverlays(
        uiState = uiState,
        viewModel = viewModel,
        showOptionsSheet = showOptionsSheet,
        onDismissSheet = { showOptionsSheet = false },
    )

    renameTarget?.let { target ->
        RenameDialog(
            initialName = target.displayName,
            onConfirm = { newName -> viewModel.onRenameConfirmed(target.id, newName) },
            onDismiss = { renameTarget = null },
        )
    }

    // 크래시로 발행되지 못한 임시 파일을 한 건씩 복구/삭제 제안한다 (기능명세서 6.1절).
    val pendingRecoveries by viewModel.pendingRecoveries.collectAsState()
    val recoveringId by viewModel.recoveringId.collectAsState()
    pendingRecoveries.firstOrNull()?.let { recovery ->
        RecoveryDialog(
            recovery = recovery,
            isRecovering = recoveringId == recovery.id,
            onRecover = { viewModel.onRecoverConfirmed(recovery.id) },
            onDiscard = { viewModel.onDiscardRecovery(recovery.id) },
        )
    }
}

@Composable
private fun HomeContent(
    uiState: HomeUiState,
    actions: HomeActions,
    onModeSelected: (io.rami.screenrecorder.domain.model.CaptureModeKind) -> Unit,
    onOpenOptions: () -> Unit,
    onPlay: (Recording) -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = ScreenPadding, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().widthIn(max = ContentMaxWidth),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            HomeHeader(recordingState = uiState.recordingState)
            CaptureModeCards(selected = uiState.selectedMode, onSelected = onModeSelected)
            RecordControlCard(uiState = uiState, actions = actions)
            ActiveConfigurationCard(uiState = uiState, onOpenOptions = onOpenOptions)
            HomeFooterRow(uiState = uiState, actions = actions, onPlay = onPlay)
        }
    }
}

/** 저장 완료 스낵바 + 이름 변경 진입 (기능명세서 6.2절 [결정]). */
@Composable
private fun ObserveSavedRecordings(
    viewModel: HomeViewModel,
    snackbarHost: SnackbarHostState,
    onRenameRequested: (Recording) -> Unit,
) {
    val savedMessage = stringResource(R.string.saved_snackbar)
    val renameAction = stringResource(R.string.saved_snackbar_rename)
    LaunchedEffect(Unit) {
        viewModel.completedRecordings.collect { completed ->
            val result =
                snackbarHost.showSnackbar(
                    message = savedMessage,
                    actionLabel = renameAction,
                    duration = SnackbarDuration.Long,
                )
            if (result == SnackbarResult.ActionPerformed) onRenameRequested(completed)
        }
    }
}

/** 복구/삭제 실패 안내 (MediaStore 오류 등 — 앱이 해결 못 하는 외부 오류). */
@Composable
private fun ObserveRecoveryFailures(
    viewModel: HomeViewModel,
    snackbarHost: SnackbarHostState,
) {
    val recoveryFailedMessage = stringResource(R.string.recovery_failed)
    LaunchedEffect(Unit) {
        viewModel.recoveryFailed.collect { snackbarHost.showSnackbar(recoveryFailedMessage) }
    }
}

/** 옵션 시트와 카운트다운 오버레이 (기능명세서 2.1, 3절). */
@Composable
private fun HomeOverlays(
    uiState: HomeUiState,
    viewModel: HomeViewModel,
    showOptionsSheet: Boolean,
    onDismissSheet: () -> Unit,
) {
    if (showOptionsSheet) {
        RecordOptionsSheet(
            preset = uiState.preset,
            onPresetChanged = viewModel::onPresetChanged,
            onDismiss = onDismissSheet,
        )
    }
    val recordingState = uiState.recordingState
    // 오버레이 권한이 있으면 서비스가 시스템 오버레이로 카운트다운을 띄운다 (다른 앱 위에서도 보이게).
    // 그때는 앱 안에서 또 그리면 숫자가 겹치므로, 권한이 없을 때만 여기서 그린다 (기능명세서 3절).
    val systemOverlayShowsCountdown = Settings.canDrawOverlays(LocalContext.current)
    if (recordingState is RecordingState.CountingDown && !systemOverlayShowsCountdown) {
        CountdownOverlay(
            remainingSeconds = recordingState.remainingSeconds,
            onTap = viewModel::onCountdownTapped,
        )
    }
}
