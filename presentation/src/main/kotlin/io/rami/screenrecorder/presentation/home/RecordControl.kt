package io.rami.screenrecorder.presentation.home

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.rami.screenrecorder.core.common.time.DurationFormatter
import io.rami.screenrecorder.core.designsystem.component.KineticCard
import io.rami.screenrecorder.core.designsystem.component.PrimaryActionButton
import io.rami.screenrecorder.core.designsystem.component.SecondaryActionButton
import io.rami.screenrecorder.core.designsystem.component.rememberPressScale
import io.rami.screenrecorder.core.designsystem.theme.tabularNumbers
import io.rami.screenrecorder.domain.model.RecordingState
import io.rami.screenrecorder.presentation.R

/** 녹화 제어 카드 — 유휴에서는 큰 녹화 버튼, 진행 중에는 경과 시간과 제어 버튼 (기능명세서 2.2절). */
@Composable
internal fun RecordControlCard(
    uiState: HomeUiState,
    actions: HomeActions,
) {
    KineticCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            when (val state = uiState.recordingState) {
                is RecordingState.Recording ->
                    RecordingStatus(
                        statusRes = R.string.home_recording_in_progress,
                        elapsed = state.elapsed,
                        isPaused = false,
                        actions = actions,
                    )

                is RecordingState.Paused ->
                    RecordingStatus(
                        statusRes = R.string.home_paused,
                        elapsed = state.elapsed,
                        isPaused = true,
                        actions = actions,
                    )

                is RecordingState.Stopping ->
                    Text(
                        text = stringResource(R.string.home_saving),
                        style = MaterialTheme.typography.titleMedium,
                    )

                else ->
                    IdleRecordButton(
                        enabled = uiState.canStartRecording,
                        onClick = actions.control.onStart,
                    )
            }
        }
    }
}

/** 대기 상태의 녹화 시작 버튼 (DESIGN_GUIDE.md 4절: primary 원형 + 링). */
@Composable
private fun IdleRecordButton(
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val press = rememberPressScale()
    val startLabel = stringResource(R.string.home_start_recording)
    Box(
        modifier =
            press
                .applyTo(Modifier)
                .size(RECORD_RING)
                .clip(CircleShape)
                .border(RING_WIDTH, MaterialTheme.colorScheme.primary.copy(alpha = RING_ALPHA), CircleShape)
                .clickable(
                    enabled = enabled,
                    interactionSource = press.interactionSource,
                    indication = null,
                    onClick = onClick,
                ).semantics { contentDescription = startLabel },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier =
                Modifier
                    .size(RECORD_BUTTON)
                    .shadow(20.dp, CircleShape)
                    .clip(CircleShape)
                    .background(
                        MaterialTheme.colorScheme.primary
                            .copy(alpha = if (enabled) 1f else DISABLED_ALPHA),
                    ),
        )
    }
    Text(text = startLabel, style = MaterialTheme.typography.titleMedium)
    if (!enabled) {
        Text(
            text = stringResource(R.string.home_storage_low_warning),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center,
        )
    }
}

/** 녹화/일시정지 중 상태 표시 (기능명세서 2.2절: 경과 시간 + 제어). */
@Composable
private fun RecordingStatus(
    statusRes: Int,
    elapsed: kotlin.time.Duration,
    isPaused: Boolean,
    actions: HomeActions,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        RecordingPulseDot(animated = !isPaused)
        Text(
            text = stringResource(statusRes),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
    }
    Text(
        text = DurationFormatter.formatElapsed(elapsed),
        style = MaterialTheme.typography.displayMedium.tabularNumbers(),
        fontSize = ELAPSED_SIZE,
    )
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        SecondaryActionButton(
            text = stringResource(if (isPaused) R.string.home_resume else R.string.home_pause),
            icon = if (isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
            onClick = if (isPaused) actions.control.onResume else actions.control.onPause,
        )
        PrimaryActionButton(
            text = stringResource(R.string.home_stop),
            icon = Icons.Default.Stop,
            onClick = actions.control.onStop,
        )
    }
}

/** 녹화 중임을 알리는 맥동하는 REC 점. 일시정지 중에는 정지한다. */
@Composable
private fun RecordingPulseDot(animated: Boolean) {
    val transition = rememberInfiniteTransition(label = "recPulse")
    val pulse by transition.animateFloat(
        initialValue = 1f,
        targetValue = PULSE_MIN_ALPHA,
        animationSpec =
            infiniteRepeatable(tween(PULSE_MILLIS), repeatMode = RepeatMode.Reverse),
        label = "recPulseAlpha",
    )
    Box(
        modifier =
            Modifier
                .size(PULSE_DOT)
                .alpha(if (animated) pulse else DISABLED_ALPHA)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary),
    )
}

/** 카운트다운 오버레이 (기능명세서 3절: 탭 = 즉시 시작). */
@Composable
internal fun CountdownOverlay(
    remainingSeconds: Int,
    onTap: () -> Unit,
) {
    // 뒤로가기도 탭과 동일하게 스킵 처리한다 (화면 이탈로 좀비 세션이 생기는 것을 방지).
    androidx.activity.compose.BackHandler(onBack = onTap)
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = COUNTDOWN_DIM_ALPHA))
                .clickable(onClick = onTap),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = remainingSeconds.toString(),
                color = Color.White,
                fontSize = COUNTDOWN_SIZE,
                // 한 자리 숫자에 고정폭(tnum)을 쓰면 좌우 여백이 남아 비어 보인다.
                style = MaterialTheme.typography.displayLarge,
                textAlign = TextAlign.Center,
            )
            Text(
                text = stringResource(R.string.home_countdown_skip_hint),
                color = Color.White,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

private val RECORD_RING = 160.dp
private val RECORD_BUTTON = 112.dp
private val RING_WIDTH = 2.dp
private val PULSE_DOT = 10.dp
private val ELAPSED_SIZE = 56.sp
private val COUNTDOWN_SIZE = 120.sp
private const val RING_ALPHA = 0.3f
private const val DISABLED_ALPHA = 0.5f
private const val PULSE_MIN_ALPHA = 0.25f
private const val PULSE_MILLIS = 900
private const val COUNTDOWN_DIM_ALPHA = 0.72f
