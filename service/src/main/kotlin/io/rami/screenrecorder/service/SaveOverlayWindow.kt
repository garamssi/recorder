package io.rami.screenrecorder.service

import android.content.Context
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Toast

/**
 * 발행 구간을 화면 위에 보여 주는 표시 (기능명세서 6.1절 [결정]).
 *
 * 서비스는 "언제" 를 알고 "어디에 어떻게" 는 구현이 정한다. 시점 검증이 창을 띄우지 않고도
 * 되도록 경계를 둔다.
 */
interface SaveOverlay {
    /**
     * 발행이 진행 중임을 보여 준다. 이미 떠 있으면 값만 갱신한다.
     *
     * @param progress 0f..1f. 아직 모르면 null.
     */
    fun showSaving(
        elapsed: String,
        fileName: String,
        progress: Float?,
    )

    /** 발행이 확정됐음을 보여 주고 잠시 뒤 스스로 접힌다. */
    fun showSaved(
        elapsed: String,
        fileName: String,
    )

    /** 표시를 즉시 내린다. 새 세션이 시작되면 지난 발행이 남아 있어서는 안 된다. */
    fun dismiss()
}

/**
 * 화면 위에 뜨는 저장 오버레이 (DESIGN_GUIDE.md 4절 "저장 오버레이").
 *
 * 카운트다운과 같은 시스템 오버레이 창이다 — 녹화가 끝나는 순간 사용자는 다른 앱을 보고 있고
 * 알림 그림자는 접혀 있다. 플로팅 버블과 별개인 이유: 버블을 끈 사용자도 녹화는 하고, 녹화가
 * 끝난 사실을 아는 것이 설정에 달려 있어서는 안 된다.
 *
 * 창의 수명은 이 객체를 만든 서비스보다 길다. 발행이 끝나면 서비스는 곧 접히므로, 사라지는
 * 시점을 코루틴 스코프가 아니라 메인 스레드 핸들러가 쥔다. 애플리케이션 컨텍스트로 만든다.
 */
internal class SaveOverlayWindow(
    private val context: Context,
    private val windows: OverlayWindows = SystemOverlayWindows(context),
) : SaveOverlay {
    private val mainHandler = Handler(Looper.getMainLooper())
    private var card: SaveOverlayCard? = null

    /** 완료 표시를 지우려고 예약해 둔 일. 새 발행이 시작되면 거둔다. */
    private var pendingRemoval: Runnable? = null

    override fun showSaving(
        elapsed: String,
        fileName: String,
        progress: Float?,
    ) {
        // 진행 구간에는 권한이 없어도 대체하지 않는다. 알림이 이미 퍼센트를 알리고 있고,
        // 발행 2~4분 동안 토스트를 반복해 띄우면 화면을 가린다.
        if (!Settings.canDrawOverlays(context)) return
        render(SaveOverlayContent(elapsed, fileName, progress, done = false))
    }

    override fun showSaved(
        elapsed: String,
        fileName: String,
    ) {
        if (!Settings.canDrawOverlays(context)) {
            fallBackToToast(fileName)
            return
        }
        render(SaveOverlayContent(elapsed, fileName, progress = 1f, done = true)) {
            val removal = Runnable { removeExisting() }
            pendingRemoval = removal
            mainHandler.postDelayed(removal, SAVE_OVERLAY_DISPLAY_MILLIS)
        }
    }

    override fun dismiss() {
        mainHandler.post(::removeExisting)
    }

    /**
     * 카드를 [content] 로 맞춘다. 창이 없으면 붙이고, 붙이지 못하면 [onAttached] 를 건너뛴다.
     *
     * 이미 떠 있으면 값만 갱신한다 — 진행률은 0.5% 단위로 올라와 발행 내내 수백 번 갱신되는데,
     * 그때마다 창을 다시 붙이면 링 애니메이션이 매번 처음부터 시작한다.
     */
    private fun render(
        content: SaveOverlayContent,
        onAttached: () -> Unit = {},
    ) {
        mainHandler.post {
            // 앞 발행의 완료 예약이 남아 있으면 거둔다. 그대로 두면 새 표시를 지운다.
            pendingRemoval?.let(mainHandler::removeCallbacks)
            pendingRemoval = null
            val existing = card
            if (existing != null) {
                existing.render(content)
                onAttached()
                return@post
            }
            val fresh = SaveOverlayCard(context)
            fresh.render(content)
            // 권한 검사와 실제 붙이기는 스레드가 달라 그 사이 권한이 사라질 수 있다.
            if (!windows.attach(fresh.root, saveOverlayLayoutParams(context.dpToPx(TOP_OFFSET_DP)))) {
                fallBackToToast(content.fileName)
                return@post
            }
            card = fresh
            onAttached()
        }
    }

    /** 화면에 그릴 수단이 토스트뿐인 경우 (기능명세서 6.1절 [결정]). */
    private fun fallBackToToast(fileName: String) {
        mainHandler.post {
            val text = context.getString(R.string.save_complete_toast, fileName)
            Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
        }
    }

    private fun removeExisting() {
        pendingRemoval?.let(mainHandler::removeCallbacks)
        pendingRemoval = null
        val attached = card ?: return
        card = null
        windows.detach(attached.root)
    }

    private companion object {
        const val TOP_OFFSET_DP = 24f
    }
}

/** 완료 표시가 머무는 시간. 눈에 걸릴 만큼 길고, 화면을 오래 가리지 않을 만큼 짧다. */
internal const val SAVE_OVERLAY_DISPLAY_MILLIS = 3_000L

/**
 * 오버레이 창의 배치 (DESIGN_GUIDE.md 4절).
 *
 * 상단 중앙에 띄운다 — 하단은 제스처 바와 플로팅 버블이 차지한다.
 */
internal fun saveOverlayLayoutParams(topOffsetPx: Int): WindowManager.LayoutParams =
    WindowManager
        .LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            // 누를 것이 없으므로 터치를 받지 않는다 — 카드 아래의 앱이 그대로 눌린다.
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = topOffsetPx
        }

/**
 * 오버레이 창 조작 (플랫폼 경계).
 *
 * 창을 붙이지 못하는 상황은 테스트에서 만들 수 없으므로 얇은 경계로 빼 둔다
 * (CLAUDE.md 5절: 플랫폼 API 는 얇은 어댑터로 격리한다).
 */
internal interface OverlayWindows {
    /** 창을 붙인다. 시스템이 거부하면 false. */
    fun attach(
        view: View,
        params: WindowManager.LayoutParams,
    ): Boolean

    /** 창을 뗀다. 이미 떨어져 있으면 조용히 지나간다. */
    fun detach(view: View)
}

/** [WindowManager] 를 쓰는 실제 구현. */
internal class SystemOverlayWindows(
    context: Context,
) : OverlayWindows {
    private val windowManager = context.getSystemService(WindowManager::class.java)

    /**
     * 권한 검사와 실제 붙이기는 스레드가 다르고, 일부 기기는 검사가 통과해도 창을 거부한다.
     * 붙이지 못하는 것은 앱이 고칠 수 있는 상태가 아니므로 표시를 포기하되, 저장을 알리려다
     * 앱을 죽이지는 않는다.
     */
    override fun attach(
        view: View,
        params: WindowManager.LayoutParams,
    ): Boolean =
        try {
            windowManager.addView(view, params)
            true
        } catch (denied: WindowManager.BadTokenException) {
            Log.w(LOG_TAG, "저장 오버레이를 띄우지 못했다 — 오버레이 권한이 없다", denied)
            false
        } catch (gone: WindowManager.InvalidDisplayException) {
            Log.w(LOG_TAG, "저장 오버레이를 띄우지 못했다 — 디스플레이가 사라졌다", gone)
            false
        }

    /** 시스템이 이미 떼어 간 창이면 조용히 지나간다 (권한 회수 등). */
    override fun detach(view: View) {
        try {
            windowManager.removeView(view)
        } catch (alreadyGone: IllegalArgumentException) {
            Log.w(LOG_TAG, "저장 오버레이가 이미 창에서 떨어져 있다", alreadyGone)
        }
    }

    private companion object {
        const val LOG_TAG = "SaveOverlay"
    }
}
