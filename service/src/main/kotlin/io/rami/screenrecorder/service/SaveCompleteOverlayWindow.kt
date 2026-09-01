package io.rami.screenrecorder.service

import android.content.Context
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

/**
 * 발행이 확정됐음을 화면 위에 알리는 표시 (기능명세서 6.1절 [결정]).
 *
 * 서비스는 "언제" 를 알고 "어디에 어떻게" 는 구현이 정한다. 시점 검증이 창을 띄우지 않고도
 * 되도록 경계를 둔다.
 */
internal interface SaveCompleteBanner {
    /** [fileName] 이 저장됐음을 잠깐 보여 준다. */
    fun show(fileName: String)

    /** 표시를 즉시 내린다. 새 세션이 시작되면 지난 완료가 남아 있어서는 안 된다. */
    fun dismiss()
}

/**
 * 화면 위에 뜨는 저장 완료 배너 (DESIGN_GUIDE.md 4절 "저장 완료 배너").
 *
 * 카운트다운과 같은 시스템 오버레이 창이다 — 녹화가 끝나는 순간 사용자는 다른 앱을 보고 있고
 * 알림 그림자는 접혀 있다. 플로팅 버블과 별개인 이유: 버블을 끈 사용자도 녹화는 하고, 녹화가
 * 끝난 사실을 아는 것이 설정에 달려 있어서는 안 된다.
 *
 * 창의 수명은 이 객체를 만든 서비스보다 길다. 발행이 끝나면 서비스는 곧 접히므로, 사라지는
 * 시점을 코루틴 스코프가 아니라 메인 스레드 핸들러가 쥔다. 애플리케이션 컨텍스트로 만든다.
 */
internal class SaveCompleteOverlayWindow(
    private val context: Context,
) : SaveCompleteBanner {
    private val windowManager = context.getSystemService(WindowManager::class.java)
    private val mainHandler = Handler(Looper.getMainLooper())
    private var root: View? = null

    /** 지금 떠 있는 배너를 지우려고 예약해 둔 일. 새 배너가 뜨면 취소한다. */
    private var pendingRemoval: Runnable? = null

    override fun show(fileName: String) {
        // 권한이 없으면 화면에 그릴 수단이 토스트뿐이다. 조용히 넘어가면 아무 표시도 남지 않는다.
        if (!Settings.canDrawOverlays(context)) {
            mainHandler.post {
                val text = context.getString(R.string.save_complete_toast, fileName)
                Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
            }
            return
        }
        mainHandler.post {
            // 연달아 저장하면 앞의 배너가 아직 떠 있다. 두 장을 겹치지 않고 새것으로 바꾼다.
            // 앞 배너의 제거 예약도 함께 거둔다 — 그대로 두면 그 예약이 새 배너를 지운다.
            removeExisting()
            val banner = context.buildSaveCompleteBanner(fileName)
            if (!attach(banner)) return@post
            root = banner
            val removal = Runnable { removeExisting() }
            pendingRemoval = removal
            mainHandler.postDelayed(removal, SAVE_COMPLETE_DISPLAY_MILLIS)
        }
    }

    override fun dismiss() {
        mainHandler.post(::removeExisting)
    }

    private fun removeExisting() {
        pendingRemoval?.let(mainHandler::removeCallbacks)
        pendingRemoval = null
        val banner = root ?: return
        root = null
        detach(banner)
    }

    /**
     * 창을 붙인다. 성공하면 true.
     *
     * [Settings.canDrawOverlays] 검사와 실제 [WindowManager.addView] 는 스레드가 달라 그 사이에
     * 권한이 사라질 수 있고, 일부 기기는 검사가 통과해도 창을 거부한다. 붙이지 못하는 것은
     * 우리가 고칠 수 있는 상태가 아니므로 표시를 포기하되, 저장을 알리려다 앱을 죽이지는 않는다.
     */
    private fun attach(banner: View): Boolean =
        try {
            windowManager.addView(banner, saveCompleteLayoutParams(context.dpToPx(TOP_OFFSET_DP)))
            true
        } catch (denied: WindowManager.BadTokenException) {
            Log.w(LOG_TAG, "완료 배너를 띄우지 못했다 — 오버레이 권한이 없다", denied)
            false
        }

    /** 창을 뗀다. 시스템이 이미 떼어 간 창이면 조용히 지나간다 (권한 회수 등). */
    private fun detach(banner: View) {
        try {
            windowManager.removeView(banner)
        } catch (alreadyGone: IllegalArgumentException) {
            Log.w(LOG_TAG, "완료 배너가 이미 창에서 떨어져 있다", alreadyGone)
        }
    }

    private companion object {
        const val TOP_OFFSET_DP = 24f
        const val LOG_TAG = "SaveCompleteBanner"
    }
}

/** 배너가 머무는 시간. 눈에 걸릴 만큼 길고, 화면을 오래 가리지 않을 만큼 짧다. */
internal const val SAVE_COMPLETE_DISPLAY_MILLIS = 3_000L

/**
 * 배너 창의 배치 (DESIGN_GUIDE.md 4절).
 *
 * 상단 중앙에 띄운다 — 하단은 제스처 바와 플로팅 버블이 차지한다.
 */
internal fun saveCompleteLayoutParams(topOffsetPx: Int): WindowManager.LayoutParams =
    WindowManager
        .LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            // 누를 것이 없으므로 터치를 받지 않는다 — 배너 아래의 앱이 그대로 눌린다.
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = topOffsetPx
        }

/**
 * 배너 뷰 — 체크 + "녹화를 저장했습니다", 그 아래 파일명.
 *
 * 파일명을 함께 두는 이유: 완료 순간 사용자에게 필요한 정보는 "무엇이 저장됐는가" 하나다.
 */
internal fun Context.buildSaveCompleteBanner(fileName: String): View {
    val check =
        ImageView(this).apply {
            setImageResource(R.drawable.ic_banner_check)
            layoutParams =
                LinearLayout.LayoutParams(dpToPx(CHECK_SIZE_DP), dpToPx(CHECK_SIZE_DP)).apply {
                    marginEnd = dpToPx(GAP_DP)
                }
        }
    val title =
        TextView(this).apply {
            text = getString(R.string.save_complete_banner)
            setTextColor(BUBBLE_FOREGROUND)
            textSize = TITLE_TEXT_SP
        }
    val name =
        TextView(this).apply {
            text = fileName
            setTextColor(BUBBLE_MUTED)
            textSize = NAME_TEXT_SP
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.MIDDLE
        }
    val column =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(title)
            addView(name)
        }
    return LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        background =
            GradientDrawable().apply {
                setColor(BUBBLE_SURFACE)
                cornerRadius = dpToPx(CORNER_DP).toFloat()
            }
        setPadding(dpToPx(PADDING_H_DP), dpToPx(PADDING_V_DP), dpToPx(PADDING_H_DP), dpToPx(PADDING_V_DP))
        addView(check)
        addView(column)
    }
}

private const val CHECK_SIZE_DP = 20f
private const val GAP_DP = 10f
private const val CORNER_DP = 28f
private const val PADDING_H_DP = 18f
private const val PADDING_V_DP = 12f
private const val TITLE_TEXT_SP = 15f
private const val NAME_TEXT_SP = 12f
