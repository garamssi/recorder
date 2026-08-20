package io.rami.screenrecorder.presentation.home

import io.rami.screenrecorder.presentation.navigation.RecordingControlActions

/**
 * 홈 화면이 상위에 위임하는 동작 묶음.
 *
 * 설정·휴지통 이동은 셸(레일/상단 바)과 목록 화면이 담당하므로 여기 두지 않는다.
 */
class HomeActions(
    /** 캡처 제어 콜백. */
    val control: RecordingControlActions,
    /** 최근 녹화 "전체 보기" — 목록 탭으로 전환한다. */
    val onOpenLibrary: () -> Unit,
)
