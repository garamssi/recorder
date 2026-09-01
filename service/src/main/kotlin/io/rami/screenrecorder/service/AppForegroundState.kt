package io.rami.screenrecorder.service

import kotlinx.coroutines.flow.StateFlow

/**
 * 앱 화면이 전면에 있는지 (기능명세서 11.1절 [결정]).
 *
 * 화면 위에 뜨는 것들(플로팅 버블, 저장 오버레이)은 다른 앱 위에서 쓰라고 있는 것이다. 앱 안에는
 * 같은 일을 하는 화면이 이미 있으므로, 전면인 동안에는 감춘다.
 *
 * 무엇을 "앱 화면" 으로 셀지는 액티비티를 아는 쪽(app 계층)이 정한다 — MediaProjection 동의를
 * 받는 투명 트램폴린처럼 사용자가 보던 앱을 그대로 비추는 화면은 여기서 빠진다.
 */
interface AppForegroundState {
    /** 앱 화면이 하나라도 앞에 있으면 true. */
    val isForeground: StateFlow<Boolean>
}
