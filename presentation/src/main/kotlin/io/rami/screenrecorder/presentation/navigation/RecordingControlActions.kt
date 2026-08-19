package io.rami.screenrecorder.presentation.navigation

/** 녹화 제어를 상위(Activity)에 위임하는 콜백 묶음 (동의/서비스는 앱 조립층 소관). */
class RecordingControlActions(
    val onStart: () -> Unit,
    val onStop: () -> Unit,
    val onPause: () -> Unit,
    val onResume: () -> Unit,
)
