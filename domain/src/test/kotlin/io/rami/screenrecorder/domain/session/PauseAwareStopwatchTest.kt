package io.rami.screenrecorder.domain.session

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

private class FakeMonotonicClock(
    var nowMillis: Long = 0L,
) : MonotonicClock {
    override fun elapsedRealtimeMillis(): Long = nowMillis
}

class PauseAwareStopwatchTest {
    private val clock = FakeMonotonicClock()
    private val stopwatch = PauseAwareStopwatch(clock)

    @Test
    fun `시작 후 경과 시간을 잰다`() {
        stopwatch.start()
        clock.nowMillis = 5_000
        assertEquals(5.seconds, stopwatch.elapsed())
    }

    @Test
    fun `일시정지 구간은 경과 시간에서 제외된다`() {
        stopwatch.start()
        clock.nowMillis = 5_000
        stopwatch.pause()
        clock.nowMillis = 65_000 // 60초 일시정지
        stopwatch.resume()
        clock.nowMillis = 70_000 // 재개 후 5초
        assertEquals(10.seconds, stopwatch.elapsed())
    }

    @Test
    fun `일시정지 중에는 경과 시간이 멈춘다`() {
        stopwatch.start()
        clock.nowMillis = 3_000
        stopwatch.pause()
        clock.nowMillis = 100_000
        assertEquals(3.seconds, stopwatch.elapsed())
    }

    @Test
    fun `여러 번 일시정지해도 누적 보정된다`() {
        stopwatch.start()
        clock.nowMillis = 1_000
        stopwatch.pause()
        clock.nowMillis = 2_000
        stopwatch.resume()
        clock.nowMillis = 3_000
        stopwatch.pause()
        clock.nowMillis = 10_000
        stopwatch.resume()
        clock.nowMillis = 10_500
        assertEquals(2_500.milliseconds, stopwatch.elapsed())
    }

    @Test
    fun `총 일시정지 시간을 조회할 수 있다`() {
        stopwatch.start()
        clock.nowMillis = 1_000
        stopwatch.pause()
        clock.nowMillis = 4_000
        stopwatch.resume()
        assertEquals(3.seconds, stopwatch.totalPaused())
    }

    @Test
    fun `이미 시작된 스톱워치를 다시 시작하면 상태 오류다`() {
        stopwatch.start()
        org.junit.jupiter.api
            .assertThrows<IllegalStateException> { stopwatch.start() }
    }

    @Test
    fun `녹화 중이 아닐 때 pause를 호출하면 상태 오류다`() {
        org.junit.jupiter.api
            .assertThrows<IllegalStateException> { stopwatch.pause() }
    }

    @Test
    fun `일시정지가 아닐 때 resume을 호출하면 상태 오류다`() {
        stopwatch.start()
        org.junit.jupiter.api
            .assertThrows<IllegalStateException> { stopwatch.resume() }
    }
}
