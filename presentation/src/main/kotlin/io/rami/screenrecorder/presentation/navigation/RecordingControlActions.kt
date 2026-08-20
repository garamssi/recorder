package io.rami.screenrecorder.presentation.navigation

/**
 * 녹화 제어를 상위(Activity)에 위임하는 콜백 묶음.
 *
 * 동의(MediaProjection)와 포그라운드 서비스 기동은 앱 조립층 소관이므로
 * presentation은 "무엇을 하고 싶은지"만 알린다 (CLAUDE.md 3절).
 *
 * 화면 캡처·음성 녹음은 앱 밖에 떠 있는 플로팅 버블이 진입점이므로 여기 두지 않는다
 * (기능명세서 11.1절).
 */
class RecordingControlActions(
    /** 화면 녹화 시작 (기능명세서 2.2절). */
    val onStart: () -> Unit,
    /** 화면 녹화 중지. */
    val onStop: () -> Unit,
    /** 화면 녹화 일시정지. */
    val onPause: () -> Unit,
    /** 화면 녹화 재개. */
    val onResume: () -> Unit,
)
