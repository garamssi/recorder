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
    fun `분과 초는 0에서 내리면 59로 돌아간다`() {
        val fields = TimeLimitFields(1, 0, 0)

        assertEquals(TimeLimitFields(1, 59, 0), fields.stepped(TimeLimitField.MINUTES, -1))
        assertEquals(TimeLimitFields(1, 0, 59), fields.stepped(TimeLimitField.SECONDS, -1))
    }

    @Test
    fun `분과 초는 59에서 올리면 0으로 돌아간다`() {
        val fields = TimeLimitFields(1, 59, 59)

        assertEquals(TimeLimitFields(1, 0, 59), fields.stepped(TimeLimitField.MINUTES, 1))
        assertEquals(TimeLimitFields(1, 59, 0), fields.stepped(TimeLimitField.SECONDS, 1))
    }

    @Test
    fun `순환해도 옆 칸은 그대로다`() {
        val fields = TimeLimitFields(1, 59, 0)

        assertEquals(1, fields.stepped(TimeLimitField.MINUTES, 1).hours)
        assertEquals(59, fields.stepped(TimeLimitField.SECONDS, -1).minutes)
    }

    @Test
    fun `시는 순환하지 않고 0과 12에서 멈춘다`() {
        val bottom = TimeLimitFields(0, 30, 0)
        val top = TimeLimitFields(12, 0, 0)

        assertEquals(bottom, bottom.stepped(TimeLimitField.HOURS, -1))
        assertEquals(top, top.stepped(TimeLimitField.HOURS, 1))
    }

    @Test
    fun `칸을 직접 고쳐도 범위 안으로 맞춘다`() {
        assertEquals(TimeLimitFields(12, 59, 59), TimeLimitFields(99, 99, 99))
    }

    /** 순환은 한 눈금 옮기는 증감 버튼의 규칙이다. 친 값을 반대편으로 뒤집지는 않는다. */
    @Test
    fun `키보드로 친 값은 순환하지 않고 상한으로 맞춘다`() {
        assertEquals(
            TimeLimitFields(0, 59, 0),
            TimeLimitFields(0, 0, 0).withValue(TimeLimitField.MINUTES, 99),
        )
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
