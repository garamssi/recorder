package io.rami.screenrecorder.service

import android.app.Activity
import android.os.Looper
import android.os.SystemClock
import android.view.MotionEvent
import io.rami.screenrecorder.domain.model.TimeLimit
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * 시간 제한 입력 창은 버튼으로만 닫힌다 (기능명세서 11.4절 [결정]).
 *
 * 증감 버튼은 작고 위아래로 틈이 있어 빠르게 누르다 보면 손끝이 카드 위 빈 자리로
 * 벗어난다. 키보드가 올라오면 카드가 위로 밀려 방금 누른 자리가 카드 바깥이 되기도 한다.
 * 바깥 탭을 닫기로 쓰는 한 값을 고치던 중에 창이 사라지므로, 딤은 터치를 삼키기만 한다.
 *
 * 딤을 실제 창에 붙여야 한다 — 붙지 않은 뷰는 탭을 자기 대기열에 쌓아 두기만 해서
 * 어떤 탭도 도달하지 않고, 무엇을 해도 통과하는 테스트가 된다.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "ko-w1280dp-h800dp")
class TimeLimitScrimTest {
    private val activity = Robolectric.buildActivity(Activity::class.java).setup().get()

    private var cancelled = false

    private val card =
        activity
            .buildTimeLimitInput(current = TimeLimit.None, onConfirm = {}, onDismiss = {})
            .root

    private val scrim =
        activity.timeLimitScrim(card, onCancel = { cancelled = true }).also { scrim ->
            activity.setContentView(scrim)
            settle()
        }

    /** 카드 왼쪽 위 모서리 안쪽 — 여백이라 아무 자식도 받지 않는 자리. */
    private val cardCorner get() = (card.left + 1f) to (card.top + 1f)

    /** 카드 바깥 — 딤만 있는 자리. */
    private val outside = 1f to 1f

    /** 배치와 클릭 전달은 메인 루퍼를 거친다. Robolectric 의 루퍼는 멈춘 채로 시작한다. */
    private fun settle() = shadowOf(Looper.getMainLooper()).idle()

    /** 터치 하나를 딤에 넣고, 소비했는지 돌려준다. */
    private fun touch(
        action: Int,
        point: Pair<Float, Float>,
    ): Boolean {
        val now = SystemClock.uptimeMillis()
        val event = MotionEvent.obtain(now, now, action, point.first, point.second, 0)
        val consumed = scrim.dispatchTouchEvent(event)
        event.recycle()
        settle()
        return consumed
    }

    private fun tap(point: Pair<Float, Float>) {
        touch(MotionEvent.ACTION_DOWN, point)
        touch(MotionEvent.ACTION_UP, point)
    }

    @Test
    fun `카드 바깥을 눌러도 닫히지 않는다`() {
        tap(outside)

        assertFalse(cancelled)
    }

    @Test
    fun `카드 바깥을 빠르게 여러 번 눌러도 닫히지 않는다`() {
        repeat(TAP_BURST) { tap(outside) }

        assertFalse(cancelled)
    }

    @Test
    fun `카드 위 빈 자리를 눌러도 닫히지 않는다`() {
        tap(cardCorner)

        assertFalse(cancelled)
    }

    @Test
    fun `카드 위를 빠르게 여러 번 눌러도 닫히지 않는다`() {
        repeat(TAP_BURST) { tap(cardCorner) }

        assertFalse(cancelled)
    }

    @Test
    fun `카드 안에서 눌러 바깥에서 떼도 닫히지 않는다`() {
        touch(MotionEvent.ACTION_DOWN, cardCorner)
        touch(MotionEvent.ACTION_UP, outside)

        assertFalse(cancelled)
    }

    @Test
    fun `딤은 터치를 아래 창으로 흘려보내지 않는다`() {
        assertTrue(touch(MotionEvent.ACTION_DOWN, outside))
        assertTrue(touch(MotionEvent.ACTION_DOWN, cardCorner))
    }

    private companion object {
        /** 연타로 봐줄 만한 횟수. 한 번으로 안 닫히는데 여러 번으로 닫히면 그것도 오조작이다. */
        const val TAP_BURST = 5
    }
}
