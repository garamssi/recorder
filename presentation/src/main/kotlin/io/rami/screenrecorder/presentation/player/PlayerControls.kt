package io.rami.screenrecorder.presentation.player

import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.rami.screenrecorder.core.common.time.DurationFormatter
import io.rami.screenrecorder.core.designsystem.component.rememberPressScale
import io.rami.screenrecorder.core.designsystem.theme.tabularNumbers
import io.rami.screenrecorder.domain.model.MediaVolume
import io.rami.screenrecorder.domain.model.Recording
import io.rami.screenrecorder.presentation.R
import kotlin.time.Duration.Companion.milliseconds

/**
 * 화면 전체를 덮는 재생 컨트롤 (DESIGN_GUIDE.md 4절 "Video Player UI").
 *
 * 재생 중에는 3초 뒤 부드럽게 사라지고, 화면을 탭하면 즉시 다시 나타난다.
 */
@Composable
internal fun PlayerControlsOverlay(
    recording: Recording,
    playback: PlaybackState,
    playbackSpeed: Float,
    fillScreen: Boolean,
    volume: MediaVolume,
    callbacks: PlayerCallbacks,
) {
    var visible by remember { mutableStateOf(true) }
    // 사용자가 조작할 때마다 갱신되는 값 — 자동 숨김 타이머를 처음부터 다시 돌리는 신호다.
    var interactionMarker by remember { mutableStateOf(0) }

    LaunchedEffect(visible, playback.isPlaying, interactionMarker) {
        if (visible && playback.isPlaying) {
            kotlinx.coroutines.delay(CONTROLS_HIDE_DELAY_MILLIS)
            visible = false
        }
    }

    fun show() {
        visible = true
        interactionMarker++
    }
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .clickable(indication = null, interactionSource = null) {
                    if (visible) visible = false else show()
                },
    ) {
        val alpha by androidx.compose.animation.core.animateFloatAsState(
            targetValue = if (visible) 1f else 0f,
            animationSpec = tween(CONTROLS_FADE_MILLIS),
            label = "controlsAlpha",
        )
        if (alpha > 0f) {
            ControlsLayer(
                recording = recording,
                playback = playback,
                playbackSpeed = playbackSpeed,
                fillScreen = fillScreen,
                volume = volume,
                callbacks = callbacks,
                alpha = alpha,
                onInteraction = ::show,
            )
        }
    }
}

@Composable
private fun ControlsLayer(
    recording: Recording,
    playback: PlaybackState,
    playbackSpeed: Float,
    fillScreen: Boolean,
    volume: MediaVolume,
    callbacks: PlayerCallbacks,
    alpha: Float,
    onInteraction: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize().alpha(alpha)) {
        PlayerTopBar(
            recording = recording,
            callbacks = callbacks,
            onInteraction = onInteraction,
            modifier = Modifier.align(Alignment.TopCenter),
        )
        CenterControls(
            playback = playback,
            callbacks = callbacks,
            onInteraction = onInteraction,
            modifier = Modifier.align(Alignment.Center),
        )
        PlayerBottomBar(
            playback = playback,
            playbackSpeed = playbackSpeed,
            fillScreen = fillScreen,
            volume = volume,
            callbacks = callbacks,
            onInteraction = onInteraction,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

/** 상단: 뒤로가기 + 제목 + 더보기 (검정 그라디언트 위). */
@Composable
private fun PlayerTopBar(
    recording: Recording,
    callbacks: PlayerCallbacks,
    onInteraction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color(0xCC000000), Color.Transparent)))
                .padding(horizontal = 24.dp, vertical = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        GlassIconButton(
            icon = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = stringResource(R.string.navigate_back),
            onClick = {
                onInteraction()
                callbacks.onBack()
            },
        )
        Text(
            text = recording.displayName,
            style = MaterialTheme.typography.titleMedium,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        PlayerOverflowMenu(recording = recording, callbacks = callbacks, onInteraction = onInteraction)
    }
}

/** 중앙: ±10초 + 재생/일시정지 (DESIGN_GUIDE.md 4절: 큰 primary 원형). */
@Composable
private fun CenterControls(
    playback: PlaybackState,
    callbacks: PlayerCallbacks,
    onInteraction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(40.dp),
    ) {
        GlassIconButton(
            icon = Icons.Default.Replay10,
            contentDescription = stringResource(R.string.player_seek_back),
            size = SEEK_BUTTON,
            onClick = {
                onInteraction()
                callbacks.onSeekBack()
            },
        )
        PlayPauseButton(
            playback = playback,
            onClick = {
                onInteraction()
                callbacks.onPlayPause()
            },
        )
        GlassIconButton(
            icon = Icons.Default.Forward10,
            contentDescription = stringResource(R.string.player_seek_forward),
            size = SEEK_BUTTON,
            onClick = {
                onInteraction()
                callbacks.onSeekForward()
            },
        )
    }
}

@Composable
private fun PlayPauseButton(
    playback: PlaybackState,
    onClick: () -> Unit,
) {
    val press = rememberPressScale()
    Box(
        modifier =
            press
                .applyTo(Modifier)
                .size(PLAY_BUTTON)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary)
                .clickable(
                    interactionSource = press.interactionSource,
                    indication = null,
                    onClick = onClick,
                ),
        contentAlignment = Alignment.Center,
    ) {
        // 끝까지 본 뒤에는 "처음부터 다시 재생"임을 아이콘으로 알린다.
        val icon =
            when {
                playback.hasEnded -> Icons.Default.Replay
                playback.isPlaying -> Icons.Default.Pause
                else -> Icons.Default.PlayArrow
            }
        val label =
            when {
                playback.hasEnded -> R.string.player_replay
                playback.isPlaying -> R.string.player_pause
                else -> R.string.player_play
            }
        Icon(
            imageVector = icon,
            contentDescription = stringResource(label),
            tint = Color.White,
            modifier = Modifier.size(PLAY_ICON),
        )
    }
}

/** 하단: 진행바 + 시간 + 배속 + 전체화면. */
@Composable
private fun PlayerBottomBar(
    playback: PlaybackState,
    playbackSpeed: Float,
    fillScreen: Boolean,
    volume: MediaVolume,
    callbacks: PlayerCallbacks,
    onInteraction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xE6000000))))
                .padding(horizontal = 24.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            TimeLabel(millis = playback.positionMs, highlighted = true)
            Slider(
                value = playback.progress,
                onValueChange = { fraction ->
                    onInteraction()
                    callbacks.onSeekTo((fraction * playback.durationMs).toLong())
                },
                colors =
                    SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary,
                        inactiveTrackColor = Color.White.copy(alpha = TRACK_ALPHA),
                    ),
                modifier = Modifier.weight(1f),
            )
            TimeLabel(millis = playback.durationMs, highlighted = false)
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            VolumeControl(
                volume = volume,
                onVolumeChange = {
                    onInteraction()
                    callbacks.onVolumeChange(it)
                },
                onToggleMute = {
                    onInteraction()
                    callbacks.onToggleMute()
                },
            )
            Row(
                // 배속 pill과 화면 채우기 버튼이 붙어 보이지 않게 넉넉히 띄운다.
                horizontalArrangement = Arrangement.spacedBy(24.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SpeedSelector(
                    playbackSpeed = playbackSpeed,
                    onSpeedSelected = {
                        onInteraction()
                        callbacks.onSpeedSelected(it)
                    },
                )
                GlassIconButton(
                    icon = if (fillScreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                    contentDescription =
                        stringResource(
                            if (fillScreen) R.string.player_fit_screen else R.string.player_fill_screen,
                        ),
                    onClick = {
                        onInteraction()
                        callbacks.onToggleFillScreen()
                    },
                )
            }
        }
    }
}

@Composable
private fun TimeLabel(
    millis: Long,
    highlighted: Boolean,
) {
    Text(
        text = DurationFormatter.formatElapsed(millis.milliseconds),
        style = MaterialTheme.typography.labelMedium.tabularNumbers(),
        color = if (highlighted) Color.White else Color.White.copy(alpha = TRACK_ALPHA),
    )
}

/** 영상 위에 얹는 반투명 원형 버튼. */
@Composable
internal fun GlassIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    size: androidx.compose.ui.unit.Dp = GLASS_BUTTON,
) {
    val press = rememberPressScale()
    Box(
        modifier =
            press
                .applyTo(Modifier)
                .size(size)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = GLASS_ALPHA))
                .clickable(
                    interactionSource = press.interactionSource,
                    indication = null,
                    onClick = onClick,
                ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = Color.White,
            modifier = Modifier.size(size / 2),
        )
    }
}

private val GLASS_BUTTON = 48.dp
private val SEEK_BUTTON = 64.dp
private val PLAY_BUTTON = 96.dp
private val PLAY_ICON = 44.dp
private const val GLASS_ALPHA = 0.15f
private const val TRACK_ALPHA = 0.6f
private const val CONTROLS_FADE_MILLIS = 250
