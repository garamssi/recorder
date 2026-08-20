package io.rami.screenrecorder.presentation.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import io.rami.screenrecorder.domain.model.CaptureRegion
import io.rami.screenrecorder.presentation.R

/**
 * 부분 영역 선택 오버레이 창 (기능명세서 2.2절).
 *
 * SYSTEM_ALERT_WINDOW 권한이 있을 때 전체 화면 위에 표시하고,
 * "확인"이면 선택 영역을 콜백으로 넘긴 뒤 닫는다.
 */
class RegionSelectionOverlay(
    private val context: Context,
) {
    private var root: FrameLayout? = null

    /** 오버레이를 표시한다. 이미 떠 있으면 무시한다. */
    fun show(
        onConfirm: (CaptureRegion) -> Unit,
        onCancel: () -> Unit,
    ) {
        if (root != null) return
        val windowManager = context.getSystemService(WindowManager::class.java)
        val selector = RegionSelectorView(context)
        val container =
            FrameLayout(context).apply {
                addView(
                    selector,
                    FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT,
                    ),
                )
                addView(
                    buildButtons(
                        onConfirm = {
                            val region = selector.currentRegion()
                            dismiss()
                            onConfirm(region)
                        },
                        onCancel = {
                            dismiss()
                            onCancel()
                        },
                    ),
                    FrameLayout
                        .LayoutParams(
                            FrameLayout.LayoutParams.WRAP_CONTENT,
                            FrameLayout.LayoutParams.WRAP_CONTENT,
                        ).apply {
                            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                            bottomMargin =
                                (BUTTON_MARGIN_DP * context.resources.displayMetrics.density).toInt()
                        },
                )
            }
        root = container
        windowManager.addView(container, overlayLayoutParams())
    }

    /** 오버레이를 닫는다. */
    fun dismiss() {
        val container = root ?: return
        root = null
        context.getSystemService(WindowManager::class.java).removeView(container)
    }

    private fun buildButtons(
        onConfirm: () -> Unit,
        onCancel: () -> Unit,
    ) = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        addView(
            Button(context).apply {
                text = context.getString(R.string.dialog_cancel)
                setOnClickListener { onCancel() }
            },
        )
        addView(
            Button(context).apply {
                text = context.getString(R.string.region_confirm)
                setOnClickListener { onConfirm() }
            },
        )
    }

    private fun overlayLayoutParams() =
        WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        )

    private companion object {
        const val BUTTON_MARGIN_DP = 48
    }
}
