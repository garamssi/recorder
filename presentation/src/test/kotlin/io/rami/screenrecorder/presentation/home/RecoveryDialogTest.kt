package io.rami.screenrecorder.presentation.home

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.rami.screenrecorder.domain.model.PendingRecovery
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 크래시 복구 다이얼로그 (기능명세서 6.1절 [결정]).
 *
 * 1시간짜리 녹화는 remux 에 수 초가 걸린다. 그 동안 화면이 그대로면 사용자는 안 눌린 줄
 * 알고 다시 누르고, 같은 파일이 여러 번 발행돼 똑같은 녹화본이 그 횟수만큼 쌓인다.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "ko")
class RecoveryDialogTest {
    @get:Rule
    val compose = createComposeRule()

    private var recoverCount = 0
    private var discardCount = 0

    private fun show(isRecovering: Boolean) {
        compose.setContent {
            RecoveryDialog(
                recovery = PendingRecovery("t.mp4", "t.mp4", SIZE_BYTES),
                isRecovering = isRecovering,
                onRecover = { recoverCount++ },
                onDiscard = { discardCount++ },
            )
        }
    }

    @Test
    fun `복구 중이 아니면 두 버튼을 누를 수 있다`() {
        show(isRecovering = false)

        compose.onNodeWithText("복구").assertIsEnabled()
        compose.onNodeWithText("삭제").assertIsEnabled()
    }

    @Test
    fun `복구 중에는 진행 표시를 보여 준다`() {
        show(isRecovering = true)

        compose.onNodeWithContentDescription("복구하는 중…").assertExists()
    }

    @Test
    fun `복구 중에는 두 버튼이 모두 잠긴다`() {
        show(isRecovering = true)

        compose.onNodeWithText("복구").assertIsNotEnabled()
        compose.onNodeWithText("삭제").assertIsNotEnabled()
    }

    @Test
    fun `복구 중에 연타해도 다시 실행되지 않는다`() {
        show(isRecovering = true)

        repeat(TAP_BURST) { compose.onNodeWithText("복구").performClick() }

        assertEquals(0, recoverCount)
    }

    private companion object {
        const val SIZE_BYTES = 433_362_223L

        /** 응답이 없다고 느낀 사용자가 연타하는 횟수. */
        const val TAP_BURST = 5
    }
}
