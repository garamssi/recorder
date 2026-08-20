package io.rami.screenrecorder.data.transcode

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import androidx.work.ForegroundInfo
import androidx.work.WorkManager
import io.rami.screenrecorder.data.R
import java.util.UUID

/** 압축 진행률 알림 (기능명세서 8절: 백그라운드 진행률 + 취소, 검수 #3). */
internal object TranscodeNotifications {
    fun foregroundInfo(
        context: Context,
        workId: UUID,
        progressPercent: Int,
    ): ForegroundInfo {
        ensureChannel(context)
        val cancelIntent = WorkManager.getInstance(context).createCancelPendingIntent(workId)
        val notification =
            Notification
                .Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setContentTitle(context.getString(R.string.transcode_notification_title))
                .setProgress(PERCENT_MAX, progressPercent, false)
                .setOngoing(true)
                .addAction(
                    Notification.Action
                        .Builder(
                            null,
                            context.getString(R.string.transcode_notification_cancel),
                            cancelIntent,
                        ).build(),
                ).build()
        return ForegroundInfo(
            NOTIFICATION_ID,
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROCESSING,
        )
    }

    private fun ensureChannel(context: Context) {
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.transcode_channel_name),
                NotificationManager.IMPORTANCE_LOW,
            ),
        )
    }

    private const val NOTIFICATION_ID = 20
    private const val CHANNEL_ID = "transcode"
    private const val PERCENT_MAX = 100
}
