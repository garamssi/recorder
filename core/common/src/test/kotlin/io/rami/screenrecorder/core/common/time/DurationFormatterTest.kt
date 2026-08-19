package io.rami.screenrecorder.core.common.time

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class DurationFormatterTest {
    @Test
    fun `0초는 00_00으로 표시한다`() {
        assertEquals("00:00", DurationFormatter.formatElapsed(Duration.ZERO))
    }

    @Test
    fun `1시간 미만은 MM_SS로 표시한다`() {
        assertEquals("00:45", DurationFormatter.formatElapsed(45.seconds))
        assertEquals("03:24", DurationFormatter.formatElapsed(3.minutes + 24.seconds))
        assertEquals("59:59", DurationFormatter.formatElapsed(59.minutes + 59.seconds))
    }

    @Test
    fun `1시간 이상은 HH_MM_SS로 표시한다`() {
        assertEquals("01:00:00", DurationFormatter.formatElapsed(1.hours))
        assertEquals("01:01:01", DurationFormatter.formatElapsed(1.hours + 1.minutes + 1.seconds))
        assertEquals("12:00:00", DurationFormatter.formatElapsed(12.hours))
    }

    @Test
    fun `밀리초 단위는 버림 처리한다`() {
        assertEquals("00:01", DurationFormatter.formatElapsed(1.seconds + 999.milliseconds))
    }

    @Test
    fun `음수 시간은 허용하지 않는다`() {
        assertThrows<IllegalArgumentException> {
            DurationFormatter.formatElapsed((-1).seconds)
        }
    }
}
