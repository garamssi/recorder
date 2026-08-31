package io.rami.screenrecorder.data.storage

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * 프로세스 시작 시각 환산 (기능명세서 6.1절 [결정]).
 *
 * 실제로 틀리기 쉬운 곳은 정리 정책이 아니라 이 환산이다 — 세 시계를 섞고 초로 절삭한다.
 * DI 람다 안에 두면 테스트가 닿지 않아, 판정이 조용히 어긋나도 아무도 모른다.
 */
class ProcessStartTimeTest {
    @Test
    fun `프로세스 나이를 벽시계에서 빼 시작 시각을 초로 낸다`() {
        val startedAt =
            processStartEpochSeconds(
                nowEpochMillis = 1_788_155_923_000L,
                elapsedRealtimeMillis = 60_000L,
                processStartElapsedRealtimeMillis = 10_000L,
            )

        // 프로세스 나이 50초 → 시작은 50초 전.
        assertEquals(1_788_155_873L, startedAt)
    }

    /** 절삭은 삭제를 줄이는 쪽으로만 기울어야 한다 — 반대면 진행 중인 발행을 지운다. */
    @Test
    fun `밀리초는 버림한다`() {
        val startedAt =
            processStartEpochSeconds(
                nowEpochMillis = 1_788_155_923_999L,
                elapsedRealtimeMillis = 1_000L,
                processStartElapsedRealtimeMillis = 1_000L,
            )

        assertEquals(1_788_155_923L, startedAt)
    }

    @Test
    fun `막 시작한 프로세스는 지금 시각이 된다`() {
        val startedAt =
            processStartEpochSeconds(
                nowEpochMillis = 5_000L,
                elapsedRealtimeMillis = 42L,
                processStartElapsedRealtimeMillis = 42L,
            )

        assertEquals(5L, startedAt)
    }
}
