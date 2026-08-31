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
 * 시간 제한 입력 창이 언제 닫히는지 (기능명세서 11.4절).
 *
 * 카드 위에는 아무 뷰도 터치를 받지 않는 빈 자리가 넓다 — 제목, 안내 문구, 단위 라벨,
 * 카드 여백, 증감 버튼과 입력 칸 사이 틈. 그 빈 자리를 바깥 탭으로 세면 값을 고치던
 * 중에 창이 사라져 설정 자체가 되지 않는다. 좌표로 판정하는지를 여기서 고정한다.
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
    fun `카드 위 빈 자리를 눌러도 닫히지 않는다`() {
        tap(cardCorner)

        assertFalse(cancelled)
    }

    @Test
    fun `카드 위를 빠르게 두 번 눌러도 닫히지 않는다`() {
        tap(cardCorner)
        tap(cardCorner)

        assertFalse(cancelled)
    }

    @Test
    fun `카드 안에서 눌러 바깥에서 떼면 닫히지 않는다`() {
        touch(MotionEvent.ACTION_DOWN, cardCorner)
        touch(MotionEvent.ACTION_UP, outside)

        assertFalse(cancelled)
    }

    @Test
    fun `카드 바깥에서 눌러 카드 위에서 떼면 닫히지 않는다`() {
        touch(MotionEvent.ACTION_DOWN, outside)
        touch(MotionEvent.ACTION_UP, cardCorner)

        assertFalse(cancelled)
    }

    @Test
    fun `카드 바깥을 누르면 닫힌다`() {
        tap(outside)

        assertTrue(cancelled)
    }

    @Test
    fun `카드 위 빈 자리를 눌러도 아래 창으로 흘려보내지 않는다`() {
        assertTrue(touch(MotionEvent.ACTION_DOWN, cardCorner))
    }
}
