package io.rami.screenrecorder.service

import android.app.Service
import android.content.Context
import io.rami.screenrecorder.core.common.time.DurationFormatter
import io.rami.screenrecorder.domain.model.AutoStopReason
import io.rami.screenrecorder.domain.model.RecordingState
import io.rami.screenrecorder.domain.model.TimeLimit
import io.rami.screenrecorder.domain.model.durationOrNull
import kotlin.time.Duration

// 진행/완료 알림 문구. 알림 구성 자체는 RecordingNotifications.kt 참조.

/**
 * 진행 알림이 [state] 에서 보여 줄 문구. 알림을 갱신할 상태가 아니면 null.
 *
 * 발행 구간(Stopping)이 빠져 있어 2~4분 동안 "녹화 중 00:57:08" 이 그대로 남아 있었다
 * (기능명세서 6.1절 [결정]).
 */
internal fun Context.ongoingNotificationText(state: RecordingState): String? =
    when (state) {
        is RecordingState.Recording -> elapsedText(state.timeLimit, state.elapsed)
        is RecordingState.Paused ->
            getString(R.string.recording_notification_paused, elapsedText(state.timeLimit, state.elapsed))
        RecordingState.Stopping -> getString(R.string.recording_notification_saving)
        else -> null
    }

/** 경과 시간 표시. 시간 제한이 있으면 "경과 / 제한"으로 병기한다 (기능명세서 11.4절). */
internal fun Context.elapsedText(
    timeLimit: TimeLimit,
    elapsed: Duration,
): String =
    getString(
        R.string.recording_notification_elapsed,
        DurationFormatter.formatElapsedWithLimit(elapsed, timeLimit.durationOrNull()),
    )

/** 자동 중지 사유별 완료 알림 문구 (기능명세서 11.4절). */
internal fun Service.autoStopText(reason: AutoStopReason): String =
    when (reason) {
        AutoStopReason.TIME_LIMIT_REACHED ->
            getString(R.string.recording_notification_completed_time_limit)

        AutoStopReason.STORAGE_LOW ->
            getString(R.string.recording_notification_completed_storage_low)

        AutoStopReason.PAUSE_TIMEOUT ->
            getString(R.string.recording_notification_completed_pause_timeout)
    }
