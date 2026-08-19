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
 * 진행 알림(경과 시간, 일시정지/재개/중지 액션)과 완료 알림을 담당한다.
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

    /** 진행 알림. [isPaused]에 따라 일시정지/재개 액션을 전환한다. */
    fun buildOngoing(
        contentText: String,
        isPaused: Boolean,
    ): Notification {
        val toggleAction =
            if (isPaused) {
                action(R.string.recording_notification_resume, RecordingForegroundService.ACTION_RESUME)
            } else {
                action(R.string.recording_notification_pause, RecordingForegroundService.ACTION_PAUSE)
            }
        return Notification
            .Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.presence_video_online)
            .setContentTitle(context.getString(R.string.recording_notification_title))
            .setContentText(contentText)
            .setOngoing(true)
            .addAction(toggleAction)
            .addAction(action(R.string.recording_notification_stop, RecordingForegroundService.ACTION_STOP))
            .build()
    }

    /** 진행 알림을 갱신한다. */
    fun updateOngoing(
        contentText: String,
        isPaused: Boolean,
    ) {
        notificationManager.notify(NOTIFICATION_ID, buildOngoing(contentText, isPaused))
    }

    /** 완료/자동 중지 알림을 별도로 게시한다 (기능명세서 11.4절: 탭 시 앱 열기). */
    fun showCompleted(contentText: String) {
        val openApp =
            PendingIntent.getActivity(
                context,
                OPEN_APP_REQUEST_CODE,
                checkNotNull(context.packageManager.getLaunchIntentForPackage(context.packageName)),
                PendingIntent.FLAG_IMMUTABLE,
            )
        val notification =
            Notification
                .Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.presence_video_online)
                .setContentTitle(context.getString(R.string.recording_notification_completed_title))
                .setContentText(contentText)
                .setContentIntent(openApp)
                .setAutoCancel(true)
                .build()
        notificationManager.notify(COMPLETED_NOTIFICATION_ID, notification)
    }

    private fun action(
        labelRes: Int,
        serviceAction: String,
    ): Notification.Action {
        val pendingIntent =
            PendingIntent.getService(
                context,
                serviceAction.hashCode(),
                Intent(context, RecordingForegroundService::class.java).setAction(serviceAction),
                PendingIntent.FLAG_IMMUTABLE,
            )
        return Notification.Action
            .Builder(null, context.getString(labelRes), pendingIntent)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "recording"
        private const val OPEN_APP_REQUEST_CODE = 100

        /** Foreground Service 진행 알림 ID. */
        const val NOTIFICATION_ID = 1

        /** 완료 알림 ID (진행 알림과 분리). */
        const val COMPLETED_NOTIFICATION_ID = 2
    }
}
