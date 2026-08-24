package io.rami.screenrecorder.presentation.player

/** 플레이어 컨트롤이 상위에 위임하는 동작 묶음. */
@Suppress("LongParameterList") // 콜백 홀더 — 파라미터 수가 곧 내용이다.
internal class PlayerCallbacks(
    val onBack: () -> Unit,
    val onPlayPause: () -> Unit,
    val onSeekBack: () -> Unit,
    val onSeekForward: () -> Unit,
    val onSeekTo: (Long) -> Unit,
    val onSpeedSelected: (Float) -> Unit,
    /** 화면 채우기(확대·크롭) 토글. */
    val onToggleFillScreen: () -> Unit,
    /** 시스템 미디어 볼륨을 비율로 설정. */
    val onVolumeChange: (Float) -> Unit,
    /** 음소거 토글. */
    val onToggleMute: () -> Unit,
    val onRename: (String) -> Unit,
    val onDelete: () -> Unit,
)
