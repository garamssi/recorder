package io.rami.screenrecorder.presentation.player

import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.input.pointer.pointerInput
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
                modifier = Modifier.weight(1f),
            )
            if (!isFullscreen) {
                SpeedAndFullscreenRow(
                    playbackSpeed = playbackSpeed,
                    onSpeedSelected = { playbackSpeed = it },
                    onFullscreen = { isFullscreen = true },
                )
            }
        }
    }
    if (isFullscreen) {
        // 전체 화면에서 뒤로가기는 일반 모드 복귀 (기능명세서 10절 전체 화면 토글).
        androidx.activity.compose.BackHandler { isFullscreen = false }
    }
}

/** 재생 서피스 + 더블 탭 ±10초 (기능명세서 10절). */
@Composable
private fun PlayerSurface(
    player: ExoPlayer,
    modifier: Modifier = Modifier,
) {
    var playerViewRef by remember { mutableStateOf<PlayerView?>(null) }
    Box(modifier = modifier) {
        AndroidView(
            factory = { viewContext ->
                PlayerView(viewContext).apply {
                    this.player = player
                    setShowNextButton(false)
                    setShowPreviousButton(false)
                    playerViewRef = this
                }
            },
            modifier = Modifier.fillMaxSize(),
        )
        // 좌/우 반면 더블 탭 = 뒤로/앞으로 10초, 단일 탭 = 컨트롤러 토글 위임.
        Row(modifier = Modifier.fillMaxSize()) {
            DoubleTapArea(
                onDoubleTap = { player.seekBack() },
                onSingleTap = { playerViewRef?.performClick() },
                modifier = Modifier.weight(1f),
            )
            DoubleTapArea(
                onDoubleTap = { player.seekForward() },
                onSingleTap = { playerViewRef?.performClick() },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun DoubleTapArea(
    onDoubleTap: () -> Unit,
    onSingleTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(
                        onDoubleTap = { onDoubleTap() },
                        onTap = { onSingleTap() },
                    )
                },
    )
}

/** 배속 칩 + 전체 화면 토글 (기능명세서 10절). */
@Composable
private fun SpeedAndFullscreenRow(
    playbackSpeed: Float,
    onSpeedSelected: (Float) -> Unit,
    onFullscreen: () -> Unit,
) {
    Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        PLAYBACK_SPEEDS.forEach { speed ->
            FilterChip(
                selected = playbackSpeed == speed,
                onClick = { onSpeedSelected(speed) },
                label = {
                    Text(stringResource(R.string.player_speed_format, formatSpeed(speed)))
                },
                modifier = Modifier.padding(end = 8.dp),
            )
        }
        // material-icons-core에는 Fullscreen 아이콘이 없어 텍스트 버튼으로 제공한다.
        androidx.compose.material3.TextButton(onClick = onFullscreen) {
            Text(stringResource(R.string.player_fullscreen))
        }
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

private fun formatSpeed(speed: Float): String =
    if (speed == speed.toInt().toFloat()) speed.toInt().toString() else speed.toString()

private val PLAYBACK_SPEEDS = listOf(0.5f, 1f, 1.5f, 2f)
private const val DEFAULT_SPEED = 1f
private const val SEEK_INCREMENT_MS = 10_000L
