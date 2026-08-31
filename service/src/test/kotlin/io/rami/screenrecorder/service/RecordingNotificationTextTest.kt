package io.rami.screenrecorder.service

import io.rami.screenrecorder.domain.model.AutoStopReason
import io.rami.screenrecorder.domain.model.RecordingState
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import kotlin.time.Duration.Companion.minutes

/**
 * 진행 알림이 상태마다 무엇을 보여 주는지 (기능명세서 6.1절 [결정]).
 *
 * 발행 구간(Stopping)이 알림에 반영되지 않아 2~4분 동안 "녹화 중 00:57:08" 이 남아 있었다.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "ko")
class RecordingNotificationTextTest {
    private val context get() = RuntimeEnvironment.getApplication()

    @Test
    fun `발행 중에는 저장 중을 보여 준다`() {
        val text = context.ongoingNotificationText(RecordingState.Stopping)

        assertEquals(context.getString(R.string.recording_notification_saving), text)
    }

    @Test
    fun `녹화 중에는 경과 시간을 보여 준다`() {
        val text = context.ongoingNotificationText(RecordingState.Recording(elapsed = 3.minutes))

        assertEquals(context.getString(R.string.recording_notification_elapsed, "03:00"), text)
    }

    /**
     * 완료 문구는 자동 중지 사유가 있으면 그것을, 없으면 수동 중지용 문구를 쓴다
     * (기능명세서 6.1절 [결정]).
     */
    @Test
    fun `자동 중지 사유가 있으면 그 사유를 완료 문구로 쓴다`() {
        val text = context.completedText(AutoStopReason.TIME_LIMIT_REACHED)

        assertEquals(context.getString(R.string.recording_notification_completed_time_limit), text)
    }

    @Test
    fun `수동 중지는 사유 없이 저장 완료만 알린다`() {
        val text = context.completedText(reason = null)

        assertEquals(context.getString(R.string.recording_notification_completed_saved), text)
    }
}
