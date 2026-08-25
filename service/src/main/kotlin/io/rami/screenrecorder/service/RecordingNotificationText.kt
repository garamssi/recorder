package io.rami.screenrecorder.service

import android.app.Service
import io.rami.screenrecorder.core.common.time.DurationFormatter
import io.rami.screenrecorder.domain.model.AutoStopReason
import io.rami.screenrecorder.domain.model.TimeLimit
import io.rami.screenrecorder.domain.model.durationOrNull
import kotlin.time.Duration

// 진행/완료 알림 문구. 알림 구성 자체는 RecordingNotifications.kt 참조.

/** 경과 시간 표시. 시간 제한이 있으면 "경과 / 제한"으로 병기한다 (기능명세서 11.4절). */
internal fun Service.elapsedText(
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
