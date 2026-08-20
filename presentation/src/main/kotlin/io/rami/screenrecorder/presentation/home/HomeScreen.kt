package io.rami.screenrecorder.presentation.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import io.rami.screenrecorder.core.common.time.DurationFormatter
import io.rami.screenrecorder.core.designsystem.theme.HeroGradientDark
import io.rami.screenrecorder.core.designsystem.theme.HeroGradientLight
import io.rami.screenrecorder.core.designsystem.theme.RecRed
import io.rami.screenrecorder.domain.model.CaptureModeKind
import io.rami.screenrecorder.domain.model.RecordingState
import io.rami.screenrecorder.presentation.R

/** 홈 화면 (기능명세서 2절, DESIGN_GUIDE 2a/2d). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    actions: HomeActions,
    onPlay: (io.rami.screenrecorder.domain.model.Recording) -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    var showOptionsSheet by androidx.compose.runtime.saveable
        .rememberSaveable { mutableStateOf(false) }
    val snackbarHost = remember { androidx.compose.material3.SnackbarHostState() }
    var renameTarget by remember {
        mutableStateOf<io.rami.screenrecorder.domain.model.Recording?>(null)
    }

    // 저장 직후 스낵바 + 이름 변경 진입 (기능명세서 6.2절 [결정]).
    val savedMessage = stringResource(R.string.saved_snackbar)
    val renameAction = stringResource(R.string.saved_snackbar_rename)
    androidx.compose.runtime.LaunchedEffect(Unit) {
        viewModel.completedRecordings.collect { completed ->
            val result =
                snackbarHost.showSnackbar(
                    message = savedMessage,
                    actionLabel = renameAction,
                    duration = androidx.compose.material3.SnackbarDuration.Long,
                )
            if (result == androidx.compose.material3.SnackbarResult.ActionPerformed) {
                renameTarget = completed
            }
        }
    }

    Scaffold(
        snackbarHost = { androidx.compose.material3.SnackbarHost(snackbarHost) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.home_app_title)) },
                actions = {
                    IconButton(onClick = actions.onOpenTrash) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = stringResource(R.string.home_open_trash),
                        )
                    }
                    IconButton(onClick = actions.onOpenSettings) {
                        Icon(
                            Icons.Default.Settings,
                            contentDescription = stringResource(R.string.home_open_settings),
                        )
                    }
                },
            )
        },
    ) { padding ->
        Row(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 32.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            HeroPanel(
                uiState = uiState,
                actions = actions,
                onModeSelected = viewModel::onModeSelected,
                onPresetClick = { showOptionsSheet = true },
                modifier = Modifier.weight(HERO_WEIGHT),
            )
            SidePanel(
                uiState = uiState,
                onOpenLibrary = actions.onOpenLibrary,
                onPlay = onPlay,
                modifier = Modifier.weight(SIDE_WEIGHT),
            )
        }
    }

    HomeOverlays(
        uiState = uiState,
        viewModel = viewModel,
        showOptionsSheet = showOptionsSheet,
        onDismissSheet = { showOptionsSheet = false },
    )

    renameTarget?.let { target ->
        io.rami.screenrecorder.presentation.library.RenameDialog(
            initialName = target.displayName,
            onConfirm = { newName -> viewModel.onRenameConfirmed(target.id, newName) },
            onDismiss = { renameTarget = null },
        )
    }

    // 복구/삭제 실패 안내 (MediaStore 오류 등 — 앱이 해결 못 하는 외부 오류).
    val recoveryFailedMessage = stringResource(R.string.recovery_failed)
    androidx.compose.runtime.LaunchedEffect(Unit) {
        viewModel.recoveryFailed.collect { snackbarHost.showSnackbar(recoveryFailedMessage) }
    }

    // 크래시로 발행되지 못한 임시 파일을 한 건씩 복구/삭제 제안한다 (기능명세서 6.1절).
    val pendingRecoveries by viewModel.pendingRecoveries.collectAsState()
    pendingRecoveries.firstOrNull()?.let { recovery ->
        RecoveryDialog(
            recovery = recovery,
            onRecover = { viewModel.onRecoverConfirmed(recovery.id) },
            onDiscard = { viewModel.onDiscardRecovery(recovery.id) },
        )
    }
}

/** 크래시 복구 다이얼로그 (기능명세서 6.1절: 복구/삭제 제안). */
@Composable
private fun RecoveryDialog(
    recovery: io.rami.screenrecorder.domain.model.PendingRecovery,
    onRecover: () -> Unit,
    onDiscard: () -> Unit,
) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = { /* 명시적 선택을 강제한다 — 임의 dismiss로 파일이 방치되지 않게 */ },
        title = { Text(stringResource(R.string.recovery_title)) },
        text = {
            Text(
                stringResource(
                    R.string.recovery_message,
                    formatRecoverySize(recovery.sizeBytes),
                ),
            )
        },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = onRecover) {
                Text(stringResource(R.string.recovery_restore))
            }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onDiscard) {
                Text(stringResource(R.string.recovery_delete))
            }
        },
    )
}

/** 1MB 미만은 KB로 표시해 작은 잔여 파일도 크기를 알아볼 수 있게 한다 (n2). */
private fun formatRecoverySize(bytes: Long): String =
    if (bytes < BYTES_PER_MB) {
        "%.0fKB".format(bytes / BYTES_PER_KB)
    } else {
        "%.1fMB".format(bytes / BYTES_PER_MB)
    }

private const val BYTES_PER_KB = 1_000f
private const val BYTES_PER_MB = 1_000_000f

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
    if (recordingState is RecordingState.CountingDown) {
        CountdownOverlay(
            remainingSeconds = recordingState.remainingSeconds,
            onTap = viewModel::onCountdownTapped,
        )
    }
}

/** 좌측 히어로 패널: 그라디언트, 모드 세그먼트, 녹화 버튼, 프리셋 칩 (DESIGN_GUIDE 2a). */
@Composable
private fun HeroPanel(
    uiState: HomeUiState,
    actions: HomeActions,
    onModeSelected: (CaptureModeKind) -> Unit,
    onPresetClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val gradient = if (isSystemInDarkTheme()) HeroGradientDark else HeroGradientLight
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .background(Brush.linearGradient(gradient), RoundedCornerShape(28.dp))
                .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceEvenly,
    ) {
        ModeSegments(selected = uiState.selectedMode, onSelected = onModeSelected)
        when (val state = uiState.recordingState) {
            is RecordingState.Recording ->
                RecordingStatusPanel(
                    statusText = stringResource(R.string.home_recording_in_progress),
                    elapsed = DurationFormatter.formatElapsed(state.elapsed),
                    isPaused = false,
                    actions = actions,
                )

            is RecordingState.Paused ->
                RecordingStatusPanel(
                    statusText = stringResource(R.string.home_paused),
                    elapsed = DurationFormatter.formatElapsed(state.elapsed),
                    isPaused = true,
                    actions = actions,
                )

            is RecordingState.Stopping ->
                Text(
                    text = stringResource(R.string.home_saving),
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                )

            else ->
                RecordButton(
                    enabled = uiState.canStartRecording,
                    onClick = actions.control.onStart,
                )
        }
        PresetChip(uiState = uiState, onClick = onPresetClick)
    }
}

/** 모드 세그먼트 3개 (DESIGN_GUIDE: pill 컨테이너 흰 12%, 선택 = 흰 배경). */
@Composable
private fun ModeSegments(
    selected: CaptureModeKind,
    onSelected: (CaptureModeKind) -> Unit,
) {
    Row(
        modifier =
            Modifier
                .background(Color.White.copy(alpha = SEGMENT_CONTAINER_ALPHA), CircleShape)
                .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        CaptureModeKind.entries.forEach { mode ->
            val isSelected = mode == selected
            Text(
                text = modeLabel(mode),
                color = if (isSelected) HeroGradientLight.first() else Color.White,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                style = MaterialTheme.typography.bodyMedium,
                modifier =
                    Modifier
                        .background(
                            if (isSelected) Color.White else Color.Transparent,
                            CircleShape,
                        ).clickable { onSelected(mode) }
                        .heightIn(min = 48.dp)
                        .wrapContentHeight()
                        .padding(horizontal = 20.dp, vertical = 10.dp),
            )
        }
    }
}

@Composable
private fun modeLabel(mode: CaptureModeKind): String =
    when (mode) {
        CaptureModeKind.FULL_SCREEN -> stringResource(R.string.home_mode_full_screen)
        CaptureModeKind.SINGLE_APP -> stringResource(R.string.home_mode_single_app)
        CaptureModeKind.REGION -> stringResource(R.string.home_mode_region)
    }

/** 녹화 시작 버튼: 흰 원 132dp + 빨간 원 52dp (DESIGN_GUIDE 4절). */
@Composable
private fun RecordButton(
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        val startLabel = stringResource(R.string.home_start_recording)
        Box(
            modifier =
                Modifier
                    .size(132.dp)
                    .shadow(16.dp, CircleShape)
                    .background(
                        if (enabled) Color.White else Color.White.copy(alpha = DISABLED_BUTTON_ALPHA),
                        CircleShape,
                    ).clickable(enabled = enabled, onClick = onClick)
                    .semantics { contentDescription = startLabel },
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier =
                    Modifier
                        .size(52.dp)
                        .background(RecRed, CircleShape),
            )
        }
        if (!enabled) {
            Spacer(Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.home_storage_low_warning),
                color = Color.White,
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

/** 녹화 중 상태 패널 (기능명세서 2.2절: 경과 시간 + 제어). */
@Composable
private fun RecordingStatusPanel(
    statusText: String,
    elapsed: String,
    isPaused: Boolean,
    actions: HomeActions,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = statusText, color = Color.White, style = MaterialTheme.typography.titleMedium)
        Text(
            text = elapsed,
            color = Color.White,
            fontSize = 56.sp,
            fontWeight = FontWeight.Bold,
            style =
                MaterialTheme.typography.displaySmall.copy(
                    fontFeatureSettings = TABULAR_NUMBERS,
                ),
        )
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(
                onClick = if (isPaused) actions.control.onResume else actions.control.onPause,
            ) {
                Text(
                    text =
                        stringResource(
                            if (isPaused) R.string.home_resume else R.string.home_pause,
                        ),
                    color = Color.White,
                )
            }
            Button(
                onClick = actions.control.onStop,
                colors =
                    androidx.compose.material3.ButtonDefaults
                        .buttonColors(containerColor = RecRed),
            ) {
                Text(stringResource(R.string.home_stop))
            }
        }
    }
}

/** 프리셋 요약 칩 — 탭 시 녹화 옵션 시트 (기능명세서 2.1절). */
@Composable
private fun PresetChip(
    uiState: HomeUiState,
    onClick: () -> Unit,
) {
    Text(
        text = presetSummary(uiState.preset),
        color = Color.White,
        style = MaterialTheme.typography.bodyMedium,
        modifier =
            Modifier
                .background(Color.White.copy(alpha = CHIP_CONTAINER_ALPHA), CircleShape)
                .clickable(onClick = onClick)
                .padding(horizontal = 20.dp, vertical = 10.dp),
    )
}

private const val HERO_WEIGHT = 1.2f
private const val SIDE_WEIGHT = 0.8f
private const val SEGMENT_CONTAINER_ALPHA = 0.12f
private const val CHIP_CONTAINER_ALPHA = 0.14f
private const val DISABLED_BUTTON_ALPHA = 0.5f
private const val TABULAR_NUMBERS = "tnum"
