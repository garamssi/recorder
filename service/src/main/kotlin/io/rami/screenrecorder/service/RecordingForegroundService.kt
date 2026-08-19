package io.rami.screenrecorder.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import dagger.hilt.android.AndroidEntryPoint
import io.rami.screenrecorder.core.common.time.DurationFormatter
import io.rami.screenrecorder.domain.model.AudioSource
import io.rami.screenrecorder.domain.model.AutoStopReason
import io.rami.screenrecorder.domain.model.RecordingSessionEvent
import io.rami.screenrecorder.domain.model.RecordingState
import io.rami.screenrecorder.domain.model.TimeLimit
import io.rami.screenrecorder.domain.repository.RecordingSessionRepository
import io.rami.screenrecorder.domain.usecase.ObserveRecordingStateUseCase
import io.rami.screenrecorder.domain.usecase.PauseRecordingUseCase
import io.rami.screenrecorder.domain.usecase.ResumeRecordingUseCase
import io.rami.screenrecorder.domain.usecase.StartRecordingUseCase
import io.rami.screenrecorder.domain.usecase.StopRecordingUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.dropWhile
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.time.Duration

/**
 * mediaProjection 타입 Foreground Service (기능명세서 11절).
 *
 * Android 14+에서는 startForeground 이후에만 MediaProjection을 열 수 있으므로,
 * 녹화 세션의 시작/중지는 반드시 이 서비스를 경유한다.
 * 알림: 경과 시간(타이머 병기), 일시정지/재개/중지 액션, 예고/완료 알림.
 */
@AndroidEntryPoint
class RecordingForegroundService : Service() {
    @Inject lateinit var startRecording: StartRecordingUseCase

    @Inject lateinit var stopRecording: StopRecordingUseCase

    @Inject lateinit var pauseRecording: PauseRecordingUseCase

    @Inject lateinit var resumeRecording: ResumeRecordingUseCase

    @Inject lateinit var observeRecordingState: ObserveRecordingStateUseCase

    @Inject lateinit var observeSettings: io.rami.screenrecorder.domain.usecase.ObserveSettingsUseCase

    @Inject lateinit var sessionRepository: RecordingSessionRepository

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val notifications by lazy { RecordingNotifications(this) }

    private var timeLimit: TimeLimit = TimeLimit.None
    private var stateObserverJob: kotlinx.coroutines.Job? = null
    private var eventObserverJob: kotlinx.coroutines.Job? = null

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
            ACTION_START -> handleStart()
            ACTION_STOP -> serviceScope.launch { stopRecording() }
            ACTION_PAUSE -> serviceScope.launch { pauseRecording() }
            ACTION_RESUME -> serviceScope.launch { resumeRecording() }
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun handleStart() {
        // 세션 진행 중 중복 START는 무시한다 — 진행 중 녹화를 stopSelf로 죽이는 사고 방지.
        if (stateObserverJob?.isActive == true) return
        serviceScope.launch {
            // 세션 구성은 설정 저장소가 단일 진실 공급원이다 (기능명세서 2.1절: 마지막 선택 유지).
            val config = observeSettings().first().recording
            timeLimit = config.timeLimit
            startForeground(
                RecordingNotifications.NOTIFICATION_ID,
                notifications.buildOngoing(
                    getString(R.string.recording_notification_preparing),
                    isPaused = false,
                ),
                foregroundServiceTypes(config.audioSource),
            )
            val result = startRecording(config)
            if (result.isFailure) {
                stopSelf()
                return@launch
            }
        }
        stateObserverJob = serviceScope.launch { observeStateForNotification() }
        eventObserverJob = serviceScope.launch { observeSessionEvents() }
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

    private suspend fun observeStateForNotification() {
        // 세션 시작 전의 초기 Idle은 종료 신호가 아니다 (병렬 구독 레이스 방지).
        observeRecordingState().dropWhile { it is RecordingState.Idle }.collectLatest { state ->
            when (state) {
                is RecordingState.Recording ->
                    notifications.updateOngoing(elapsedText(state.elapsed), isPaused = false)

                is RecordingState.Paused ->
                    notifications.updateOngoing(
                        getString(R.string.recording_notification_paused, elapsedText(state.elapsed)),
                        isPaused = true,
                    )

                is RecordingState.Idle -> stopSelf()
                else -> Unit
            }
        }
    }

    /** 예고/자동 중지 이벤트를 알림으로 반영한다 (기능명세서 11절). */
    private suspend fun observeSessionEvents() {
        sessionRepository.sessionEvents.collectLatest { event ->
            when (event) {
                is RecordingSessionEvent.TimeLimitWarning ->
                    notifications.updateOngoing(
                        getString(
                            R.string.recording_notification_time_limit_warning,
                            DurationFormatter.formatElapsed(event.remaining),
                        ),
                        isPaused = false,
                    )

                is RecordingSessionEvent.PauseTimeoutWarning ->
                    notifications.updateOngoing(
                        getString(
                            R.string.recording_notification_pause_timeout_warning,
                            DurationFormatter.formatElapsed(event.remaining),
                        ),
                        isPaused = true,
                    )

                is RecordingSessionEvent.AutoStopped ->
                    notifications.showCompleted(autoStopText(event.reason))
            }
        }
    }

    /** 경과 시간 표시. 시간 제한이 있으면 "경과 / 제한"으로 병기한다 (기능명세서 11.4절). */
    private fun elapsedText(elapsed: Duration): String {
        val elapsedFormatted = DurationFormatter.formatElapsed(elapsed)
        val limit = timeLimit
        return if (limit is TimeLimit.Limited) {
            getString(
                R.string.recording_notification_elapsed_with_limit,
                elapsedFormatted,
                DurationFormatter.formatElapsed(limit.duration),
            )
        } else {
            getString(R.string.recording_notification_elapsed, elapsedFormatted)
        }
    }

    private fun autoStopText(reason: AutoStopReason): String =
        when (reason) {
            AutoStopReason.TIME_LIMIT_REACHED ->
                getString(R.string.recording_notification_completed_time_limit)

            AutoStopReason.STORAGE_LOW ->
                getString(R.string.recording_notification_completed_storage_low)

            AutoStopReason.PAUSE_TIMEOUT ->
                getString(R.string.recording_notification_completed_pause_timeout)
        }

    companion object {
        private const val ACTION_START = "io.rami.screenrecorder.action.START_RECORDING"
        internal const val ACTION_STOP = "io.rami.screenrecorder.action.STOP_RECORDING"
        internal const val ACTION_PAUSE = "io.rami.screenrecorder.action.PAUSE_RECORDING"
        internal const val ACTION_RESUME = "io.rami.screenrecorder.action.RESUME_RECORDING"

        /** 녹화 시작 인텐트 (동의 토큰은 TokenHolder에, 세션 구성은 설정 저장소에 있어야 한다). */
        fun startIntent(context: Context): Intent =
            Intent(context, RecordingForegroundService::class.java).setAction(ACTION_START)

        /** 녹화 중지 인텐트. */
        fun stopIntent(context: Context): Intent =
            Intent(context, RecordingForegroundService::class.java).setAction(ACTION_STOP)

        /** 일시정지 인텐트 (알림 액션과 동일 경로). */
        fun pauseIntent(context: Context): Intent =
            Intent(context, RecordingForegroundService::class.java).setAction(ACTION_PAUSE)

        /** 재개 인텐트 (알림 액션과 동일 경로). */
        fun resumeIntent(context: Context): Intent =
            Intent(context, RecordingForegroundService::class.java).setAction(ACTION_RESUME)
    }
}
