package io.rami.screenrecorder.domain.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class TimeLimitInputTest {
    @Test
    fun `시분초를 합쳐 유효한 제한을 만든다`() {
        val result = TimeLimit.fromHoursMinutesSeconds(hours = 1, minutes = 30, seconds = 15)

        assertEquals(
            TimeLimitInput.Valid(TimeLimit.Limited(1.hours + 30.minutes + 15.seconds)),
            result,
        )
    }

    @Test
    fun `최소값 10초 미만은 거부한다`() {
        assertEquals(
            TimeLimitInput.TooShort,
            TimeLimit.fromHoursMinutesSeconds(hours = 0, minutes = 0, seconds = 9),
        )
    }

    @Test
    fun `0은 거부한다`() {
        assertEquals(
            TimeLimitInput.TooShort,
            TimeLimit.fromHoursMinutesSeconds(hours = 0, minutes = 0, seconds = 0),
        )
    }

    @Test
    fun `최대값 12시간 초과는 거부한다`() {
        assertEquals(
            TimeLimitInput.TooLong,
            TimeLimit.fromHoursMinutesSeconds(hours = 12, minutes = 0, seconds = 1),
        )
    }

    @Test
    fun `경계값 10초와 12시간은 유효하다`() {
        assertEquals(
            TimeLimitInput.Valid(TimeLimit.Limited(10.seconds)),
            TimeLimit.fromHoursMinutesSeconds(0, 0, 10),
        )
        assertEquals(
            TimeLimitInput.Valid(TimeLimit.Limited(12.hours)),
            TimeLimit.fromHoursMinutesSeconds(12, 0, 0),
        )
    }

    @Test
    fun `음수 입력은 거부한다`() {
        assertEquals(
            TimeLimitInput.TooShort,
            TimeLimit.fromHoursMinutesSeconds(hours = 0, minutes = -1, seconds = 0),
        )
    }
}
