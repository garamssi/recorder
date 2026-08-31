package io.rami.screenrecorder.service

import android.content.Context
import android.view.Gravity
import android.widget.LinearLayout

// 녹화 중 버블의 pill 뷰 구성. 창 관리와 상태 전이는 FloatingCaptureBubble.kt 참조.

/** 화면 녹화 중 pill을 만든다. */
internal fun Context.buildScreenRecordingPill(
    current: BubbleState.ScreenRecording,
    actions: BubbleActions?,
): PillViews? {
    val callbacks = actions ?: return null
    val pauseIcon = if (current.isPaused) R.drawable.ic_bubble_play else R.drawable.ic_bubble_pause
    val pauseLabel = getString(if (current.isPaused) R.string.floating_resume else R.string.floating_pause)
    return buildPill(current.elapsed, active = !current.isPaused) { pill ->
        pill.addView(
            circleButton(pauseIcon, pauseLabel, accent = false) {
                if (current.isPaused) callbacks.onResumeRecording() else callbacks.onPauseRecording()
            },
        )
        pill.addStacked(
            circleButton(
                R.drawable.ic_bubble_stop,
                getString(R.string.floating_stop),
                accent = true,
                onClick = callbacks::onStopRecording,
            ),
            withGap = false,
        )
    }
}

/**
 * 조작할 것이 없는 구간의 pill — 문구만 (기능명세서 6.1절 [결정]).
 *
 * 유휴 메뉴로 되돌리면 "녹화 시작"이 노출돼 MediaProjection 동의만 소비하게 된다.
 * 탭하면 펼침 메뉴가 열리지만 그 안에는 세션 중 메뉴("앱으로 가기")만 있다.
 */
internal fun Context.buildBusyPill(reason: BubbleBusyReason): PillViews =
    buildPill(getString(reason.labelRes()), active = false) { }

private fun BubbleBusyReason.labelRes(): Int =
    when (this) {
        BubbleBusyReason.PREPARING -> R.string.floating_preparing
        BubbleBusyReason.SAVING -> R.string.floating_saving
    }

/** 음성 녹음 중 pill을 만든다. */
internal fun Context.buildVoiceRecordingPill(
    current: BubbleState.VoiceRecording,
    actions: BubbleActions?,
): PillViews? {
    val callbacks = actions ?: return null
    return buildPill(current.elapsed, active = true) { pill ->
        pill.addView(
            circleButton(
                R.drawable.ic_bubble_stop,
                getString(R.string.floating_stop),
                accent = true,
                onClick = callbacks::onStopVoiceRecording,
            ),
        )
    }
}

/**
 * "REC 점 + 경과 시간" 손잡이와 [controls]로 채운 제어 버튼을 담은 pill.
 *
 * 손잡이에는 클릭 리스너를 달지 않는다 — 드래그와 탭 구분은 [BubbleDragHandler]가 맡는다.
 */
private fun Context.buildPill(
    elapsedText: String,
    active: Boolean,
    controls: (LinearLayout) -> Unit,
): PillViews {
    val elapsed = elapsedLabel().apply { text = elapsedText }
    val handle =
        LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(statusDot(active))
            addView(elapsed)
        }
    val pill = pillContainer().apply { addView(handle) }
    controls(pill)
    return PillViews(elapsed = elapsed, handle = handle, root = pill)
}
