package io.rami.screenrecorder.domain.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * 시간 제한 직접 입력 칸의 증감 규칙 (기능명세서 11.4절).
 *
 * 홈 옵션 시트와 플로팅 버블이 같은 규칙을 써야 하므로 domain에 둔다.
 */
class TimeLimitFieldsTest {
    @Test
    fun `제한이 없으면 0에서 시작한다`() {
        assertEquals(TimeLimitFields(0, 0, 0), TimeLimitFields.of(TimeLimit.None))
    }

    @Test
    fun `현재 제한을 시분초로 나눠 채운다`() {
        val fields = TimeLimitFields.of(TimeLimit.Limited(1.hours + 30.minutes + 5.seconds))

        assertEquals(TimeLimitFields(1, 30, 5), fields)
    }

    @Test
    fun `칸마다 1씩 올린다`() {
        val fields = TimeLimitFields(0, 10, 30)

        assertEquals(TimeLimitFields(1, 10, 30), fields.stepped(TimeLimitField.HOURS, 1))
        assertEquals(TimeLimitFields(0, 11, 30), fields.stepped(TimeLimitField.MINUTES, 1))
        assertEquals(TimeLimitFields(0, 10, 31), fields.stepped(TimeLimitField.SECONDS, 1))
    }

    @Test
    fun `칸마다 1씩 내린다`() {
        val fields = TimeLimitFields(1, 10, 30)

        assertEquals(TimeLimitFields(0, 10, 30), fields.stepped(TimeLimitField.HOURS, -1))
        assertEquals(TimeLimitFields(1, 9, 30), fields.stepped(TimeLimitField.MINUTES, -1))
        assertEquals(TimeLimitFields(1, 10, 29), fields.stepped(TimeLimitField.SECONDS, -1))
    }

    @Test
    fun `0에서 더 내려가지 않는다`() {
        val fields = TimeLimitFields(0, 0, 0)

        assertEquals(fields, fields.stepped(TimeLimitField.HOURS, -1))
        assertEquals(fields, fields.stepped(TimeLimitField.MINUTES, -1))
        assertEquals(fields, fields.stepped(TimeLimitField.SECONDS, -1))
    }

    @Test
    fun `분과 초는 59에서 더 올라가지 않고 자리 넘김도 없다`() {
        val fields = TimeLimitFields(0, 59, 59)

        assertEquals(fields, fields.stepped(TimeLimitField.MINUTES, 1))
        assertEquals(fields, fields.stepped(TimeLimitField.SECONDS, 1))
    }

    @Test
    fun `시는 최대 제한 시간인 12에서 멈춘다`() {
        val fields = TimeLimitFields(12, 0, 0)

        assertEquals(fields, fields.stepped(TimeLimitField.HOURS, 1))
    }

    @Test
    fun `칸을 직접 고쳐도 범위 안으로 맞춘다`() {
        assertEquals(TimeLimitFields(12, 59, 59), TimeLimitFields(99, 99, 99))
    }

    @Test
    fun `칸별 범위를 지켜도 총합이 최대를 넘으면 검증이 막는다`() {
        val fields = TimeLimitFields(12, 30, 0)

        assertEquals(TimeLimitInput.TooLong, fields.validate())
    }

    @Test
    fun `총합이 최소보다 짧으면 검증이 막는다`() {
        assertEquals(TimeLimitInput.TooShort, TimeLimitFields(0, 0, 5).validate())
    }

    @Test
    fun `범위 안이면 시간 제한으로 확정된다`() {
        val validated = TimeLimitFields(0, 10, 0).validate()

        assertEquals(TimeLimitInput.Valid(TimeLimit.Limited(10.minutes)), validated)
    }
}
