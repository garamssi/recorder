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

    /** 녹화 진행 중. [elapsed]는 일시정지 구간을 제외한 실제 녹화 시간이다. */
    data class Recording(
        val elapsed: Duration,
    ) : RecordingState

    /** 일시정지됨 (기능명세서 11.2절). 세션과 캡처 자원은 유지된다. */
    data class Paused(
        val elapsed: Duration,
    ) : RecordingState

    /** 중지 처리 중 (파일 마무리). */
    data object Stopping : RecordingState
}
