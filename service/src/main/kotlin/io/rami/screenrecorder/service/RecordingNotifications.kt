package io.rami.screenrecorder.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent

/**
 * 녹화 알림 구성 (기능명세서 11.1절).
 *
 * Stage 5에서 일시정지/재개 액션과 타이머 병기 표시로 확장된다.
 */
internal class RecordingNotifications(
    private val context: Context,
) {
    private val notificationManager =
        context.getSystemService(NotificationManager::class.java)

    /** 알림 채널을 등록한다 (재호출 안전). */
    fun createChannel() {
        val channel =
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.recording_notification_channel),
                NotificationManager.IMPORTANCE_LOW,
            )
        notificationManager.createNotificationChannel(channel)
    }

    /** [contentText]로 진행 알림을 만든다. */
    fun build(contentText: String): Notification {
        val stopIntent =
            PendingIntent.getService(
                context,
                STOP_REQUEST_CODE,
                Intent(context, RecordingForegroundService::class.java)
                    .setAction(RecordingForegroundService.ACTION_STOP),
                PendingIntent.FLAG_IMMUTABLE,
            )
        return Notification
            .Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.presence_video_online)
            .setContentTitle(context.getString(R.string.recording_notification_title))
            .setContentText(contentText)
            .setOngoing(true)
            .addAction(
                Notification.Action
                    .Builder(null, context.getString(R.string.recording_notification_stop), stopIntent)
                    .build(),
            ).build()
    }

    /** 진행 알림을 갱신한다. */
    fun update(contentText: String) {
        notificationManager.notify(NOTIFICATION_ID, build(contentText))
    }

    companion object {
        private const val CHANNEL_ID = "recording"
        private const val STOP_REQUEST_CODE = 1

        /** Foreground Service 알림 ID. */
        const val NOTIFICATION_ID = 1
    }
}
