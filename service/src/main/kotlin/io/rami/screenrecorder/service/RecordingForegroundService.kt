package io.rami.screenrecorder.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import dagger.hilt.android.AndroidEntryPoint
import io.rami.screenrecorder.core.common.time.DurationFormatter
import io.rami.screenrecorder.domain.model.AudioSource
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

    private val notifications by lazy { RecordingNotifications(this) }

    override fun onCreate() {
        super.onCreate()
        notifications.createChannel()
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        when (intent?.action) {
            ACTION_START -> handleStart(readAudioSource(intent))
            ACTION_STOP -> handleStop()
        }
        return START_NOT_STICKY
    }

    private fun readAudioSource(intent: Intent): AudioSource =
        intent
            .getStringExtra(EXTRA_AUDIO_SOURCE)
            ?.let { runCatching { AudioSource.valueOf(it) }.getOrNull() }
            ?: AudioSource.INTERNAL

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun handleStart(audioSource: AudioSource) {
        startForeground(
            RecordingNotifications.NOTIFICATION_ID,
            notifications.build(getString(R.string.recording_notification_preparing)),
            foregroundServiceTypes(audioSource),
        )
        serviceScope.launch {
            // TODO(Stage 6): DataStore 설정(해상도/fps/비트레이트/마이크 장치/볼륨 등)을 주입한다.
            //  현재는 명세 4절 기본값에 오디오 소스만 반영한 임시 구성이다.
            val result = startRecording(RecordingConfig.DEFAULT.copy(audioSource = audioSource))
            if (result.isFailure) {
                stopSelf()
                return@launch
            }
            observeStateForNotification()
        }
    }

    /** 마이크를 쓰는 세션은 microphone FGS 타입을 함께 선언해야 한다 (Android 14+). */
    private fun foregroundServiceTypes(audioSource: AudioSource): Int {
        val usesMicrophone =
            audioSource == AudioSource.MICROPHONE ||
                audioSource == AudioSource.INTERNAL_AND_MICROPHONE
        return if (usesMicrophone) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION or
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
        } else {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
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
                    notifications.update(
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

    companion object {
        private const val ACTION_START = "io.rami.screenrecorder.action.START_RECORDING"
        internal const val ACTION_STOP = "io.rami.screenrecorder.action.STOP_RECORDING"
        private const val EXTRA_AUDIO_SOURCE = "audio_source"

        /** 녹화 시작 인텐트 (동의 토큰은 TokenHolder에 먼저 보관되어 있어야 한다). */
        fun startIntent(
            context: Context,
            audioSource: AudioSource = AudioSource.INTERNAL,
        ): Intent =
            Intent(context, RecordingForegroundService::class.java)
                .setAction(ACTION_START)
                .putExtra(EXTRA_AUDIO_SOURCE, audioSource.name)

        /** 녹화 중지 인텐트. */
        fun stopIntent(context: Context): Intent =
            Intent(context, RecordingForegroundService::class.java).setAction(ACTION_STOP)
    }
}
