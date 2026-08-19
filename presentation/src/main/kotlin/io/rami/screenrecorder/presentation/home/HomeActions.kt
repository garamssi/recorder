package io.rami.screenrecorder.presentation.home

import io.rami.screenrecorder.presentation.navigation.RecordingControlActions

/** 홈 화면이 상위에 위임하는 동작 묶음 (녹화 제어 + 화면 이동). */
class HomeActions(
    val control: RecordingControlActions,
    val onOpenSettings: () -> Unit,
    val onOpenTrash: () -> Unit,
    val onOpenLibrary: () -> Unit,
)
