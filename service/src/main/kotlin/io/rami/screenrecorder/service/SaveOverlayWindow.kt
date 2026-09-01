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
import io.rami.screenrecorder.core.common.design.SavingGaugeSpec
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

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
     * 오버레이 권한이 없어도 토스트로 대체하지 않는다 — 알림이 이미 퍼센트를 알리고 있고,
     * 발행 2~4분 동안 토스트를 반복해 띄우면 화면을 가린다.
     *
     * @param progress 0f..1f. 아직 모르면 null.
     */
    fun showSaving(
        elapsed: String,
        fileName: String,
        progress: Float?,
    )

    /**
     * 발행이 확정됐음을 보여 주고 잠시 뒤 스스로 접힌다.
     *
     * 이름만 받는다 — 길이와 진행률은 직전까지 그리던 것을 물려받는다. 이름은 발행이 확정한
     * 것이 진실이라 따로 받는다(같은 이름이 이미 있으면 발행이 "(1)" 을 붙인다).
     */
    fun showSaved(fileName: String)

    /**
     * 발행이 실패했음을 보여 주고 잠시 뒤 스스로 접힌다 (기능명세서 6.1절 [결정]).
     *
     * 인자를 받지 않는다. 무엇이 어디까지 가다 실패했는지는 직전까지 그리던 내용이 이미
     * 알고 있고, 실패 이벤트에는 그 정보가 없다.
     */
    fun showFailed()

    /**
     * 발행 구간이 끝났다. 결말을 보여 주지 못한 채 끝났으면 내린다.
     *
     * 빈 세션은 결말 없이 유휴로 끝난다. 그때 내리지 않으면 저장 중 표시가 화면에 영구히
     * 남는다. 결말(완료·실패)을 이미 보여 줬다면 그쪽 수명(3초)을 건드리지 않는다.
     */
    fun endSaving()

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
    private val appForeground: AppForegroundState,
    scope: CoroutineScope,
    private val windows: OverlayWindows = SystemOverlayWindows(context),
) : SaveOverlay {
    private val mainHandler = Handler(Looper.getMainLooper())

    init {
        // 앱을 오가는 동안 표시를 맞춘다. 발행은 분 단위로 걸려 그 사이 앱에 들어왔다 나갈 수 있다.
        scope.launch { appForeground.isForeground.collect(::followAppForeground) }
    }

    private var card: SaveOverlayCard? = null

    /** 완료 표시를 지우려고 예약해 둔 일. 새 발행이 시작되면 거둔다. */
    private var pendingRemoval: Runnable? = null

    /**
     * 마지막으로 그린 발행 중 내용.
     *
     * 실패 표시가 길이·파일명·진행률을 물려받는 출처다. 메인 스레드에서만 읽고 쓴다.
     *
     * 권한이 없어 그리지 못한 내용도 남긴다 — 그래야 그 상태에서 실패해도 토스트가 무엇이
     * 실패했는지 말할 수 있다. 정확히는 "마지막으로 그리려던" 내용이다.
     */
    private var lastSaving: SaveOverlayContent? = null

    /**
     * 결말(완료·실패)을 이미 보여 줬는지.
     *
     * 상태 흐름과 결말 흐름은 서로 다른 수집기에서 돈다. 마지막 진행률 갱신이 결말보다 늦게
     * 도착할 수 있는데, 그것을 그리면 표시가 "저장 중 100%" 로 되돌아가고 제거 예약까지
     * 거둬 가 카드가 그대로 굳는다.
     */
    private var outcomeShown = false

    override fun showSaving(
        elapsed: String,
        fileName: String,
        progress: Float?,
    ) {
        render { SaveOverlayContent(elapsed, fileName, progress, SaveOutcome.IN_PROGRESS) }
    }

    override fun showSaved(fileName: String) {
        // 성공은 발행 중 표시를 못 그렸더라도 반드시 알린다. 그래서 실패와 달리 직전 내용을
        // 물려받지 않는다 — 완료 카드는 중앙이 체크라 물려받을 것도 없다. 이름은 발행이
        // 확정한 것이 진실이다(같은 이름이 있으면 발행이 "(1)" 을 붙인다).
        showOutcome { SaveOverlayContent(NO_ELAPSED, fileName, progress = 1f, SaveOutcome.SAVED) }
    }

    override fun showFailed() {
        // 직전까지 그리던 것을 물려받되 결말만 바꾼다. 진행률은 채우지 않는다 — 채우면
        // 저장된 것으로 읽힌다. 물려받을 것이 없으면 무엇이 실패했는지 말할 수 없어 알림에 맡긴다.
        showOutcome { lastSaving?.copy(outcome = SaveOutcome.FAILED) }
    }

    /** 결말을 그리고 스스로 접히도록 예약한다. */
    private fun showOutcome(content: () -> SaveOverlayContent?) {
        render(
            onAttached = {
                outcomeShown = true
                val removal = Runnable { removeExisting() }
                pendingRemoval = removal
                mainHandler.postDelayed(removal, SAVE_OVERLAY_DISPLAY_MILLIS)
            },
            content = content,
        )
    }

    override fun endSaving() {
        mainHandler.post {
            // 완료를 이미 보여 줬다면 그쪽 3초 수명이 접기를 맡는다.
            if (!outcomeShown) removeExisting()
        }
    }

    override fun dismiss() {
        mainHandler.post {
            // 새 세션이다. 지난 발행의 내용을 물려줄 곳이 없다.
            lastSaving = null
            removeExisting()
        }
    }

    /**
     * 카드를 그린다. 창이 없으면 붙이고, 붙이지 못하면 [onAttached] 를 건너뛴다.
     *
     * 이미 떠 있으면 값만 갱신한다 — 진행률은 0.5% 단위로 올라와 발행 내내 수백 번 갱신되는데,
     * 그때마다 창을 다시 붙이면 링 애니메이션이 매번 처음부터 시작한다.
     *
     * 내용을 값이 아니라 함수로 받는다. 실패 카드는 [lastSaving] 을 읽어야 하는데, 그 읽기를
     * 바깥에서 하면 메시지가 하나 더 늘어 그 사이에 들어온 [dismiss] 를 지나쳐 버린다 — 새
     * 세션이 시작된 뒤에 지난 결말이 다시 붙는다. 읽기와 그리기를 한 메시지 안에 둔다.
     *
     * [content] 는 부수 효과가 없어야 한다 — 평가한 뒤 그리지 않고 버릴 수 있다(결말을 보여 준
     * 뒤에 도착한 진행률 갱신). null 을 돌려주면 그릴 것이 없다는 뜻으로 아무것도 하지 않는다.
     */
    private fun render(
        onAttached: () -> Unit = {},
        content: () -> SaveOverlayContent?,
    ) {
        mainHandler.post {
            val resolved = content() ?: return@post
            // 완료 판정은 메인 스레드 안에서 한다. 상태 흐름과 완료 흐름은 서로 다른 수집기에서
            // 돌아, 호출 스레드에서 보면 늦은 진행률 갱신이 완료를 덮는 창이 남는다.
            if (outcomeShown && resolved.outcome == SaveOutcome.IN_PROGRESS) return@post
            // 앞 발행의 완료 예약이 남아 있으면 거둔다. 그대로 두면 새 표시를 지운다.
            pendingRemoval?.let(mainHandler::removeCallbacks)
            pendingRemoval = null
            // 결말을 그리고 나면 물려줄 것이 없다. 남겨 두면 다음 세션의 실패가 지난 녹화의
            // 이름을 물려받을 수 있다.
            lastSaving = resolved.takeIf { it.outcome == SaveOutcome.IN_PROGRESS }
            // 앱 화면이 앞에 있으면 홈 카드가 같은 링을 그린다. 여기서 또 그리면 링이 둘이 된다
            // (기능명세서 6.1절 [결정]).
            if (appForeground.isForeground.value) {
                detachWindow()
                return@post
            }
            val existing = card
            if (existing != null) {
                existing.render(resolved)
                onAttached()
                return@post
            }
            // 카드가 없을 때만 권한을 확인한다. 진행률은 발행 내내 수백 번 갱신되는데 그때마다
            // 시스템 서버로 바인더 호출을 보낼 이유가 없다.
            if (!Settings.canDrawOverlays(context)) {
                // 진행 중에는 대체하지 않는다 — 발행 2~4분 동안 토스트를 반복하면 화면을 가린다.
                // 결말은 성공이든 실패든 알려야 한다. 특히 실패를 놓치면 저장된 줄 알고 넘어간다.
                if (resolved.outcome != SaveOutcome.IN_PROGRESS) context.showSaveToast(resolved)
                return@post
            }
            val fresh = SaveOverlayCard(context)
            fresh.render(resolved)
            // 권한 검사와 실제 붙이기는 스레드가 달라 그 사이 권한이 사라질 수 있다.
            if (!windows.attach(
                    fresh.root,
                    saveOverlayLayoutParams(
                        widthPx = context.dpToPx(SavingGaugeSpec.CARD_WIDTH_DP),
                        topOffsetPx = context.dpToPx(TOP_OFFSET_DP),
                    ),
                )
            ) {
                if (resolved.outcome != SaveOutcome.IN_PROGRESS) context.showSaveToast(resolved)
                return@post
            }
            card = fresh
            onAttached()
        }
    }

    private fun removeExisting() {
        pendingRemoval?.let(mainHandler::removeCallbacks)
        pendingRemoval = null
        outcomeShown = false
        detachWindow()
    }

    /**
     * 창만 뗀다. 결말 예약과 물려줄 내용은 건드리지 않는다 — 앱에 들어와 잠시 감추는 것과
     * 표시가 끝나는 것은 다르다.
     */
    private fun detachWindow() {
        val attached = card ?: return
        card = null
        windows.detach(attached.root)
    }

    /**
     * 앱을 오갈 때 표시를 맞춘다 (기능명세서 6.1절 [결정]).
     *
     * 결말(완료·실패)은 되살리지 않는다. 몇 초짜리라 되살릴 시점에는 이미 지난 이야기고,
     * 완료 알림이 사실을 들고 있다.
     */
    private fun followAppForeground(inApp: Boolean) {
        mainHandler.post {
            if (inApp) {
                detachWindow()
                return@post
            }
            if (outcomeShown) return@post
            lastSaving?.let { saving -> render { saving } }
        }
    }

    private companion object {
        const val TOP_OFFSET_DP = 24f

        /** 완료 카드는 중앙이 체크라 길이를 그리지 않는다. 물려받을 것이 없을 때 쓰는 빈 값. */
        const val NO_ELAPSED = ""
    }
}

/**
 * 화면에 그릴 수단이 토스트뿐인 경우 (기능명세서 6.1절 [결정]).
 *
 * 창 상태와 무관하다 — 문구를 고르고 띄우는 일뿐이다. 호출 지점이 모두 메인 스레드다.
 */
private fun Context.showSaveToast(content: SaveOverlayContent) {
    val template =
        if (content.outcome == SaveOutcome.FAILED) {
            R.string.save_failed_toast
        } else {
            R.string.save_complete_toast
        }
    Toast.makeText(this, getString(template, content.fileName), Toast.LENGTH_SHORT).show()
}

/** 완료 표시가 머무는 시간. 눈에 걸릴 만큼 길고, 화면을 오래 가리지 않을 만큼 짧다. */
internal const val SAVE_OVERLAY_DISPLAY_MILLIS = 3_000L

/**
 * 오버레이 창의 배치 (DESIGN_GUIDE.md 4절).
 *
 * 상단 중앙에 띄운다 — 하단은 제스처 바와 플로팅 버블이 차지한다.
 *
 * 폭을 고정한다. 내용에 맡기면 국면이 바뀔 때마다 카드가 옆으로 늘었다 줄었다 하고, 짧은
 * 문구에서는 링이 그 폭으로 눌려 좌우가 잘린다.
 */
internal fun saveOverlayLayoutParams(
    widthPx: Int,
    topOffsetPx: Int,
): WindowManager.LayoutParams =
    WindowManager
        .LayoutParams(
            widthPx,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            // 누를 것이 없으므로 터치를 받지 않는다 — 카드 아래의 앱이 그대로 눌린다.
            //
            // 대가가 있다. Android 12+ 는 터치를 받지 않는 오버레이의 불투명도를
            // `maximum_obscuring_opacity_for_touch`(기본 0.8)로 깎는다. 배경을 완전 불투명으로
            // 선언해도 화면에는 80%로 그려져 아래 앱 글자가 옅게 비친다. 실기기에서 플래그를
            // 빼면 정확히 1.00 이 나오는 것으로 확인했다. 터치를 가로채는 쪽이 더 나쁘므로
            // 비침을 감수한다 (docs/postmortem/2026-09-01-overlay-opacity-cap.md).
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
