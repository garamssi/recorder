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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    var showOptionsSheet by remember { mutableStateOf(false) }

    Scaffold(
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
        Box(
            modifier =
                Modifier
                    .size(132.dp)
                    .shadow(16.dp, CircleShape)
                    .background(if (enabled) Color.White else Color.White.copy(alpha = 0.5f), CircleShape)
                    .clickable(enabled = enabled, onClick = onClick),
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
