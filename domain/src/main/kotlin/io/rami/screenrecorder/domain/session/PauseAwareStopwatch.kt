package io.rami.screenrecorder.domain.session

import kotlin.time.Duration

/**
 * 일시정지 구간을 제외한 실제 녹화 경과 시간을 계산하는 스톱워치 (기능명세서 11.2절).
 *
 * 인코더 presentationTimeUs 보정과 타이머 녹화(11.4절)의 기준 시간 계산에 쓴다.
 * 스레드 안전하지 않으므로 세션 오케스트레이터 단일 스레드에서 사용한다.
 */
class PauseAwareStopwatch(
    private val clock: MonotonicClock,
) {
    /** 스톱워치를 시작한다. */
    fun start(): Unit = TODO()

    /** 일시정지한다. 녹화 중이 아니면 [IllegalStateException]. */
    fun pause(): Unit = TODO()

    /** 재개한다. 일시정지 상태가 아니면 [IllegalStateException]. */
    fun resume(): Unit = TODO()

    /** 일시정지 구간을 제외한 경과 시간. */
    fun elapsed(): Duration = TODO()

    /** 누적 일시정지 시간. */
    fun totalPaused(): Duration = TODO()
}
