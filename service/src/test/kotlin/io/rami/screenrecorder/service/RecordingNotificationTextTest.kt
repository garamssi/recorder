package io.rami.screenrecorder.service

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
}
