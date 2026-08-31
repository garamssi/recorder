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

    /**
     * 이 앱의 알림은 아이콘·제목·상시 표시가 모두 같고 **조작 액션만 다르다.**
     * 빌더를 네 벌 두면 한 벌만 고쳐지는 날이 온다.
     */
    private fun build(
        contentText: String,
        title: String = context.getString(R.string.recording_notification_title),
        actions: List<Notification.Action> = emptyList(),
    ): Notification =
        Notification
            .Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.presence_video_online)
            .setContentTitle(title)
            .setContentText(contentText)
            .setOngoing(true)
            .apply { actions.forEach(::addAction) }
            .build()

    /** 진행 알림. [isPaused]에 따라 일시정지/재개 액션을 전환한다. */
    fun buildOngoing(
        contentText: String,
        isPaused: Boolean,
    ): Notification {
        val toggle =
            if (isPaused) {
                action(R.string.recording_notification_resume, RecordingForegroundService.ACTION_RESUME)
            } else {
                action(R.string.recording_notification_pause, RecordingForegroundService.ACTION_PAUSE)
            }
        return build(contentText, actions = listOf(toggle, stopAction))
    }

    /** 진행 알림을 갱신한다. */
    fun updateOngoing(
        contentText: String,
        isPaused: Boolean,
    ) {
        notificationManager.notify(NOTIFICATION_ID, buildOngoing(contentText, isPaused))
    }

    /**
     * 조작을 제한한 진행 알림 (기능명세서 6.1절 [결정]).
     *
     * 실제로 동작하는 것만 남긴다. 카운트다운 중의 일시정지는 세션이 아직 없어 아무 일도 하지
     * 않고, 발행은 되돌릴 수 없다. 같은 NOTIFICATION_ID 를 유지해야 포그라운드 알림이 이어진다.
     *
     * @param stoppable 중지가 실제로 동작하는 구간인지. 카운트다운은 취소되지만 발행은 아니다.
     */
    fun showLimited(
        contentText: String,
        stoppable: Boolean,
    ) {
        val actions = if (stoppable) listOf(stopAction) else emptyList()
        notificationManager.notify(NOTIFICATION_ID, build(contentText, actions = actions))
    }

    /**
     * 화면 캡처·음성 녹음용 진행 알림 (기능명세서 12, 13절).
     *
     * 일시정지/재개가 없는 짧은 작업이므로 액션은 중지 하나뿐이며, 사용자가 멈출 수 있는
     * 음성 녹음에만 붙인다. 몇 초 만에 끝나는 화면 캡처에 중지 버튼을 두면 오해를 부른다.
     */
    fun buildQuickCapture(
        contentText: String,
        stoppable: Boolean,
    ): Notification =
        build(
            contentText,
            actions =
                if (stoppable) {
                    listOf(action(R.string.recording_notification_stop, RecordingForegroundService.ACTION_STOP_VOICE))
                } else {
                    emptyList()
                },
        )

    /** 화면 녹화 중지 액션. 여러 알림이 같은 것을 쓴다. */
    private val stopAction: Notification.Action
        get() = action(R.string.recording_notification_stop, RecordingForegroundService.ACTION_STOP)

    /** 음성 녹음 진행 알림을 갱신한다 (경과 시간 반영). */
    fun updateQuickCapture(contentText: String) {
        notificationManager.notify(NOTIFICATION_ID, buildQuickCapture(contentText, stoppable = true))
    }

    /**
     * 플로팅 캡처 버블을 유지하는 상시 알림 (기능명세서 11.1절).
     *
     * 버블이 다른 앱 위에 떠 있으려면 포그라운드 서비스가 필요하고, 그 대가로 알림이 남는다.
     * 사용자가 알림에서 바로 버블을 내릴 수 있도록 "숨기기" 액션을 둔다.
     */
    fun buildFloatingBubble(): Notification =
        Notification
            .Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.presence_video_online)
            .setContentTitle(context.getString(R.string.floating_notification_title))
            .setContentText(context.getString(R.string.floating_notification_text))
            .setOngoing(true)
            .addAction(
                Notification.Action
                    .Builder(
                        null,
                        context.getString(R.string.floating_notification_hide),
                        PendingIntent.getService(
                            context,
                            FloatingCaptureService.ACTION_HIDE.hashCode(),
                            FloatingCaptureService.hideIntent(context),
                            PendingIntent.FLAG_IMMUTABLE,
                        ),
                    ).build(),
            ).build()

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
