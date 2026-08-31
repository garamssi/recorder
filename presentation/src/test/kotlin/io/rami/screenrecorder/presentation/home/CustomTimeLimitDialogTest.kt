package io.rami.screenrecorder.presentation.home

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import io.rami.screenrecorder.domain.model.TimeLimit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

/**
 * 홈 옵션 시트의 시간 제한 직접 입력 (기능명세서 11.4절).
 *
 * 플로팅 버블의 입력 창과 같은 규칙을 쓴다 — 칸별 증감, 칸별 범위, 총합 10초~12시간.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "ko")
class CustomTimeLimitDialogTest {
    @get:Rule
    val compose = createComposeRule()

    private var confirmed: TimeLimit? = null

    private fun showDialog(current: TimeLimit = TimeLimit.None) {
        compose.setContent {
            CustomTimeLimitDialog(
                current = current,
                onConfirm = { confirmed = it },
                onDismiss = {},
            )
        }
    }

    private fun typeInto(
        unit: String,
        value: String,
    ) = compose.onNodeWithContentDescription(unit).performTextReplacement(value)

    /** 칸에 적힌 현재 값. 저장을 거치지 않고 화면 그대로 읽는다. */
    private fun valueOf(unit: String): String =
        compose
            .onNodeWithContentDescription(unit)
            .fetchSemanticsNode()
            .config[SemanticsProperties.EditableText]
            .text

    /**
     * [unit] 칸의 증가 버튼을 [millis] 동안 누르고 있는다.
     *
     * 연속 증감은 시간이 흘러야 일어난다. 자동으로 흐르는 시계는 손을 떼기 전에
     * 멈추지 않으므로 직접 감아 준다.
     */
    private fun holdStepUp(
        unit: String,
        millis: Long,
    ) {
        val button = compose.onNodeWithContentDescription("$unit 늘리기")
        compose.mainClock.autoAdvance = false
        button.performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(millis)
        button.performTouchInput { up() }
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
    }

    /**
     * 두 진입점이 같은 창을 쓴다 (기능명세서 11.4절). 버블 쪽 문구는
     * service 모듈의 TimeLimitInputViewTest 가 같은 값으로 고정하고 있다.
     */
    @Test
    fun `제목과 안내 문구가 버블 입력 창과 같다`() {
        // 안내 자리는 범위를 벗어나면 사유로 바뀐다 — 유효한 값에서 견준다.
        showDialog(TimeLimit.Limited(10.minutes))

        compose.onNodeWithText("녹화 시간 제한").assertExists()
        compose.onNodeWithText("10초 ~ 12시간").assertExists()
    }

    @Test
    fun `버튼 구성이 버블 입력 창과 같다`() {
        showDialog()

        listOf("제한 없음", "취소", "저장").forEach { label ->
            compose.onNodeWithText(label).assertExists()
        }
    }

    @Test
    fun `제한 없음을 누르면 제한이 해제된다`() {
        showDialog(TimeLimit.Limited(10.minutes))

        compose.onNodeWithText("제한 없음").performClick()

        assertEquals(TimeLimit.None, confirmed)
    }

    @Test
    fun `현재 설정값을 시분초로 미리 채운다`() {
        showDialog(TimeLimit.Limited(1.hours + 30.minutes))

        compose.onNodeWithText("1").assertExists()
        compose.onNodeWithText("30").assertExists()
    }

    @Test
    fun `증가 버튼은 칸 값을 1씩 올린다`() {
        showDialog()
        typeInto("분", "10")

        compose.onNodeWithContentDescription("분 늘리기").performClick()

        compose.onNodeWithText("11").assertExists()
    }

    @Test
    fun `감소 버튼은 칸 값을 1씩 내린다`() {
        showDialog()
        typeInto("분", "10")

        compose.onNodeWithContentDescription("분 줄이기").performClick()

        compose.onNodeWithText("9").assertExists()
    }

    @Test
    fun `분은 59에서 올리면 0으로 돌아가고 자리 넘김은 없다`() {
        showDialog()
        typeInto("시", "1")
        typeInto("분", "59")

        compose.onNodeWithContentDescription("분 늘리기").performClick()

        assertEquals("0", valueOf("분"))
        assertEquals("1", valueOf("시"))
    }

    @Test
    fun `초는 0에서 내리면 59로 돌아간다`() {
        showDialog()
        typeInto("분", "1")

        compose.onNodeWithContentDescription("초 줄이기").performClick()

        assertEquals("59", valueOf("초"))
        assertEquals("1", valueOf("분"))
    }

    @Test
    fun `증감으로 범위를 벗어나면 저장이 막힌다`() {
        showDialog(TimeLimit.Limited(12.hours))
        typeInto("분", "30")

        compose.onNodeWithText("저장").assertIsNotEnabled()
        compose.onNodeWithText("최대 12시간까지 설정할 수 있습니다").assertExists()
    }

    @Test
    fun `회전해도 입력하던 값이 남는다`() {
        val restorer = StateRestorationTester(compose)
        restorer.setContent {
            CustomTimeLimitDialog(
                current = TimeLimit.None,
                onConfirm = { confirmed = it },
                onDismiss = {},
            )
        }
        typeInto("분", "10")

        restorer.emulateSavedInstanceStateRestore()

        compose.onNodeWithText("10").assertExists()
    }

    @Test
    fun `증감으로 맞춘 값을 그대로 확정한다`() {
        showDialog()
        typeInto("분", "9")

        compose.onNodeWithContentDescription("분 늘리기").performClick()
        compose.onNodeWithText("저장").performClick()

        assertEquals(TimeLimit.Limited(10.minutes), confirmed)
    }

    /**
     * 바깥을 눌러도 닫히지 않는다 (기능명세서 11.4절 [결정]).
     *
     * 증감 버튼은 작아서 빠르게 누르다 보면 손끝이 카드 밖으로 벗어난다. 그때 창이
     * 닫히면 값을 고치던 중에 설정 자체가 되지 않는다. 닫는 길은 버튼뿐이어야 한다.
     */
    @Test
    fun `바깥을 눌러도 닫히지 않는다`() {
        showDialog()

        val dialog = requireNotNull(ShadowDialog.getLatestDialog())
        assertFalse(shadowOf(dialog).isCancelableOnTouchOutside)
    }

    /**
     * 길게 누르면 연속으로 증감한다 (기능명세서 11.4절).
     *
     * 12시간처럼 큰 값을 한 번에 올리려면 한 번 누른 채로 계속 올라가야 한다.
     */
    @Test
    fun `증가 버튼을 길게 누르면 연속으로 오른다`() {
        showDialog()

        holdStepUp("분", HOLD_MILLIS)

        assertTrue("길게 눌렀는데 ${valueOf("분")}분에서 멈췄다", valueOf("분").toInt() > 1)
    }

    private companion object {
        /** 첫 반복이 시작되고도 여러 번 오를 만큼. */
        const val HOLD_MILLIS = 1_000L
    }
}
