package io.rami.screenrecorder.service

import android.content.Context
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

/** 버블이 그려야 할 상태 (기능명세서 11.1절). */
internal sealed interface BubbleState {
    /** 아무 캡처도 진행 중이 아님 — 접힘/펼침 캡처 메뉴. */
    data object Idle : BubbleState

    /** 화면 녹화 중 — 경과 시간과 일시정지/중지. */
    data class ScreenRecording(
        val elapsed: String,
        val isPaused: Boolean,
    ) : BubbleState

    /** 음성 전용 녹음 중 — 경과 시간과 중지. */
    data class VoiceRecording(
        val elapsed: String,
    ) : BubbleState
}

/** 펼침 메뉴 한 줄의 구성. */
private class BubbleMenuItem(
    val iconRes: Int,
    val labelRes: Int,
    val accent: Boolean = false,
    val onClick: () -> Unit,
)

/** 버블이 상위(서비스)에 위임하는 동작. */
internal interface BubbleActions {
    fun onStartRecording()

    fun onStopRecording()

    fun onPauseRecording()

    fun onResumeRecording()

    fun onCaptureScreenshot()

    fun onStartVoiceRecording()

    fun onStopVoiceRecording()

    /** 앱 화면을 연다 (기능명세서 11.1절: 다른 앱에서 앱으로 돌아가기). */
    fun onOpenApp()
}

/**
 * 다른 앱 위에 떠 있는 캡처 버블 (기능명세서 11.1절).
 *
 * 접힘 상태는 원형 "+" 버튼이고, 탭하면 화면 녹화·화면 캡처·음성 녹음 메뉴가 펼쳐진다.
 * 드래그로 옮길 수 있고 손을 떼면 가까운 가장자리에 붙는다 ([BubbleDragHandler]).
 * 녹화가 시작되면 경과 시간과 제어 버튼을 담은 pill로 바뀐다.
 *
 * 오버레이 특성상 버블 자체가 녹화 영상에 찍힌다 (명세에 명시).
 */
internal class FloatingCaptureBubble(
    private val context: Context,
) {
    private val windowManager = context.getSystemService(WindowManager::class.java)
    private val layoutParams = overlayLayoutParams()

    private var root: FrameLayout? = null
    private var content: LinearLayout? = null
    private var elapsedView: TextView? = null

    // 접힘 상태에서 드래그·탭을 받는 영역. 상태에 따라 "+" 버튼 또는 pill의 시간 영역이다.
    private var dragHandle: android.view.View? = null

    private val position = BubbleWindowPosition(context, windowManager, layoutParams)
    private var actions: BubbleActions? = null
    private var state: BubbleState = BubbleState.Idle
    private var expanded = false

    /** 버블을 띄운다. 이미 떠 있으면 무시한다. */
    fun show(actions: BubbleActions) {
        if (root != null) return
        this.actions = actions
        val container = FrameLayout(context)
        val stack =
            LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.END
            }
        container.addView(stack)
        root = container
        content = stack
        windowManager.addView(container, layoutParams)
        render(BubbleState.Idle)
    }

    /** 상태를 반영해 다시 그린다. 경과 시간만 바뀌면 텍스트만 갱신한다. */
    fun render(newState: BubbleState) {
        if (root == null) return
        val onlyElapsedChanged = state.sameShapeAs(newState)
        state = newState
        if (onlyElapsedChanged) {
            elapsedView?.text = newState.elapsedText()
            return
        }
        rebuild()
    }

    /** 버블을 닫는다. */
    fun dismiss() {
        val container = root ?: return
        root = null
        content = null
        elapsedView = null
        dragHandle = null
        actions = null
        expanded = false
        position.reset()
        windowManager.removeView(container)
    }

    private fun rebuild() {
        val stack = content ?: return
        stack.removeAllViews()
        elapsedView = null
        dragHandle = null
        if (expanded) buildMenuRows(stack)
        when (val current = state) {
            is BubbleState.Idle -> buildToggle(stack)
            is BubbleState.ScreenRecording ->
                context.buildScreenRecordingPill(stack, current, actions).let { pill ->
                    elapsedView = pill?.elapsed
                    dragHandle = pill?.handle
                }

            is BubbleState.VoiceRecording ->
                context.buildVoiceRecordingPill(stack, current, actions).let { pill ->
                    elapsedView = pill?.elapsed
                    dragHandle = pill?.handle
                }
        }
        attachDrag()
        // 실제 크기는 측정 후에 확정되므로 다음 레이아웃 패스에서 위치를 맞춘다.
        root?.post { root?.let(position::keepOnScreen) }
    }

    /** 펼침 상태의 메뉴 줄. 진행 중일 때는 겹칠 수 없는 캡처 동작을 빼고 "앱으로 가기"만 남긴다. */
    private fun buildMenuRows(stack: LinearLayout) {
        val callbacks = actions ?: return
        val items =
            if (state is BubbleState.Idle) idleMenuItems(callbacks) else inSessionMenuItems(callbacks)
        items.forEachIndexed { index, item ->
            stack.addStacked(
                context.actionRow(
                    iconRes = item.iconRes,
                    label = context.getString(item.labelRes),
                    accent = item.accent,
                ) { collapseThen(item.onClick) },
                withGap = index > 0,
            )
        }
    }

    /** 접힘/펼침 토글 버튼 (유휴 상태의 아래쪽 요소). */
    private fun buildToggle(stack: LinearLayout) {
        val toggleIcon = if (expanded) R.drawable.ic_bubble_close else R.drawable.ic_bubble_add
        val toggleLabel = context.getString(if (expanded) R.string.floating_collapse else R.string.floating_expand)
        val toggle = context.circleButton(toggleIcon, toggleLabel, accent = !expanded) { toggleExpanded() }
        stack.addStacked(toggle, withGap = expanded)
        dragHandle = toggle
    }

    /**
     * 드래그 손잡이를 붙인다.
     *
     * 펼친 상태에서는 스택 배경 전체가 손잡이다(자식 버튼이 탭을 먼저 받는다).
     * 접힌 상태에서는 유일한 뷰가 손잡이이므로 탭과 드래그를 [BubbleDragHandler]가 구분한다.
     */
    private fun attachDrag() {
        val handle = (if (expanded) content else dragHandle) ?: return
        BubbleDragHandler(
            windowManager = windowManager,
            layoutParams = layoutParams,
            onTap = { toggleExpanded() },
            onSnapped = { toRight -> position.onSnapped(toRight, root) },
        ).attachTo(handle)
    }

    private fun toggleExpanded() {
        expanded = !expanded
        rebuild()
    }

    private fun collapseThen(action: () -> Unit) {
        expanded = false
        rebuild()
        action()
    }

    private fun overlayLayoutParams() =
        WindowManager
            .LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                // FLAG_LAYOUT_IN_SCREEN이 없으면 y가 상태 바 아래를 0으로 잡아,
                // 화면 크기 기준으로 계산한 위치가 상태 바 높이만큼 아래로 밀려 하단이 잘린다.
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT,
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = context.dpToPx(INITIAL_MARGIN_DP)
                y = context.dpToPx(INITIAL_TOP_DP)
            }

    private companion object {
        const val INITIAL_MARGIN_DP = 12f
        const val INITIAL_TOP_DP = 160f
    }
}

/** 모양(펼침 여부·버튼 구성)이 같아 텍스트만 갱신하면 되는지. */
private fun BubbleState.sameShapeAs(other: BubbleState): Boolean =
    when {
        this is BubbleState.ScreenRecording && other is BubbleState.ScreenRecording ->
            isPaused == other.isPaused

        this is BubbleState.VoiceRecording && other is BubbleState.VoiceRecording -> true
        else -> false
    }

/** 상태에 담긴 경과 시간 문자열 (없으면 빈 문자열). */
private fun BubbleState.elapsedText(): String =
    when (this) {
        is BubbleState.ScreenRecording -> elapsed
        is BubbleState.VoiceRecording -> elapsed
        is BubbleState.Idle -> ""
    }

/** 펼침 메뉴 항목 — 캡처 동작 3개 뒤에 앱으로 이동을 둔다 (DESIGN_GUIDE.md 4절). */
private fun idleMenuItems(callbacks: BubbleActions) =
    listOf(
        BubbleMenuItem(
            R.drawable.ic_bubble_record,
            R.string.floating_record,
            accent = true,
            onClick = callbacks::onStartRecording,
        ),
        BubbleMenuItem(
            R.drawable.ic_bubble_screenshot,
            R.string.floating_screenshot,
            onClick = callbacks::onCaptureScreenshot,
        ),
        BubbleMenuItem(
            R.drawable.ic_bubble_mic,
            R.string.floating_voice,
            onClick = callbacks::onStartVoiceRecording,
        ),
        BubbleMenuItem(
            R.drawable.ic_bubble_open_app,
            R.string.floating_open_app,
            onClick = callbacks::onOpenApp,
        ),
    )

/** 진행 중 펼침 메뉴 — 화면 캡처·음성 녹음은 세션이 겹쳐 시작할 수 없으므로 넣지 않는다. */
private fun inSessionMenuItems(callbacks: BubbleActions) =
    listOf(
        BubbleMenuItem(
            R.drawable.ic_bubble_open_app,
            R.string.floating_open_app,
            onClick = callbacks::onOpenApp,
        ),
    )
