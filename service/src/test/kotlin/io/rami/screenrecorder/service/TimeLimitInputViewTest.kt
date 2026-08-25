package io.rami.screenrecorder.service

import android.content.Context
import io.rami.screenrecorder.domain.model.TimeLimit
import io.rami.screenrecorder.domain.model.TimeLimitField
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * 버블에서 여는 녹화 시간 제한 입력 창 (기능명세서 11.4절).
 *
 * 옵션 시트의 직접 입력과 같은 규칙을 쓴다 — 10초~12시간, 벗어나면 입력 단계에서 차단한다.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "ko")
class TimeLimitInputViewTest {
    private val context: Context get() = RuntimeEnvironment.getApplication()

    private var result: TimeLimit? = null
    private var dismissed = false

    private fun build(current: TimeLimit) =
        context.buildTimeLimitInput(
            current = current,
            onConfirm = { result = it },
            onDismiss = { dismissed = true },
        )

    private fun TimeLimitInputViews.type(
        hours: String,
        minutes: String,
        seconds: String,
    ) {
        this.hours.setText(hours)
        this.minutes.setText(minutes)
        this.seconds.setText(seconds)
    }

    @Test
    fun `현재 설정값을 시분초로 미리 채운다`() {
        val views = build(TimeLimit.Limited(1.hours + 30.minutes))

        assertEquals("1", views.hours.text.toString())
        assertEquals("30", views.minutes.text.toString())
        assertEquals("0", views.seconds.text.toString())
    }

    @Test
    fun `제한이 없으면 0으로 시작한다`() {
        val views = build(TimeLimit.None)

        val typed = listOf(views.hours, views.minutes, views.seconds).map { it.text.toString() }

        assertEquals(listOf("0", "0", "0"), typed)
    }

    @Test
    fun `확인을 누르면 입력한 시간이 제한으로 전달된다`() {
        val views = build(TimeLimit.None)

        views.type("0", "10", "0")
        views.confirm.performClick()

        assertEquals(TimeLimit.Limited(10.minutes), result)
    }

    @Test
    fun `최소값보다 짧으면 확인을 막고 사유를 보여준다`() {
        val views = build(TimeLimit.None)

        views.type("0", "0", "5")

        assertFalse("10초 미만은 저장할 수 없다", views.confirm.isEnabled)
        assertEquals("최소 10초 이상이어야 합니다", views.error.text.toString())
    }

    @Test
    fun `최대값보다 길면 확인을 막고 사유를 보여준다`() {
        val views = build(TimeLimit.None)

        views.type("13", "0", "0")

        assertFalse("12시간 초과는 저장할 수 없다", views.confirm.isEnabled)
        assertEquals("최대 12시간까지 설정할 수 있습니다", views.error.text.toString())
    }

    @Test
    fun `범위 안으로 되돌리면 확인이 다시 열린다`() {
        val views = build(TimeLimit.None)

        views.type("0", "0", "5")
        views.type("0", "0", "30")

        assertTrue(views.confirm.isEnabled)
    }

    @Test
    fun `제한 없음을 누르면 제한이 해제된다`() {
        val views = build(TimeLimit.Limited(10.minutes))

        views.clear.performClick()

        assertEquals(TimeLimit.None, result)
    }

    @Test
    fun `증가 버튼은 칸 값을 1씩 올린다`() {
        val views = build(TimeLimit.None)

        views.type("0", "10", "30")
        views.stepUp(TimeLimitField.MINUTES).performClick()

        assertEquals("11", views.minutes.text.toString())
    }

    @Test
    fun `감소 버튼은 칸 값을 1씩 내린다`() {
        val views = build(TimeLimit.None)

        views.type("1", "10", "30")
        views.stepDown(TimeLimitField.HOURS).performClick()

        assertEquals("0", views.hours.text.toString())
    }

    @Test
    fun `분은 59에서 더 오르지 않고 시로 넘어가지도 않는다`() {
        val views = build(TimeLimit.None)

        views.type("0", "59", "0")
        views.stepUp(TimeLimitField.MINUTES).performClick()

        assertEquals("59", views.minutes.text.toString())
        assertEquals("0", views.hours.text.toString())
    }

    @Test
    fun `0에서 더 내려가지 않는다`() {
        val views = build(TimeLimit.None)

        views.type("0", "0", "0")
        views.stepDown(TimeLimitField.SECONDS).performClick()

        assertEquals("0", views.seconds.text.toString())
    }

    @Test
    fun `증감으로 범위를 벗어나면 검증도 따라 막는다`() {
        val views = build(TimeLimit.Limited(11.hours + 59.minutes + 59.seconds))

        views.stepUp(TimeLimitField.HOURS).performClick()

        assertFalse("12시 59분 59초는 12시간을 넘는다", views.confirm.isEnabled)
        assertEquals("최대 12시간까지 설정할 수 있습니다", views.error.text.toString())
    }

    @Test
    fun `증감으로 맞춘 값을 그대로 저장한다`() {
        val views = build(TimeLimit.None)

        views.type("0", "9", "0")
        views.stepUp(TimeLimitField.MINUTES).performClick()
        views.confirm.performClick()

        assertEquals(TimeLimit.Limited(10.minutes), result)
    }

    @Test
    fun `취소는 값을 바꾸지 않고 창만 닫는다`() {
        val views = build(TimeLimit.Limited(10.minutes))

        views.type("0", "20", "0")
        views.cancel.performClick()

        assertNull("취소했는데 값이 전달되면 설정이 몰래 바뀐다", result)
        assertTrue(dismissed)
    }
}
