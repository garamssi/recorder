package io.rami.screenrecorder.service

import android.app.Notification
import android.app.PendingIntent
import android.content.Context

/**
 * 플로팅 캡처 버블을 유지하는 상시 알림 (기능명세서 11.1절).
 *
 * 버블이 다른 앱 위에 떠 있으려면 포그라운드 서비스가 필요하고, 그 대가로 알림이 남는다.
 * 사용자가 알림에서 바로 버블을 내릴 수 있도록 "숨기기" 액션을 둔다.
 *
 * 녹화 알림과 수명·ID·내용이 모두 달라 [RecordingNotifications] 와 나눠 둔다 — 한 클래스가
 * 알림 네 종류를 다루면 어느 것을 고치는지 알기 어렵다.
 */
internal fun Context.buildFloatingBubbleNotification(): Notification =
    Notification
        .Builder(this, RecordingNotifications.CHANNEL_ID)
        .setSmallIcon(android.R.drawable.presence_video_online)
        .setContentTitle(getString(R.string.floating_notification_title))
        .setContentText(getString(R.string.floating_notification_text))
        .setOngoing(true)
        .addAction(
            Notification.Action
                .Builder(
                    null,
                    getString(R.string.floating_notification_hide),
                    PendingIntent.getService(
                        this,
                        FloatingCaptureService.ACTION_HIDE.hashCode(),
                        FloatingCaptureService.hideIntent(this),
                        PendingIntent.FLAG_IMMUTABLE,
                    ),
                ).build(),
        ).build()
