package io.rami.screenrecorder.domain.model

import kotlin.time.Duration

/** 녹화 세션의 상태 (기능명세서 2.2, 3, 11절). */
sealed interface RecordingState {
    /** 세션 없음. */
    data object Idle : RecordingState

    /** 카운트다운 진행 중 (기능명세서 3절). 인코딩은 아직 시작되지 않았다. */
    data class CountingDown(
        val remainingSeconds: Int,
    ) : RecordingState

    /**
     * 녹화 진행 중. [elapsed]는 일시정지 구간을 제외한 실제 녹화 시간이다.
     *
     * @param timeLimit 이 세션이 시작할 때 정해진 시간 제한 (기능명세서 11.4절).
     *   알림과 플로팅 버블이 남은 시간을 병기하는 데 쓴다. 녹화 중 설정이 바뀌어도
     *   세션을 멈출 시각은 그대로이므로 설정이 아니라 상태가 들고 있어야 한다.
     */
    data class Recording(
        val elapsed: Duration,
        val timeLimit: TimeLimit = TimeLimit.None,
    ) : RecordingState

    /** 일시정지됨 (기능명세서 11.2절). 세션과 캡처 자원은 유지된다. */
    data class Paused(
        val elapsed: Duration,
        val timeLimit: TimeLimit = TimeLimit.None,
    ) : RecordingState

    /** 중지 처리 중 (파일 마무리). */
    data object Stopping : RecordingState
}
