package io.rami.screenrecorder.domain.session

import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * 일시정지 구간을 제외한 실제 녹화 경과 시간을 계산하는 스톱워치 (기능명세서 11.2절).
 *
 * 인코더 presentationTimeUs 보정과 타이머 녹화(11.4절)의 기준 시간 계산에 쓴다.
 * 스레드 안전하지 않으므로 세션 오케스트레이터 단일 스레드에서 사용한다.
 */
class PauseAwareStopwatch(
    private val clock: MonotonicClock,
) {
    private var startedAtMillis: Long = NOT_STARTED
    private var pausedAtMillis: Long = NOT_PAUSED
    private var totalPausedMillis: Long = 0

    private val isRunning: Boolean get() = startedAtMillis != NOT_STARTED
    private val isPaused: Boolean get() = pausedAtMillis != NOT_PAUSED

    /** 스톱워치를 시작한다. */
    fun start() {
        check(!isRunning) { "이미 시작된 스톱워치다" }
        startedAtMillis = clock.elapsedRealtimeMillis()
    }

    /** 일시정지한다. 녹화 중이 아니면 [IllegalStateException]. */
    fun pause() {
        check(isRunning && !isPaused) { "녹화 중이 아니면 일시정지할 수 없다" }
        pausedAtMillis = clock.elapsedRealtimeMillis()
    }

    /** 재개한다. 일시정지 상태가 아니면 [IllegalStateException]. */
    fun resume() {
        check(isPaused) { "일시정지 상태가 아니면 재개할 수 없다" }
        totalPausedMillis += clock.elapsedRealtimeMillis() - pausedAtMillis
        pausedAtMillis = NOT_PAUSED
    }

    /** 일시정지 구간을 제외한 경과 시간. */
    fun elapsed(): Duration {
        if (!isRunning) return Duration.ZERO
        val now = if (isPaused) pausedAtMillis else clock.elapsedRealtimeMillis()
        return (now - startedAtMillis - totalPausedMillis).milliseconds
    }

    /** 누적 일시정지 시간. */
    fun totalPaused(): Duration = totalPausedMillis.milliseconds

    private companion object {
        const val NOT_STARTED = -1L
        const val NOT_PAUSED = -1L
    }
}
