package io.rami.screenrecorder.service

import android.content.Context
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import io.rami.screenrecorder.core.common.time.DurationFormatter
import io.rami.screenrecorder.domain.model.TimeLimit

/** 버블이 그려야 할 상태 (기능명세서 11.1절). */
internal sealed interface BubbleState {
    /**
     * 아무 캡처도 진행 중이 아님 — 접힘/펼침 캡처 메뉴.
     *
     * @param timeLimit 펼침 메뉴가 보여 줄 현재 녹화 시간 제한 (기능명세서 11.4절).
     */
    data class Idle(
        val timeLimit: TimeLimit = TimeLimit.None,
    ) : BubbleState

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

/**
 * 펼침 메뉴 한 줄의 구성.
 *
 * @param label 이미 지역화된 문구. 시간 제한 줄처럼 현재 설정값이 붙는 줄이 있어
 *   문자열 리소스 ID만으로는 표현할 수 없다.
 */
internal class BubbleMenuItem(
    val iconRes: Int,
    val label: String,
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

    /** 녹화 시간 제한 입력 창을 연다 (기능명세서 11.4절). */
    fun onEditTimeLimit()

    /** 앱 화면을 연다 (기능명세서 11.1절: 다른 앱에서 앱으로 돌아가기). */
    fun onOpenApp()
}

/**
 * 다른 앱 위에 떠 있는 캡처 버블 (기능명세서 11.1절).
 *
 * 접힘 상태는 원형 "+" 버튼이고, 탭하면 화면 녹화·화면 캡처·음성 녹음·시간 제한 메뉴가 펼쳐진다.
 * 드래그로 옮길 수 있고 손을 떼면 가까운 가장자리에 붙는다 ([BubbleDragHandler]).
 * 녹화가 시작되면 경과 시간과 제어 버튼을 담은 pill로 바뀐다.
 *
 * 오버레이 특성상 버블 자체가 녹화 영상에 찍힌다 (명세에 명시).
 */
internal class FloatingCaptureBubble(
    private val context: Context,
) {
    private val windowManager = context.getSystemService(WindowManager::class.java)
    private val layoutParams = context.overlayLayoutParams()

    private var root: FrameLayout? = null
    private var content: LinearLayout? = null
    private var elapsedView: TextView? = null

    // 접힘 상태에서 드래그·탭을 받는 영역. 상태에 따라 "+" 버튼 또는 pill의 시간 영역이다.
    private var dragHandle: View? = null

    // 펼쳐도 제자리를 지켜야 하는 요소 ("+" 버튼 또는 pill)와, 그 위나 아래에 붙는 메뉴 줄들.
    private var baseView: View? = null
    private var menuRows: List<View> = emptyList()
    private var menuBelowBase = false

    private val position = BubbleWindowPosition(context, windowManager, layoutParams)
    private var actions: BubbleActions? = null
    private var state: BubbleState = BubbleState.Idle()
    private var expanded = false

    /** 버블을 띄운다. 이미 떠 있으면 무시한다. */
    fun show(actions: BubbleActions) {
        if (root != null) return
        this.actions = actions
        val container = FrameLayout(context)
        val stack = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        container.addView(stack)
        root = container
        content = stack
        windowManager.addView(container, layoutParams)
        rebuild()
    }

    /** 상태를 반영해 다시 그린다. 값이 그대로면 아무것도 하지 않고, 경과 시간만 바뀌면 텍스트만 갱신한다. */
    fun render(newState: BubbleState) {
        if (root == null || newState == state) return
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
        baseView = null
        menuRows = emptyList()
        menuBelowBase = false
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
        val base = createBase() ?: return
        baseView = base
        menuRows =
            if (expanded) {
                context.bubbleMenuRows(context.menuItemsFor(state, actions), position.snappedToRight) {
                    collapseThen(it.onClick)
                }
            } else {
                emptyList()
            }
        // 총 높이는 순서와 무관하므로 잠정 배치로 재고, 방향이 정해지면 그때 순서를 바꾼다.
        menuBelowBase = false
        stack.arrange(menuRows, base, menuBelowBase = false, snappedToRight = position.snappedToRight)
        attachDrag()
        place(position::keepOnScreen)
    }

    /** 상태에 맞는 기준 요소 — 유휴면 토글 버튼, 진행 중이면 pill. */
    private fun createBase(): View? =
        when (val current = state) {
            is BubbleState.Idle -> context.bubbleToggle(expanded, ::toggleExpanded).also { dragHandle = it }
            is BubbleState.ScreenRecording -> context.buildScreenRecordingPill(current, actions)?.let(::adopt)
            is BubbleState.VoiceRecording -> context.buildVoiceRecordingPill(current, actions)?.let(::adopt)
        }

    private fun adopt(pill: PillViews): View {
        elapsedView = pill.elapsed
        dragHandle = pill.handle
        return pill.root
    }

    /**
     * 창을 화면 안에 맞추고, 정해진 방향대로 메뉴를 다시 담는다.
     *
     * @param reposition 컨테이너와 기준 요소를 받아 "메뉴를 기준 요소 아래에 둘지"를 돌려준다.
     */
    private fun place(reposition: (container: View, base: View) -> Boolean) {
        val container = root ?: return
        val stack = content ?: return
        val base = baseView ?: return
        val below = reposition(container, base)
        if (below == menuBelowBase) return
        menuBelowBase = below
        stack.arrange(menuRows, base, below, position.snappedToRight)
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
            onSnapped = { toRight ->
                // 반대쪽 변으로 옮겨 가면 라벨 위치와 정렬이 뒤집히므로 메뉴를 다시 만든다.
                val edgeChanged = position.snappedToRight != toRight
                place { container, base -> position.onSnapped(toRight, container, base) }
                if (edgeChanged && expanded) rebuild()
            },
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
}

private const val INITIAL_MARGIN_DP = 12f
private const val INITIAL_TOP_DP = 160f

/** 다른 앱 위에 뜨는 WRAP_CONTENT 창의 파라미터. */
private fun Context.overlayLayoutParams() =
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
            x = dpToPx(INITIAL_MARGIN_DP)
            y = dpToPx(INITIAL_TOP_DP)
        }

/** 접힘/펼침 토글 버튼. 펼침 상태에서는 닫기 아이콘이 된다. */
internal fun Context.bubbleToggle(
    expanded: Boolean,
    onToggle: () -> Unit,
): View {
    val icon = if (expanded) R.drawable.ic_bubble_close else R.drawable.ic_bubble_add
    val label = getString(if (expanded) R.string.floating_collapse else R.string.floating_expand)
    return circleButton(icon, label, accent = !expanded, onClick = onToggle)
}

/** 펼침 메뉴 줄들. 라벨은 붙어 있는 변의 반대쪽에 둔다. */
internal fun Context.bubbleMenuRows(
    items: List<BubbleMenuItem>,
    snappedToRight: Boolean,
    onSelect: (BubbleMenuItem) -> Unit,
): List<View> =
    items.map { item ->
        actionRow(
            iconRes = item.iconRes,
            label = item.label,
            accent = item.accent,
            labelFirst = snappedToRight,
        ) { onSelect(item) }
    }

/** 진행 중일 때는 겹칠 수 없는 캡처 동작을 빼고 "앱으로 가기"만 남긴다. */
internal fun Context.menuItemsFor(
    state: BubbleState,
    actions: BubbleActions?,
): List<BubbleMenuItem> {
    val callbacks = actions ?: return emptyList()
    return if (state is BubbleState.Idle) {
        idleMenuItems(state.timeLimit, callbacks)
    } else {
        inSessionMenuItems(callbacks)
    }
}

/** 기준 요소와 메뉴 줄을 방향에 맞는 순서와 정렬로 다시 담는다. */
internal fun LinearLayout.arrange(
    menuRows: List<View>,
    base: View,
    menuBelowBase: Boolean,
    snappedToRight: Boolean,
) {
    removeAllViews()
    gravity = if (snappedToRight) Gravity.END else Gravity.START
    val ordered = if (menuBelowBase) listOf(base) + menuRows else menuRows + base
    ordered.forEachIndexed { index, view ->
        addStacked(view, withGap = index > 0, alignEnd = snappedToRight)
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

/** 시간 제한 줄에 붙는 현재 값 — 없으면 "제한 없음". */
internal fun Context.timeLimitLabel(timeLimit: TimeLimit): String =
    getString(
        R.string.floating_time_limit,
        when (timeLimit) {
            is TimeLimit.None -> getString(R.string.floating_time_limit_none)
            is TimeLimit.Limited -> DurationFormatter.formatElapsed(timeLimit.duration)
        },
    )

/**
 * 펼침 메뉴 항목 — 캡처 동작 3개, 시간 제한, 앱으로 이동 순이다 (DESIGN_GUIDE.md 4절).
 *
 * 시간 제한은 녹화를 시작하기 전에만 바꿀 수 있으므로 유휴 메뉴에만 둔다 (기능명세서 11.4절).
 */
private fun Context.idleMenuItems(
    timeLimit: TimeLimit,
    callbacks: BubbleActions,
) = listOf(
    BubbleMenuItem(
        R.drawable.ic_bubble_record,
        getString(R.string.floating_record),
        accent = true,
        onClick = callbacks::onStartRecording,
    ),
    BubbleMenuItem(
        R.drawable.ic_bubble_screenshot,
        getString(R.string.floating_screenshot),
        onClick = callbacks::onCaptureScreenshot,
    ),
    BubbleMenuItem(
        R.drawable.ic_bubble_mic,
        getString(R.string.floating_voice),
        onClick = callbacks::onStartVoiceRecording,
    ),
    BubbleMenuItem(
        R.drawable.ic_bubble_time_limit,
        timeLimitLabel(timeLimit),
        onClick = callbacks::onEditTimeLimit,
    ),
    BubbleMenuItem(
        R.drawable.ic_bubble_open_app,
        getString(R.string.floating_open_app),
        onClick = callbacks::onOpenApp,
    ),
)

/**
 * 진행 중 펼침 메뉴 — 화면 캡처·음성 녹음은 세션이 겹쳐 시작할 수 없으므로 넣지 않는다.
 *
 * 시간 제한도 뺀다: 녹화 중 해제·연장은 1차 범위 제외다 (기능명세서 11.4절).
 */
private fun Context.inSessionMenuItems(callbacks: BubbleActions) =
    listOf(
        BubbleMenuItem(
            R.drawable.ic_bubble_open_app,
            getString(R.string.floating_open_app),
            onClick = callbacks::onOpenApp,
        ),
    )
