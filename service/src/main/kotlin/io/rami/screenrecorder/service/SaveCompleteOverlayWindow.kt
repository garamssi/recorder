package io.rami.screenrecorder.service

import android.content.Context
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.View
import android.view.WindowManager

/**
 * 발행이 확정됐음을 화면 위에 알리는 표시 (기능명세서 6.1절 [결정]).
 *
 * 서비스는 "언제" 를 알고 "어디에 어떻게" 는 구현이 정한다. 시점 검증이 창을 띄우지 않고도
 * 되도록 경계를 둔다.
 */
internal interface SaveCompleteBanner {
    /** [fileName] 이 저장됐음을 잠깐 보여 준다. */
    fun show(fileName: String)
}

/**
 * 화면 위에 뜨는 저장 완료 배너 (DESIGN_GUIDE.md 4절 "저장 완료 배너").
 *
 * 카운트다운과 같은 시스템 오버레이 창이다 — 녹화가 끝나는 순간 사용자는 다른 앱을 보고 있고
 * 알림 그림자는 접혀 있다. 플로팅 버블과 별개인 이유: 버블을 끈 사용자도 녹화는 하고, 녹화가
 * 끝난 사실을 아는 것이 설정에 달려 있어서는 안 된다.
 */
internal class SaveCompleteOverlayWindow(
    private val context: Context,
) : SaveCompleteBanner {
    override fun show(fileName: String) = Unit
}

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
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = topOffsetPx
        }

/** 배너 뷰. 완료 문구 한 줄과 무엇이 저장됐는지(파일명) 한 줄. */
internal fun Context.buildSaveCompleteBanner(fileName: String): View = View(this)
