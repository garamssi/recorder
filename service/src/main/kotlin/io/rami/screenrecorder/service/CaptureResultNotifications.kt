package io.rami.screenrecorder.service

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context

// 캡처가 끝났거나 시작할 수 없다는 "결과 안내" 알림.
// 포그라운드 진행 알림(ID 1)은 RecordingNotifications.kt 가 다룬다 — 수명도 ID 도 다르다.

/** 완료/자동 중지 알림 (기능명세서 11.4절: 탭 시 앱 열기). */
internal fun Context.showCompletedNotification(contentText: String) {
    val openApp =
        PendingIntent.getActivity(
            this,
            OPEN_APP_REQUEST_CODE,
            checkNotNull(packageManager.getLaunchIntentForPackage(packageName)),
            PendingIntent.FLAG_IMMUTABLE,
        )
    notify(
        COMPLETED_NOTIFICATION_ID,
        resultNotification(getString(R.string.recording_notification_completed_title), contentText)
            .setContentIntent(openApp)
            .build(),
    )
}

/**
 * 지금은 시작할 수 없다는 안내 (기능명세서 6.1절 [결정]).
 *
 * 완료 알림을 재사용하면 녹화가 도는 중에 "녹화가 완료되었습니다" 가 뜬다. 제목이 내용과
 * 정면으로 모순되고 사용자는 녹화가 끝난 줄 안다. ID 도 나눠 써야 서로를 지우지 않는다.
 */
internal fun Context.showBusyNotification(contentText: String) {
    notify(BUSY_NOTIFICATION_ID, resultNotification(getString(R.string.capture_busy_title), contentText).build())
}

private fun Context.resultNotification(
    title: String,
    contentText: String,
): Notification.Builder =
    Notification
        .Builder(this, RecordingNotifications.CHANNEL_ID)
        .setSmallIcon(android.R.drawable.presence_video_online)
        .setContentTitle(title)
        .setContentText(contentText)
        .setAutoCancel(true)

private fun Context.notify(
    id: Int,
    notification: Notification,
) = getSystemService(NotificationManager::class.java).notify(id, notification)

/** 완료 안내. 진행 알림(1)과 구분한다. */
private const val COMPLETED_NOTIFICATION_ID = 2

/** 시작 불가 안내. 완료와 서로 지우지 않도록 따로 쓴다. */
private const val BUSY_NOTIFICATION_ID = 4

private const val OPEN_APP_REQUEST_CODE = 100
