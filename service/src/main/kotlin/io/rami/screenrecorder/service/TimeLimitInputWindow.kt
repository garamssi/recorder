package io.rami.screenrecorder.service

import android.content.Context
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.KeyEvent
import android.view.WindowInsets
import android.view.WindowManager
import android.widget.FrameLayout
import io.rami.screenrecorder.domain.model.TimeLimit

/**
 * 버블에서 여는 녹화 시간 제한 입력 오버레이 (기능명세서 11.4절).
 *
 * 버블 자체는 FLAG_NOT_FOCUSABLE이라 키 입력을 받지 못한다. 시/분/초를 타이핑하려면
 * 소프트 키보드가 붙어야 하므로 입력 창만 포커스를 가져가는 별도 창으로 띄운다.
 */
internal class TimeLimitInputWindow(
    private val context: Context,
) {
    private val windowManager = context.getSystemService(WindowManager::class.java)
    private val mainHandler = Handler(Looper.getMainLooper())
    private var root: FrameLayout? = null

    /**
     * 입력 창을 띄운다. 이미 떠 있으면 무시한다.
     *
     * 창 조작은 메인 스레드에서만 가능하므로 호출 스레드와 무관하게 스스로 옮긴다.
     * 오버레이 권한이 없으면 버블도 떠 있을 수 없으므로 아무 일도 하지 않는다.
     *
     * @param current 미리 채울 현재 설정값.
     * @param onConfirm 사용자가 확정한 값. 창은 호출 전에 닫힌다.
     */
    fun show(
        current: TimeLimit,
        onConfirm: (TimeLimit) -> Unit,
    ) {
        if (!Settings.canDrawOverlays(context)) return
        mainHandler.post {
            if (root != null) return@post
            val views =
                context.buildTimeLimitInput(
                    current = current,
                    onConfirm = { limit ->
                        dismissNow()
                        onConfirm(limit)
                    },
                    onDismiss = ::dismissNow,
                )
            val container = scrimContainer(views.root)
            root = container
            windowManager.addView(container, overlayLayoutParams())
        }
    }

    /** 입력 창을 닫는다. 떠 있지 않으면 아무 일도 하지 않는다. */
    fun dismiss() {
        mainHandler.post(::dismissNow)
    }

    private fun dismissNow() {
        val container = root ?: return
        root = null
        windowManager.removeView(container)
    }

    /** 카드를 가운데 놓고 바깥을 딤으로 덮는다. 바깥 탭과 뒤로 가기는 취소로 본다. */
    private fun scrimContainer(card: android.view.View): FrameLayout =
        object : FrameLayout(context) {
            override fun dispatchKeyEvent(event: KeyEvent): Boolean {
                if (event.keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
                    dismissNow()
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
            setOnClickListener { dismissNow() }
        }

    private fun overlayLayoutParams(): WindowManager.LayoutParams {
        val bounds = windowManager.currentWindowMetrics.bounds
        return WindowManager
            .LayoutParams(
                bounds.width(),
                bounds.height(),
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                // FLAG_NOT_FOCUSABLE을 주면 소프트 키보드가 이 창에 붙지 않아 숫자를 입력할 수 없다.
                // FLAG_WATCH_OUTSIDE_TOUCH 대신 딤 뷰가 화면 전체를 덮어 바깥 탭을 직접 받는다.
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT,
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                softInputMode = WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE
            }
    }

    private companion object {
        /** DESIGN_GUIDE.md 1c: 딤 72%. */
        const val SCRIM_COLOR = 0xB8000000.toInt()
    }
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
