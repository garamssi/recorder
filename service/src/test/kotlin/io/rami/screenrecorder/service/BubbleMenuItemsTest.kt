package io.rami.screenrecorder.service

import android.content.Context
import io.rami.screenrecorder.domain.model.TimeLimit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import kotlin.time.Duration.Companion.minutes

/**
 * 플로팅 버블 펼침 메뉴의 구성 (기능명세서 11.1·11.4절).
 *
 * 시간 제한은 녹화를 시작하기 전에만 바꿀 수 있다 — 녹화 중 해제·연장은 1차 범위에서 제외다.
 */
@RunWith(RobolectricTestRunner::class)
// 라벨 문구를 검증하므로 한국어 리소스로 고정한다 (기본 언어, 기능명세서 4.5절).
@Config(sdk = [34], qualifiers = "ko")
class BubbleMenuItemsTest {
    private val context: Context get() = RuntimeEnvironment.getApplication()

    private val recordedActions = mutableListOf<String>()

    private val actions =
        object : BubbleActions {
            override fun onStartRecording() = record("start")

            override fun onStopRecording() = record("stop")

            override fun onPauseRecording() = record("pause")

            override fun onResumeRecording() = record("resume")

            override fun onCaptureScreenshot() = record("screenshot")

            override fun onStartVoiceRecording() = record("voice")

            override fun onStopVoiceRecording() = record("voiceStop")

            override fun onEditTimeLimit() = record("timeLimit")

            override fun onOpenApp() = record("openApp")

            private fun record(name: String) {
                recordedActions += name
            }
        }

    private fun idleMenu(timeLimit: TimeLimit) = context.menuItemsFor(BubbleState.Idle(timeLimit), actions)

    private fun timeLimitLabelOf(timeLimit: TimeLimit) =
        idleMenu(timeLimit).single { it.iconRes == R.drawable.ic_bubble_time_limit }.label

    @Test
    fun `유휴 메뉴에 시간 제한 줄이 있다`() {
        val icons = idleMenu(TimeLimit.None).map { it.iconRes }

        assertTrue(
            "버블에서 시간 제한을 걸 수 없으면 앱으로 돌아가야 한다",
            R.drawable.ic_bubble_time_limit in icons,
        )
    }

    @Test
    fun `시간 제한 줄은 현재 설정값을 함께 보여준다`() {
        assertEquals("시간 제한 · 10:00", timeLimitLabelOf(TimeLimit.Limited(10.minutes)))
    }

    @Test
    fun `제한이 없으면 제한 없음으로 보여준다`() {
        assertEquals("시간 제한 · 제한 없음", timeLimitLabelOf(TimeLimit.None))
    }

    @Test
    fun `시간 제한 줄을 누르면 입력 창을 연다`() {
        idleMenu(TimeLimit.None).single { it.iconRes == R.drawable.ic_bubble_time_limit }.onClick()

        assertEquals(listOf("timeLimit"), recordedActions)
    }

    @Test
    fun `녹화 중 메뉴에는 시간 제한이 없다`() {
        val icons =
            context
                .menuItemsFor(
                    BubbleState.ScreenRecording(elapsed = "00:10", isPaused = false),
                    actions,
                ).map { it.iconRes }

        assertFalse(
            "녹화 중 시간 제한 변경은 1차 범위 제외다 (기능명세서 11.4절)",
            R.drawable.ic_bubble_time_limit in icons,
        )
    }
}
