package io.rami.screenrecorder.service

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PixelFormat
import android.util.TypedValue
import android.view.Gravity
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

/**
 * 화면 전체를 덮는 카운트다운 오버레이 (기능명세서 3절).
 *
 * 플로팅 버블로 다른 앱 위에서 녹화를 시작하면 앱 화면이 떠 있지 않아 Compose 오버레이가 보이지 않는다.
 * 그래서 카운트다운은 시스템 오버레이 창으로 띄운다 — 어느 앱 위에 있든 남은 시간이 보인다.
 *
 * 인코딩은 카운트다운이 끝난 뒤 시작하므로(기능명세서 3절) 이 창은 녹화 영상에 담기지 않는다.
 */
internal class CountdownOverlayWindow(
    private val context: Context,
) {
    private val windowManager = context.getSystemService(WindowManager::class.java)
    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private var root: FrameLayout? = null
    private var secondsView: TextView? = null

    /**
     * 남은 시간을 표시한다. 창이 없으면 만든다. [onSkip]은 화면을 탭했을 때 호출된다.
     *
     * 창 조작은 메인 스레드에서만 가능하므로 호출 스레드와 무관하게 스스로 옮긴다.
     * 오버레이 권한이 없으면 아무 것도 하지 않는다 — 그때는 앱 화면이 카운트다운을 그린다.
     */
    fun show(
        remainingSeconds: Int,
        onSkip: () -> Unit,
    ) {
        if (!android.provider.Settings.canDrawOverlays(context)) return
        mainHandler.post {
            val existing = secondsView
            if (existing != null) {
                existing.text = remainingSeconds.toString()
            } else {
                val container = buildView(remainingSeconds, onSkip)
                root = container
                windowManager.addView(container, overlayLayoutParams())
            }
        }
    }

    /** 오버레이를 닫는다. 떠 있지 않으면 아무 일도 하지 않는다. */
    fun dismiss() {
        mainHandler.post {
            val container = root ?: return@post
            root = null
            secondsView = null
            windowManager.removeView(container)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun buildView(
        remainingSeconds: Int,
        onSkip: () -> Unit,
    ): FrameLayout {
        val seconds =
            TextView(context).apply {
                text = remainingSeconds.toString()
                setTextColor(BUBBLE_FOREGROUND)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, SECONDS_TEXT_SP)
                // 고정폭 숫자(tnum)는 쓰지 않는다: 한 자리 숫자에 고정 advance가 붙어 좌우가 비어 보인다.
                // 매초 바뀌는 여러 자리 타이머(경과 시간)와 달리 여기서는 자리수가 늘지 않는다.
                typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.BOLD)
                includeFontPadding = false
                gravity = Gravity.CENTER
            }
        secondsView = seconds
        val hint =
            TextView(context).apply {
                text = context.getString(R.string.countdown_skip_hint)
                setTextColor(BUBBLE_MUTED)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, HINT_TEXT_SP)
                gravity = Gravity.CENTER
            }
        val column =
            LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                addView(seconds)
                addView(hint)
            }
        return FrameLayout(context).apply {
            setBackgroundColor(SCRIM_COLOR)
            addView(
                column,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    Gravity.CENTER,
                ),
            )
            // 어디를 탭해도 즉시 시작한다 (기능명세서 3절: 탭 = 스킵).
            setOnClickListener { onSkip() }
        }
    }

    private fun overlayLayoutParams(): WindowManager.LayoutParams {
        // MATCH_PARENT는 인셋 영역(하단 제스처 바)을 남겨 딤이 끊긴다 — 디스플레이 크기를 직접 준다.
        val bounds = windowManager.currentWindowMetrics.bounds
        return WindowManager
            .LayoutParams(
                bounds.width(),
                bounds.height(),
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                // 포커스는 가져가지 않되 터치는 받는다(탭=스킵). 상태 바 영역까지 덮어 딤이 끊기지 않게 한다.
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT,
            ).apply { gravity = Gravity.TOP or Gravity.START }
    }

    private companion object {
        /** DESIGN_GUIDE.md 1c: 딤 72%. */
        const val SCRIM_COLOR = 0xB8000000.toInt()
        const val SECONDS_TEXT_SP = 120f
        const val HINT_TEXT_SP = 16f
    }
}
