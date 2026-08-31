package io.rami.screenrecorder.service

import android.content.Context
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.WindowInsets
import android.widget.FrameLayout

// 시간 제한 입력 카드를 화면 가운데 놓고 바깥을 덮는 딤 (기능명세서 11.4절).
// 창의 생명주기는 TimeLimitInputWindow.kt, 카드 자체는 TimeLimitInputViewParts.kt 참조.

/**
 * [card]를 가운데 놓고 바깥을 딤으로 덮는 뷰를 만든다.
 *
 * 오버레이 창에는 `Dialog`가 없어 "바깥을 눌러 닫기"를 스스로 판정해야 한다.
 *
 * @param onCancel 바깥 탭 또는 뒤로 가기로 값을 바꾸지 않고 닫을 때.
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
        setOnClickListener { onCancel() }
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
