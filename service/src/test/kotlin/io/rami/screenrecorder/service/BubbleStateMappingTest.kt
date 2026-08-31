package io.rami.screenrecorder.service

import io.rami.screenrecorder.domain.model.RecordingState
import io.rami.screenrecorder.domain.model.TimeLimit
import io.rami.screenrecorder.domain.model.VoiceRecordingState
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * 캡처 상태를 버블 모양으로 옮기는 규칙 (기능명세서 11.1·11.4절).
 *
 * 시간 제한이 걸린 세션은 남은 시간을 가늠할 수 있어야 하므로 경과 시간과 제한을 병기한다.
 */
class BubbleStateMappingTest {
    private val noVoice = VoiceRecordingState.Idle

    @Test
    fun `시간 제한이 있으면 경과 시간과 제한을 병기한다`() {
        val state =
            bubbleStateFor(
                screen = RecordingState.Recording(3.minutes + 24.seconds, TimeLimit.Limited(10.minutes)),
                voice = noVoice,
                settingTimeLimit = TimeLimit.None,
            )

        assertEquals(BubbleState.ScreenRecording("03:24 / 10:00", isPaused = false), state)
    }

    @Test
    fun `제한이 없으면 경과 시간만 보여준다`() {
        val state =
            bubbleStateFor(
                screen = RecordingState.Recording(3.minutes + 24.seconds, TimeLimit.None),
                voice = noVoice,
                settingTimeLimit = TimeLimit.None,
            )

        assertEquals(BubbleState.ScreenRecording("03:24", isPaused = false), state)
    }

    @Test
    fun `일시정지 중에도 제한을 함께 보여준다`() {
        val state =
            bubbleStateFor(
                screen = RecordingState.Paused(1.minutes, TimeLimit.Limited(10.minutes)),
                voice = noVoice,
                settingTimeLimit = TimeLimit.None,
            )

        assertEquals(BubbleState.ScreenRecording("01:00 / 10:00", isPaused = true), state)
    }

    @Test
    fun `녹화 중에는 설정을 바꿔도 세션이 시작한 제한을 보여준다`() {
        val state =
            bubbleStateFor(
                screen = RecordingState.Recording(1.minutes, TimeLimit.Limited(10.minutes)),
                voice = noVoice,
                settingTimeLimit = TimeLimit.Limited(30.minutes),
            )

        assertEquals(
            BubbleState.ScreenRecording("01:00 / 10:00", isPaused = false),
            state,
        ) { "세션을 멈출 시각은 시작할 때 정해진다 — 설정을 따라가면 남은 시간을 잘못 알려 준다" }
    }

    @Test
    fun `음성 녹음에는 시간 제한이 없다`() {
        val state =
            bubbleStateFor(
                screen = RecordingState.Idle,
                voice = VoiceRecordingState.Recording(30.seconds),
                settingTimeLimit = TimeLimit.Limited(10.minutes),
            )

        assertEquals(BubbleState.VoiceRecording("00:30"), state)
    }

    @Test
    fun `유휴 상태에는 설정된 제한을 그대로 넘긴다`() {
        val state =
            bubbleStateFor(
                screen = RecordingState.Idle,
                voice = noVoice,
                settingTimeLimit = TimeLimit.Limited(10.minutes),
            )

        assertEquals(BubbleState.Idle(TimeLimit.Limited(10.minutes)), state)
    }

    /**
     * 발행 중에 유휴 메뉴를 띄우면 안 된다 (기능명세서 6.1절 [결정]).
     *
     * "녹화 시작"을 누르면 MediaProjection 동의 다이얼로그까지 소비하고, 서비스는
     * 진행 중인 세션 때문에 START 를 조용히 무시한다. 동의만 받고 아무 일도 안 일어난다.
     */
    @Test
    fun `발행 중에는 유휴 메뉴 대신 저장 중을 보여 준다`() {
        val state =
            bubbleStateFor(
                screen = RecordingState.Stopping,
                voice = noVoice,
                settingTimeLimit = TimeLimit.None,
            )

        assertEquals(BubbleState.Saving, state)
    }
}
