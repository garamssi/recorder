package io.rami.screenrecorder.presentation.player

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.net.toUri
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import io.rami.screenrecorder.domain.model.Recording
import io.rami.screenrecorder.presentation.R
import io.rami.screenrecorder.presentation.library.DeleteConfirmDialog
import io.rami.screenrecorder.presentation.library.DetailDialog
import io.rami.screenrecorder.presentation.library.RenameDialog
import io.rami.screenrecorder.presentation.library.shareRecording

/** 내장 플레이어 화면 (기능명세서 10절, DESIGN_GUIDE 1i). */
@OptIn(ExperimentalMaterial3Api::class)
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
        is PlayerTarget.Loading -> Unit

        is PlayerTarget.Missing ->
            // 삭제(휴지통 이동) 등으로 대상이 사라지면 목록으로 복귀한다 (기능명세서 10절).
            LaunchedEffect(Unit) { onBack() }

        is PlayerTarget.Found ->
            PlayerContent(recording = current.recording, viewModel = viewModel, onBack = onBack)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
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
    var isFullscreen by rememberSaveable { mutableStateOf(false) }
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

    // 전체 화면에서는 시스템 바를 숨겨 몰입형으로 전환한다 (기능명세서 10절).
    val view = LocalView.current
    LaunchedEffect(isFullscreen) { applyImmersive(view, isFullscreen) }
    DisposableEffect(Unit) { onDispose { applyImmersive(view, immersive = false) } }

    Scaffold(
        topBar = {
            if (!isFullscreen) {
                PlayerTopBar(recording, viewModel, onBack)
            }
        },
    ) { padding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding),
        ) {
            PlayerSurface(
                player = player,
                onFullscreenToggle = { isFullscreen = it },
                modifier = Modifier.weight(1f),
            )
            if (!isFullscreen) {
                SpeedRow(
                    playbackSpeed = playbackSpeed,
                    onSpeedSelected = { playbackSpeed = it },
                )
            }
        }
    }
    if (isFullscreen) {
        // 전체 화면에서 뒤로가기 = 일반 모드 복귀 (컨트롤러의 전체화면 버튼과 함께 이중 탈출 경로).
        androidx.activity.compose.BackHandler { isFullscreen = false }
    }
}

/**
 * ExoPlayer 재생 서피스.
 *
 * PlayerView 내장 컨트롤러(재생/일시정지·시크바·±10초 버튼·단일 탭 표시)를 그대로 쓰고,
 * 전체 화면 토글은 컨트롤러의 전체화면 버튼으로 노출한다. 더블 탭 ±10초는 이벤트를 소비하지
 * 않는 GestureDetector로 얹어 컨트롤러 동작을 막지 않는다 (이전 오버레이 방식의 버그 수정).
 */
@Composable
private fun PlayerSurface(
    player: ExoPlayer,
    onFullscreenToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    AndroidView(
        factory = { viewContext -> buildPlayerView(viewContext, player, onFullscreenToggle) },
        modifier = modifier,
    )
}

@android.annotation.SuppressLint("ClickableViewAccessibility")
private fun buildPlayerView(
    context: android.content.Context,
    player: ExoPlayer,
    onFullscreenToggle: (Boolean) -> Unit,
): PlayerView =
    PlayerView(context).apply {
        this.player = player
        setShowNextButton(false)
        setShowPreviousButton(false)
        // 컨트롤러에 전체화면 버튼을 노출한다 — 진입/복귀 모두 이 버튼으로 가능하다.
        setFullscreenButtonClickListener(onFullscreenToggle::invoke)
        val doubleTapDetector =
            android.view.GestureDetector(
                context,
                object : android.view.GestureDetector.SimpleOnGestureListener() {
                    override fun onDoubleTap(event: android.view.MotionEvent): Boolean {
                        if (event.x < width / 2f) player.seekBack() else player.seekForward()
                        return true
                    }
                },
            )
        // 이벤트를 소비하지 않으므로(반환 false) PlayerView의 단일 탭·버튼 처리가 그대로 동작한다.
        setOnTouchListener { _, event ->
            doubleTapDetector.onTouchEvent(event)
            false
        }
    }

/** 배속 선택 (기능명세서 10절: 0.5x~2.0x, 0.1 단위 선택). */
@Composable
private fun SpeedRow(
    playbackSpeed: Float,
    onSpeedSelected: (Float) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.player_speed_label),
            style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(end = 8.dp),
        )
        Box {
            androidx.compose.material3.OutlinedButton(onClick = { expanded = true }) {
                Text(stringResource(R.string.player_speed_format, formatSpeed(playbackSpeed)))
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                PLAYBACK_SPEEDS.forEach { speed ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                stringResource(R.string.player_speed_format, formatSpeed(speed)) +
                                    if (speed == playbackSpeed) "  ✓" else "",
                            )
                        },
                        onClick = {
                            onSpeedSelected(speed)
                            expanded = false
                        },
                    )
                }
            }
        }
    }
}

/** 전체 화면 몰입 모드로 시스템 바를 숨기거나 되돌린다. */
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

/** 상단 메뉴 (기능명세서 10절: 이름 변경/공유/상세 정보/삭제). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlayerTopBar(
    recording: Recording,
    viewModel: PlayerViewModel,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    var menuExpanded by remember { mutableStateOf(false) }
    var showRename by remember { mutableStateOf(false) }
    var showDetail by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }

    TopAppBar(
        title = { Text(recording.displayName, maxLines = 1) },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.navigate_back),
                )
            }
        },
        actions = {
            IconButton(onClick = { menuExpanded = true }) {
                Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.menu_more))
            }
            DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.menu_rename)) },
                    onClick = {
                        menuExpanded = false
                        showRename = true
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.menu_share)) },
                    onClick = {
                        menuExpanded = false
                        shareRecording(context, recording)
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.menu_details)) },
                    onClick = {
                        menuExpanded = false
                        showDetail = true
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.menu_delete)) },
                    onClick = {
                        menuExpanded = false
                        showDelete = true
                    },
                )
            }
        },
    )

    if (showRename) {
        RenameDialog(
            initialName = recording.displayName,
            onConfirm = viewModel::onRenameConfirmed,
            onDismiss = { showRename = false },
        )
    }
    if (showDetail) {
        DetailDialog(recording = recording, onDismiss = { showDetail = false })
    }
    if (showDelete) {
        DeleteConfirmDialog(
            count = 1,
            onConfirm = viewModel::onDeleteConfirmed,
            onDismiss = { showDelete = false },
        )
    }
}

/** 항상 소수 첫째 자리까지 표시한다 (예: 0.5, 1.0, 1.5). */
private fun formatSpeed(speed: Float): String = "%.1f".format(speed)

/** 0.5x부터 2.0x까지 0.1 단위 (정수 스텝을 10으로 나눠 부동소수 드리프트를 줄인다). */
private val PLAYBACK_SPEEDS: List<Float> =
    (SPEED_MIN_STEPS..SPEED_MAX_STEPS).map { it / SPEED_DENOMINATOR }

private const val SPEED_DENOMINATOR = 10f
private const val SPEED_MIN_STEPS = 5 // 0.5x
private const val SPEED_MAX_STEPS = 20 // 2.0x
private const val DEFAULT_SPEED = 1f
private const val SEEK_INCREMENT_MS = 10_000L
