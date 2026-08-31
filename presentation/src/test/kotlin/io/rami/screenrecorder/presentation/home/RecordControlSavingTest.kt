package io.rami.screenrecorder.presentation.home

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import io.rami.screenrecorder.domain.model.Recording
import io.rami.screenrecorder.domain.model.RecordingId
import io.rami.screenrecorder.domain.model.RecordingState
import io.rami.screenrecorder.domain.model.Resolution
import io.rami.screenrecorder.domain.model.VideoCodec
import io.rami.screenrecorder.presentation.navigation.RecordingControlActions
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * 저장 중·저장 완료 국면의 녹화 제어 카드 (기능명세서 2.1절 [결정], DESIGN_GUIDE.md 4절 "홈").
 *
 * 이 상태는 오랫동안 텍스트 한 줄이었다. 카드가 220dp 에서 40dp 로 접혔다가 저장이 끝나면
 * 다시 튀어 올라, 아래에 쌓인 카드들이 통째로 출렁였다. 발행은 분 단위로 걸리므로 그 동안
 * 진행 표시도 없었다.
 */
@RunWith(RobolectricTestRunner::class)
// 대상 기기는 태블릿이다 (CLAUDE.md 1절). 기본 폰 창이면 카드가 잘려 "표시되지 않음"이 된다.
@Config(sdk = [34], qualifiers = "ko-w1280dp-h800dp")
class RecordControlSavingTest {
    @get:Rule
    val compose = createComposeRule()

    private val actions =
        HomeActions(
            control = RecordingControlActions(onStart = {}, onStop = {}, onPause = {}, onResume = {}),
            onOpenLibrary = {},
        )

    private var state by mutableStateOf<RecordingState>(RecordingState.Idle)
    private var justSaved by mutableStateOf<Recording?>(null)

    private fun show(
        initial: RecordingState = RecordingState.Idle,
        canStart: Boolean = true,
    ) {
        state = initial
        compose.setContent {
            RecordControlCard(
                uiState = HomeUiState(recordingState = state, canStartRecording = canStart),
                actions = actions,
                justSaved = justSaved,
            )
        }
    }

    /**
     * 상태를 바꾸고 화면까지 전파시킨다.
     *
     * 저장 중 국면에는 끝나지 않는 애니메이션(글로우 맥동, 역회전 원호)이 있지만 시계를
     * 멈출 필요는 없다 — Compose 테스트 환경이 무한 애니메이션을 취소해 유휴를 막지 않는다.
     * 시계를 멈추면 오히려 스냅샷 적용 통지가 프레임 뒤로 밀려 변경이 화면에 닿지 않는다.
     */
    private fun change(mutate: () -> Unit) {
        compose.runOnIdle(mutate)
        compose.waitForIdle()
    }

    private fun rootHeight(): Float {
        val bounds = compose.onRoot().getUnclippedBoundsInRoot()
        return (bounds.bottom - bounds.top).value
    }

    @Test
    fun `저장 중에는 녹화 길이와 진행률을 함께 보여 준다`() {
        show(RecordingState.Stopping(RECORDED, FILE_NAME, progress = 0.62f))

        compose.onNodeWithText("03:42", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("62%", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("저장 중…").assertIsDisplayed()
        compose.onNodeWithText(FILE_NAME).assertIsDisplayed()
    }

    @Test
    fun `진행률을 아직 모르면 퍼센트를 감춘다`() {
        show(RecordingState.Stopping(RECORDED, FILE_NAME, progress = null))

        compose.onNodeWithText("저장 중…").assertIsDisplayed()
        // 길이는 그대로 보여 주되 퍼센트만 감춘다 — 링이 통째로 사라지면 안 된다.
        compose.onNodeWithText("03:42", useUnmergedTree = true).assertIsDisplayed()
        compose.onAllNodesWithText("%", substring = true, useUnmergedTree = true).assertCountEquals(0)
    }

    @Test
    fun `저장 중 카드는 녹화 중과 같은 높이를 쓴다`() {
        show(RecordingState.Recording(RECORDED))
        val recordingHeight = rootHeight()

        change { state = RecordingState.Stopping(RECORDED, FILE_NAME, progress = 0.4f) }

        // 전환이 실제로 반영됐음을 먼저 확인한다 — 아니면 같은 화면을 두 번 재는 것이다.
        compose.onNodeWithText("저장 중…").assertIsDisplayed()
        assertEquals(recordingHeight, rootHeight(), HEIGHT_TOLERANCE)
    }

    @Test
    fun `저장 중 카드는 대기 상태와도 같은 높이를 쓴다`() {
        show(RecordingState.Idle)
        val idleHeight = rootHeight()

        change { state = RecordingState.Stopping(RECORDED, FILE_NAME, progress = 0.4f) }

        // 전환이 실제로 반영됐음을 먼저 확인한다 — 아니면 같은 화면을 두 번 재는 것이다.
        compose.onNodeWithText("저장 중…").assertIsDisplayed()
        assertEquals(idleHeight, rootHeight(), HEIGHT_TOLERANCE)
    }

    /** 저장 공간이 부족하면 대기 상태에 경고 한 줄이 더 붙는다 — 그래도 튀지 않아야 한다. */
    @Test
    fun `저장 공간 경고가 붙은 대기 상태와도 같은 높이를 쓴다`() {
        show(RecordingState.Idle, canStart = false)
        val warnedHeight = rootHeight()

        change { state = RecordingState.Stopping(RECORDED, FILE_NAME, progress = 0.4f) }

        // 전환이 실제로 반영됐음을 먼저 확인한다 — 아니면 같은 화면을 두 번 재는 것이다.
        compose.onNodeWithText("저장 중…").assertIsDisplayed()
        assertEquals(warnedHeight, rootHeight(), HEIGHT_TOLERANCE)
    }

    /**
     * 저장 완료 표시 (DESIGN_GUIDE.md 4절 "저장 완료").
     *
     * 완료는 세션 상태 전이가 아니라 발행이 확정된 녹화본으로만 판정한다 — 발행 실패와
     * 빈 세션도 `Stopping -> Idle` 전이를 만들기 때문이다. 붙드는 시간은 ViewModel 이 센다.
     */
    @Test
    fun `발행된 녹화본이 있으면 완료 표시를 보여 준다`() {
        show(RecordingState.Idle)

        change { justSaved = SAVED_RECORDING }

        compose.onNodeWithText("저장했습니다").assertIsDisplayed()
        compose.onNodeWithText(FILE_NAME).assertIsDisplayed()
    }

    @Test
    fun `완료 표시가 사라지면 시작 버튼으로 돌아간다`() {
        show(RecordingState.Idle)
        change { justSaved = SAVED_RECORDING }
        compose.onNodeWithText("저장했습니다").assertIsDisplayed()

        change { justSaved = null }

        compose.onNodeWithText("저장했습니다").assertDoesNotExist()
        compose.onNodeWithText("녹화 시작").assertIsDisplayed()
    }

    /** 완료 표시가 다음 녹화를 막아서는 안 된다. */
    @Test
    fun `완료 표시 중에 새 세션이 시작되면 즉시 접는다`() {
        show(RecordingState.Idle)
        change { justSaved = SAVED_RECORDING }
        compose.onNodeWithText("저장했습니다").assertIsDisplayed()

        change { state = RecordingState.Recording(RECORDED) }

        compose.onNodeWithText("저장했습니다").assertDoesNotExist()
    }

    private companion object {
        val RECORDED = 3.minutes + 42.seconds
        const val FILE_NAME = "ScreenRecorder_20260831_143020.mp4"

        /** dp 반올림 차이만 허용한다 — 이 값을 넘으면 전환에 레이아웃이 튄다. */
        const val HEIGHT_TOLERANCE = 1f

        val SAVED_RECORDING =
            Recording(
                id = RecordingId(1L),
                displayName = FILE_NAME,
                contentUri = "content://media/external/video/media/1",
                sizeBytes = 12_345L,
                duration = 3.minutes + 42.seconds,
                resolution = Resolution(1920, 1080),
                frameRate = 60,
                codec = VideoCodec.H264,
                createdAtEpochMillis = 1_788_155_923_000L,
                bitrateBps = 12_000_000,
            )
    }
}
