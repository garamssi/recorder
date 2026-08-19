package io.rami.screenrecorder.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import dagger.hilt.android.AndroidEntryPoint
import io.rami.screenrecorder.core.common.time.DurationFormatter
import io.rami.screenrecorder.domain.model.RecordingConfig
import io.rami.screenrecorder.domain.model.RecordingState
import io.rami.screenrecorder.domain.usecase.ObserveRecordingStateUseCase
import io.rami.screenrecorder.domain.usecase.StartRecordingUseCase
import io.rami.screenrecorder.domain.usecase.StopRecordingUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * mediaProjection 타입 Foreground Service (기능명세서 11절).
 *
 * Android 14+에서는 startForeground 이후에만 MediaProjection을 열 수 있으므로,
 * 녹화 세션의 시작/중지는 반드시 이 서비스를 경유한다.
 * 알림 액션(일시정지/재개/타이머 병기)의 완성은 Stage 5에서 진행한다.
 */
@AndroidEntryPoint
class RecordingForegroundService : Service() {
    @Inject lateinit var startRecording: StartRecordingUseCase

    @Inject lateinit var stopRecording: StopRecordingUseCase

    @Inject lateinit var observeRecordingState: ObserveRecordingStateUseCase

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        when (intent?.action) {
            ACTION_START -> handleStart()
            ACTION_STOP -> handleStop()
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun handleStart() {
        startForeground(
            NOTIFICATION_ID,
            buildNotification(getString(R.string.recording_notification_preparing)),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION,
        )
        serviceScope.launch {
            val result = startRecording(RecordingConfig.DEFAULT)
            if (result.isFailure) {
                stopSelf()
                return@launch
            }
            observeStateForNotification()
        }
    }

    private fun handleStop() {
        serviceScope.launch {
            stopRecording()
            stopSelf()
        }
    }

    private suspend fun observeStateForNotification() {
        observeRecordingState().collectLatest { state ->
            when (state) {
                is RecordingState.Recording ->
                    updateNotification(
                        getString(
                            R.string.recording_notification_elapsed,
                            DurationFormatter.formatElapsed(state.elapsed),
                        ),
                    )

                is RecordingState.Idle -> stopSelf()
                else -> Unit
            }
        }
    }

    private fun updateNotification(contentText: String) {
        getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID, buildNotification(contentText))
    }

    private fun buildNotification(contentText: String): Notification {
        val stopIntent =
            PendingIntent.getService(
                this,
                STOP_REQUEST_CODE,
                Intent(this, RecordingForegroundService::class.java).setAction(ACTION_STOP),
                PendingIntent.FLAG_IMMUTABLE,
            )
        return Notification
            .Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.presence_video_online)
            .setContentTitle(getString(R.string.recording_notification_title))
            .setContentText(contentText)
            .setOngoing(true)
            .addAction(
                Notification.Action
                    .Builder(null, getString(R.string.recording_notification_stop), stopIntent)
                    .build(),
            ).build()
    }

    private fun createNotificationChannel() {
        val channel =
            NotificationChannel(
                CHANNEL_ID,
                getString(R.string.recording_notification_channel),
                NotificationManager.IMPORTANCE_LOW,
            )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    companion object {
        private const val CHANNEL_ID = "recording"
        private const val NOTIFICATION_ID = 1
        private const val STOP_REQUEST_CODE = 1

        private const val ACTION_START = "io.rami.screenrecorder.action.START_RECORDING"
        private const val ACTION_STOP = "io.rami.screenrecorder.action.STOP_RECORDING"

        /** 녹화 시작 인텐트 (동의 토큰은 TokenHolder에 먼저 보관되어 있어야 한다). */
        fun startIntent(context: Context): Intent =
            Intent(context, RecordingForegroundService::class.java).setAction(ACTION_START)

        /** 녹화 중지 인텐트. */
        fun stopIntent(context: Context): Intent =
            Intent(context, RecordingForegroundService::class.java).setAction(ACTION_STOP)
    }
}
