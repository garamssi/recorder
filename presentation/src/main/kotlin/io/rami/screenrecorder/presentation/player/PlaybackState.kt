package io.rami.screenrecorder.presentation.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.media3.common.Player
import kotlinx.coroutines.delay

/** 컨트롤이 그리는 데 필요한 재생 상태 스냅숏. */
class PlaybackState(
    /** 재생 중이면 true (일시정지·버퍼링 종료 시 false). */
    val isPlaying: Boolean,
    /** 현재 재생 위치 (밀리초). */
    val positionMs: Long,
    /** 전체 길이 (밀리초). 아직 알 수 없으면 0. */
    val durationMs: Long,
    /** 끝까지 재생돼 멈춘 상태. 이때 재생 버튼은 처음부터 다시 재생해야 한다. */
    val hasEnded: Boolean,
) {
    /** 진행률 0f~1f. 길이를 모를 때는 0f. */
    val progress: Float get() = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
}

/**
 * ExoPlayer 상태를 Compose 상태로 옮긴다.
 *
 * 재생 위치는 콜백이 없으므로 재생 중일 때만 주기적으로 읽는다 — 일시정지 중에는 폴링하지 않아
 * 불필요한 리컴포지션을 만들지 않는다.
 */
@Composable
internal fun rememberPlaybackState(player: Player): PlaybackState {
    var isPlaying by remember { mutableStateOf(player.isPlaying) }
    var positionMs by remember { mutableLongStateOf(player.currentPosition) }
    var durationMs by remember { mutableLongStateOf(player.knownDuration()) }
    var hasEnded by remember { mutableStateOf(player.playbackState == Player.STATE_ENDED) }

    DisposableEffect(player) {
        val listener =
            object : Player.Listener {
                override fun onIsPlayingChanged(playing: Boolean) {
                    isPlaying = playing
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                    durationMs = player.knownDuration()
                    hasEnded = playbackState == Player.STATE_ENDED
                    // 끝나면 폴링이 멈추므로 위치를 끝으로 맞춰 시크바가 100%에서 멈추게 한다.
                    if (hasEnded) positionMs = player.currentPosition
                }

                override fun onPositionDiscontinuity(
                    oldPosition: Player.PositionInfo,
                    newPosition: Player.PositionInfo,
                    reason: Int,
                ) {
                    positionMs = player.currentPosition
                    hasEnded = player.playbackState == Player.STATE_ENDED
                }
            }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }

    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            positionMs = player.currentPosition
            durationMs = player.knownDuration()
            delay(POSITION_POLL_MILLIS)
        }
    }

    return PlaybackState(
        isPlaying = isPlaying,
        positionMs = positionMs,
        durationMs = durationMs,
        hasEnded = hasEnded,
    )
}

/** 길이를 아직 모를 때 ExoPlayer가 반환하는 TIME_UNSET을 0으로 정규화한다. */
private fun Player.knownDuration(): Long = duration.takeIf { it != androidx.media3.common.C.TIME_UNSET } ?: 0L

/** 컨트롤이 3초 뒤 사라지도록 하는 유휴 판정 주기 (DESIGN_GUIDE.md 4절). */
internal const val CONTROLS_HIDE_DELAY_MILLIS = 3_000L

private const val POSITION_POLL_MILLIS = 250L
