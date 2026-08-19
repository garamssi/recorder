package io.rami.screenrecorder.domain.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/** 기능명세서 11절 [결정] 정책 값을 고정하는 테스트 (명세 변경 시 함께 갱신). */
class SessionPolicyTest {
    @Test
    fun `일시정지 자동 중지는 30분이고 5분 전에 예고한다`() {
        assertEquals(30.minutes, PauseTimeoutPolicy.AUTO_STOP_AFTER)
        assertEquals(5.minutes, PauseTimeoutPolicy.WARNING_BEFORE)
    }

    @Test
    fun `타이머 예고는 1분 전과 10초 전이다`() {
        assertEquals(60.seconds, TimeLimitWarningPolicy.FIRST_WARNING_BEFORE)
        assertEquals(10.seconds, TimeLimitWarningPolicy.FINAL_WARNING_BEFORE)
    }

    @Test
    fun `녹화 유지 임계 공간은 200MB다`() {
        assertEquals(200_000_000L, RecordableTimeEstimator.MIN_FREE_BYTES_TO_CONTINUE)
    }

    @Test
    fun `자동 중지 사유는 3가지다`() {
        assertEquals(3, AutoStopReason.entries.size)
    }

    @Test
    fun `세션 이벤트는 예고 시간을 담는다`() {
        val warning = RecordingSessionEvent.TimeLimitWarning(10.seconds)
        val pauseWarning = RecordingSessionEvent.PauseTimeoutWarning(5.minutes)
        val stopped = RecordingSessionEvent.AutoStopped(AutoStopReason.STORAGE_LOW)

        assertEquals(10.seconds, warning.remaining)
        assertEquals(5.minutes, pauseWarning.remaining)
        assertEquals(AutoStopReason.STORAGE_LOW, stopped.reason)
    }
}
