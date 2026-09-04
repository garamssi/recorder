package io.rami.screenrecorder.service

import android.content.Context
import android.view.View
import io.rami.screenrecorder.core.common.time.DurationFormatter
import io.rami.screenrecorder.domain.model.TimeLimit

// 플로팅 버블의 접힘 토글과 펼침 메뉴 구성. 창 관리와 상태 전이는 FloatingCaptureBubble.kt 참조.

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
