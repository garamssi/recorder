package io.rami.screenrecorder.presentation.home

import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import io.rami.screenrecorder.domain.model.TimeLimit
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
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
    fun `분은 59에서 더 오르지 않고 자리 넘김도 없다`() {
        showDialog()
        typeInto("분", "59")

        compose.onNodeWithContentDescription("분 늘리기").performClick()

        compose.onNodeWithText("59").assertExists()
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
}
