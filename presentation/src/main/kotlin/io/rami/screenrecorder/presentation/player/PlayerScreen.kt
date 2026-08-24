package io.rami.screenrecorder.presentation.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.net.toUri
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import io.rami.screenrecorder.domain.model.Recording
import io.rami.screenrecorder.presentation.R

/** 내장 플레이어 화면 (기능명세서 10절, DESIGN_GUIDE.md 4절 "Video Player UI"). */
@Composable
fun PlayerScreen(
    onBack: () -> Unit,
    viewModel: PlayerViewModel = hiltViewModel(),
) {
    val target by viewModel.target.collectAsState()

    // 재생 중 화면 꺼짐 방지 (기능명세서 10절: KEEP_SCREEN_ON).
    val view = LocalView.current
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }

    when (val current = target) {
        is PlayerTarget.Loading -> Box(Modifier.fillMaxSize().background(Color.Black))

        is PlayerTarget.Missing ->
            // 삭제(휴지통 이동) 등으로 대상이 사라지면 목록으로 복귀한다 (기능명세서 10절).
            LaunchedEffect(Unit) { onBack() }

        is PlayerTarget.Found ->
            PlayerContent(recording = current.recording, viewModel = viewModel, onBack = onBack)
    }
}

@Composable
private fun PlayerContent(
    recording: Recording,
    viewModel: PlayerViewModel,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    var playbackSpeed by rememberSaveable { mutableFloatStateOf(DEFAULT_SPEED) }
    // 회전(컴포지션 재생성) 시 재생 위치를 복원한다 (기능명세서 10절 회전 대응).
    var savedPositionMs by rememberSaveable { mutableLongStateOf(0L) }
    // 플레이어는 이미 화면을 꽉 채우므로 "전체화면"은 영상을 잘라 화면을 채우는 모드를 뜻한다.
    var fillScreen by rememberSaveable { mutableStateOf(false) }

    val player =
        remember {
            ExoPlayer
                .Builder(context)
                .setSeekBackIncrementMs(SEEK_INCREMENT_MS)
                .setSeekForwardIncrementMs(SEEK_INCREMENT_MS)
                .build()
                .apply {
                    setMediaItem(MediaItem.fromUri(recording.contentUri.toUri()))
                    prepare()
                    seekTo(savedPositionMs)
                    playWhenReady = true
                }
        }
    DisposableEffect(Unit) {
        onDispose {
            savedPositionMs = player.currentPosition
            player.release()
        }
    }
    LaunchedEffect(playbackSpeed) { player.setPlaybackSpeed(playbackSpeed) }

    // 화면 채우기에서는 시스템 바까지 숨겨 영상만 남긴다 (기능명세서 10절).
    val view = LocalView.current
    LaunchedEffect(fillScreen) { applyImmersive(view, fillScreen) }
    DisposableEffect(Unit) { onDispose { applyImmersive(view, immersive = false) } }

    val playback = rememberPlaybackState(player)
    val volume by viewModel.volume.collectAsState()

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        PlayerSurface(
            player = player,
            fillScreen = fillScreen,
            modifier = Modifier.fillMaxSize(),
        )
        PlayerControlsOverlay(
            recording = recording,
            playback = playback,
            playbackSpeed = playbackSpeed,
            fillScreen = fillScreen,
            volume = volume,
            callbacks =
                PlayerCallbacks(
                    onBack = onBack,
                    onPlayPause = { player.playOrRestart(playback.hasEnded) },
                    onSeekBack = player::seekBack,
                    onSeekForward = player::seekForward,
                    onSeekTo = player::seekTo,
                    onSpeedSelected = { playbackSpeed = it },
                    onToggleFillScreen = { fillScreen = !fillScreen },
                    onVolumeChange = viewModel::onVolumeChanged,
                    onToggleMute = viewModel::onToggleMute,
                    onRename = viewModel::onRenameConfirmed,
                    onDelete = viewModel::onDeleteConfirmed,
                ),
        )
    }

    if (fillScreen) {
        // 화면 채우기에서 뒤로가기 = 원래 비율 복귀 (버튼과 함께 이중 탈출 경로).
        androidx.activity.compose.BackHandler { fillScreen = false }
    }
}

/**
 * ExoPlayer 재생 서피스.
 *
 * 내장 컨트롤러는 끄고(view_player.xml) 컨트롤을 Compose로 그린다.
 * TextureView 표면을 그대로 써서 SurfaceView 특유의 지터/잔상을 피한다.
 *
 * @param fillScreen true면 영상을 확대·크롭해 화면을 꽉 채운다 (레터박스 제거).
 */
@Composable
private fun PlayerSurface(
    player: ExoPlayer,
    fillScreen: Boolean,
    modifier: Modifier = Modifier,
) {
    AndroidView(
        factory = { viewContext -> buildPlayerView(viewContext, player) },
        update = { playerView ->
            playerView.resizeMode =
                if (fillScreen) {
                    androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                } else {
                    androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
                }
        },
        // 컴포지션 이탈 시 플레이어 연결을 끊어 마지막 프레임 잔상을 남기지 않는다.
        onRelease = { it.player = null },
        modifier = modifier,
    )
}

/**
 * 재생/일시정지 토글. 끝까지 재생된 뒤에는 처음으로 되돌린 다음 재생한다.
 *
 * ExoPlayer는 STATE_ENDED에서 play()를 불러도 위치가 끝이라 아무 일도 일어나지 않는다.
 */
private fun ExoPlayer.playOrRestart(hasEnded: Boolean) {
    when {
        hasEnded -> {
            seekTo(0)
            play()
        }

        isPlaying -> pause()
        else -> play()
    }
}

@android.annotation.SuppressLint("InflateParams")
private fun buildPlayerView(
    context: android.content.Context,
    player: ExoPlayer,
): PlayerView {
    val playerView =
        android.view.LayoutInflater
            .from(context)
            .inflate(R.layout.view_player, null) as PlayerView
    playerView.player = player
    return playerView
}

/** 몰입 모드로 시스템 바를 숨기거나 되돌린다. */
private fun applyImmersive(
    view: android.view.View,
    immersive: Boolean,
) {
    val window = (view.context as? android.app.Activity)?.window ?: return
    val controller =
        androidx.core.view.WindowCompat
            .getInsetsController(window, view)
    val systemBars =
        androidx.core.view.WindowInsetsCompat.Type
            .systemBars()
    if (immersive) {
        controller.systemBarsBehavior =
            androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(systemBars)
    } else {
        controller.show(systemBars)
    }
}

/** 배속 선택지: 0.5x부터 2.0x까지 0.1 단위 (기능명세서 10절). */
internal val PLAYBACK_SPEEDS: List<Float> =
    (SPEED_MIN_STEPS..SPEED_MAX_STEPS).map { it / SPEED_DENOMINATOR }

private const val SPEED_DENOMINATOR = 10f
private const val SPEED_MIN_STEPS = 5 // 0.5x
private const val SPEED_MAX_STEPS = 20 // 2.0x
private const val DEFAULT_SPEED = 1f
private const val SEEK_INCREMENT_MS = 10_000L
