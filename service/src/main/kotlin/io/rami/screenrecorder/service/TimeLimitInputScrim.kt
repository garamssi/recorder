package io.rami.screenrecorder.service

import android.content.Context
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsets
import android.widget.FrameLayout

// 시간 제한 입력 카드를 화면 가운데 놓고 바깥을 덮는 딤 (기능명세서 11.4절).
// 창의 생명주기는 TimeLimitInputWindow.kt, 카드 자체는 TimeLimitInputViewParts.kt 참조.

/**
 * [card]를 가운데 놓고 바깥을 딤으로 덮는 뷰를 만든다.
 *
 * 딤은 터치를 삼키기만 하고 창을 닫지 않는다 (기능명세서 11.4절 [결정]). 카드 위에는
 * 제목·안내 문구·단위 라벨·여백·증감 버튼과 입력 칸 사이 틈처럼 아무 자식도 터치를 받지
 * 않는 빈 자리가 넓고, 키보드가 올라오면 카드가 위로 밀려 방금 누른 자리가 카드 바깥이
 * 되기도 한다. 바깥 탭을 닫기로 쓰는 한 값을 고치던 중에 창이 사라진다.
 *
 * @param onCancel 뒤로 가기로 값을 바꾸지 않고 닫을 때. 카드의 "취소"와 같다.
 */
internal fun Context.timeLimitScrim(
    card: View,
    onCancel: () -> Unit,
): FrameLayout =
    object : FrameLayout(this) {
        override fun dispatchKeyEvent(event: KeyEvent): Boolean {
            if (event.keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
                onCancel()
                return true
            }
            return super.dispatchKeyEvent(event)
        }

        // 자식이 소비하지 않은 터치가 여기까지 온다. 삼키기만 해서 아래 앱으로 새어 나가지
        // 않게 한다 — 딤 너머가 눌리면 녹화 중인 화면이 엉뚱하게 조작된다.
        override fun onTouchEvent(event: MotionEvent): Boolean = true
    }.apply {
        setBackgroundColor(SCRIM_COLOR)
        isFocusableInTouchMode = true
        liftAboveKeyboard()
        addView(
            card,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER,
            ),
        )
    }

/**
 * 소프트 키보드가 올라온 만큼 아래 여백을 줘서 카드가 가려지지 않게 한다.
 *
 * SOFT_INPUT_ADJUST_RESIZE는 API 30부터 폐기됐고, WindowManager에 직접 붙인 창에는
 * `Window`가 없어 WindowCompat도 쓸 수 없다. 남은 방법은 IME 인셋을 직접 반영하는 것이다.
 */
private fun FrameLayout.liftAboveKeyboard() {
    setOnApplyWindowInsetsListener { view, insets ->
        val ime = insets.getInsets(WindowInsets.Type.ime())
        view.setPadding(0, 0, 0, ime.bottom)
        insets
    }
}

/** DESIGN_GUIDE.md 1c: 딤 72%. */
private const val SCRIM_COLOR = 0xB8000000.toInt()
