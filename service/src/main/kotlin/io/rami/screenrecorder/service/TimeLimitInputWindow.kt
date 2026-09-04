package io.rami.screenrecorder.service

import android.content.Context
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import android.widget.FrameLayout
import io.rami.screenrecorder.domain.model.TimeLimit

/**
 * 버블에서 여는 녹화 시간 제한 입력 오버레이 (기능명세서 11.4절).
 *
 * 버블 자체는 FLAG_NOT_FOCUSABLE이라 키 입력을 받지 못한다. 시/분/초를 타이핑하려면
 * 소프트 키보드가 붙어야 하므로 입력 창만 포커스를 가져가는 별도 창으로 띄운다.
 *
 * 카드를 덮는 딤과 닫기 판정은 TimeLimitInputScrim.kt 가 갖는다.
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
            val container = context.timeLimitScrim(views.root, onCancel = ::dismissNow)
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

    /**
     * 화면 전체를 덮는 입력 창의 파라미터.
     *
     * 크기를 픽셀로 못 박지 않는다. 띄울 때의 화면 크기를 박아 두면 회전한 뒤 딤이 화면을
     * 다 덮지 못해, 남는 자리로 아래 앱이 눌리고 바깥 탭 취소도 그 자리에서는 먹지 않는다.
     */
    private fun overlayLayoutParams(): WindowManager.LayoutParams =
        WindowManager
            .LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
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
