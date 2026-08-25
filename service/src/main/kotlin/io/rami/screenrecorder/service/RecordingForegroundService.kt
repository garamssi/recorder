package io.rami.screenrecorder.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.widget.Toast
import dagger.hilt.android.AndroidEntryPoint
import io.rami.screenrecorder.core.common.time.DurationFormatter
import io.rami.screenrecorder.domain.model.AudioSource
import io.rami.screenrecorder.domain.model.CaptureMode
import io.rami.screenrecorder.domain.model.CaptureModeKind
import io.rami.screenrecorder.domain.model.CaptureRegion
import io.rami.screenrecorder.domain.model.MicrophoneDevice
import io.rami.screenrecorder.domain.model.RecordingSessionEvent
import io.rami.screenrecorder.domain.model.RecordingState
import io.rami.screenrecorder.domain.repository.RecordingSessionRepository
import io.rami.screenrecorder.domain.usecase.CaptureScreenshotUseCase
import io.rami.screenrecorder.domain.usecase.ObserveRecordingStateUseCase
import io.rami.screenrecorder.domain.usecase.ObserveVoiceMicrophoneFallbackUseCase
import io.rami.screenrecorder.domain.usecase.ObserveVoiceRecordingStateUseCase
import io.rami.screenrecorder.domain.usecase.PauseRecordingUseCase
import io.rami.screenrecorder.domain.usecase.ResumeRecordingUseCase
import io.rami.screenrecorder.domain.usecase.SkipCountdownUseCase
import io.rami.screenrecorder.domain.usecase.StartRecordingUseCase
import io.rami.screenrecorder.domain.usecase.StartVoiceRecordingUseCase
import io.rami.screenrecorder.domain.usecase.StopRecordingUseCase
import io.rami.screenrecorder.domain.usecase.StopVoiceRecordingUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.dropWhile
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

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

    @Inject lateinit var captureScreenshot: CaptureScreenshotUseCase

    @Inject lateinit var startVoiceRecording: StartVoiceRecordingUseCase

    @Inject lateinit var stopVoiceRecording: StopVoiceRecordingUseCase

    @Inject lateinit var observeVoiceRecordingState: ObserveVoiceRecordingStateUseCase

    @Inject lateinit var observeVoiceMicrophoneFallback: ObserveVoiceMicrophoneFallbackUseCase

    @Inject lateinit var skipCountdown: SkipCountdownUseCase

    @Inject lateinit var observeSettings: io.rami.screenrecorder.domain.usecase.ObserveSettingsUseCase

    @Inject lateinit var sessionRepository: RecordingSessionRepository

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val notifications by lazy { RecordingNotifications(this) }

    private val countdownOverlay by lazy { CountdownOverlayWindow(this) }

    private val quickCapture by lazy {
        QuickCaptureRunner(
            service = this,
            notifications = notifications,
            scope = serviceScope,
            useCases =
                QuickCaptureUseCases(
                    captureScreenshot = captureScreenshot,
                    startVoiceRecording = startVoiceRecording,
                    stopVoiceRecording = stopVoiceRecording,
                    observeVoiceRecordingState = observeVoiceRecordingState,
                ),
        )
    }

    private var stateObserverJob: kotlinx.coroutines.Job? = null
    private var eventObserverJob: kotlinx.coroutines.Job? = null

    override fun onCreate() {
        super.onCreate()
        notifications.createChannel()
        // 음성 전용 녹음은 세션 이벤트 스트림을 쓰지 않으므로 별도로 관찰한다.
        serviceScope.launch {
            observeVoiceMicrophoneFallback().collect(::showMicrophoneFallback)
        }
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        when (intent?.action) {
            ACTION_START -> handleStart(readRegion(intent))
            ACTION_STOP -> serviceScope.launch { stopRecording() }
            ACTION_PAUSE -> serviceScope.launch { pauseRecording() }
            ACTION_RESUME -> serviceScope.launch { resumeRecording() }
            ACTION_SCREENSHOT -> startQuickCapture(quickCapture::captureScreenshot)
            ACTION_START_VOICE -> startQuickCapture(quickCapture::startVoiceRecording)
            // 음성 녹음 중일 때만 처리한다 — 오래된 알림의 중지 버튼이 진행 중인 화면 녹화를 죽이지 않게.
            ACTION_STOP_VOICE -> if (quickCapture.isVoiceRecording) quickCapture.stopVoiceRecording()
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    /**
     * 화면 녹화·다른 캡처가 진행 중이 아닐 때만 짧은 캡처 작업을 시작한다.
     *
     * MediaProjection 세션과 마이크는 동시에 하나만 쓸 수 있어 겹치면 조용히 실패하는 대신 안내한다.
     */
    private fun startQuickCapture(action: () -> Unit) {
        if (stateObserverJob?.isActive == true || quickCapture.isVoiceRecording) {
            quickCapture.notifyBusy()
            return
        }
        action()
    }

    override fun onDestroy() {
        countdownOverlay.dismiss()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun handleStart(region: CaptureRegion?) {
        // 세션 진행 중 중복 START는 무시한다 — 진행 중 녹화를 stopSelf로 죽이는 사고 방지.
        if (stateObserverJob?.isActive == true) return
        serviceScope.launch {
            // 세션 구성은 설정 저장소가 단일 진실 공급원이다 (기능명세서 2.1절: 마지막 선택 유지).
            val settings = observeSettings().first()
            val config = settings.recording.copy(captureMode = captureMode(settings, region))
            // 녹화 중 플로팅 컨트롤은 FloatingCaptureService의 버블이 상태를 구독해 직접 표시한다
            // (기능명세서 11.1절) — 여기서 별도 버블을 띄우면 두 개가 겹친다.
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
                is RecordingState.CountingDown ->
                    countdownOverlay.show(state.remainingSeconds, onSkip = skipCountdown::invoke)

                is RecordingState.Recording -> {
                    countdownOverlay.dismiss()
                    notifications.updateOngoing(elapsedText(state.timeLimit, state.elapsed), isPaused = false)
                }

                is RecordingState.Paused ->
                    notifications.updateOngoing(
                        getString(
                            R.string.recording_notification_paused,
                            elapsedText(state.timeLimit, state.elapsed),
                        ),
                        isPaused = true,
                    )

                is RecordingState.Idle -> {
                    countdownOverlay.dismiss()
                    stopSelf()
                }

                else -> countdownOverlay.dismiss()
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

                is RecordingSessionEvent.MicrophoneFellBack -> showMicrophoneFallback(event.requested)

                is RecordingSessionEvent.RegionInvalidatedByRotation ->
                    // 명세 5절 [결정]: "영역을 다시 지정하거나 중지하세요"
                    notifications.updateOngoing(
                        getString(R.string.recording_notification_region_rotated),
                        isPaused = true,
                    )
            }
        }
    }

    /**
     * 선택한 마이크를 쓸 수 없을 때 알린다 (기능명세서 4.2절 [결정]).
     *
     * 다른 앱 위에서 녹화를 시작했을 수도 있으므로 앱 내 스낵바가 아닌 토스트를 쓴다.
     * 진행 알림 문구는 경과 시간 갱신으로 곧 덮이므로 쓰지 않는다.
     */
    private fun showMicrophoneFallback(requested: MicrophoneDevice) {
        val message =
            getString(
                R.string.recording_microphone_fell_back,
                getString(microphoneDeviceNameRes(requested)),
            )
        // 이벤트 수집은 백그라운드 디스패처에서 일어나므로 토스트는 메인 스레드로 올린다.
        mainExecutor.execute { Toast.makeText(this, message, Toast.LENGTH_LONG).show() }
    }

    companion object {
        /** 홈에서 선택한 모드 + (부분 영역이면) 오버레이에서 지정한 영역을 세션 모드로 해석한다. */
        internal fun captureMode(
            settings: io.rami.screenrecorder.domain.model.AppSettings,
            region: CaptureRegion?,
        ): CaptureMode =
            when (settings.selectedCaptureMode) {
                CaptureModeKind.FULL_SCREEN -> CaptureMode.FullScreen
                CaptureModeKind.SINGLE_APP -> CaptureMode.SingleApp
                CaptureModeKind.REGION ->
                    if (region != null) {
                        CaptureMode.Region(region)
                    } else {
                        // 영역 없이 시작되면(비정상 경로) 몰래 전체 화면으로 대체하지 않는다.
                        error("부분 영역 모드인데 선택 영역이 없다")
                    }
            }

        internal fun readRegion(intent: Intent): CaptureRegion? {
            if (!intent.hasExtra(EXTRA_REGION_WIDTH)) return null
            return CaptureRegion(
                x = intent.getIntExtra(EXTRA_REGION_X, 0),
                y = intent.getIntExtra(EXTRA_REGION_Y, 0),
                width = intent.getIntExtra(EXTRA_REGION_WIDTH, CaptureRegion.MIN_WIDTH),
                height = intent.getIntExtra(EXTRA_REGION_HEIGHT, CaptureRegion.MIN_HEIGHT),
            )
        }

        private const val ACTION_START = "io.rami.screenrecorder.action.START_RECORDING"
        private const val ACTION_SCREENSHOT = "io.rami.screenrecorder.action.CAPTURE_SCREENSHOT"
        private const val ACTION_START_VOICE = "io.rami.screenrecorder.action.START_VOICE_RECORDING"
        internal const val ACTION_STOP_VOICE = "io.rami.screenrecorder.action.STOP_VOICE_RECORDING"
        internal const val ACTION_STOP = "io.rami.screenrecorder.action.STOP_RECORDING"
        internal const val ACTION_PAUSE = "io.rami.screenrecorder.action.PAUSE_RECORDING"
        internal const val ACTION_RESUME = "io.rami.screenrecorder.action.RESUME_RECORDING"
        private const val EXTRA_REGION_X = "region_x"
        private const val EXTRA_REGION_Y = "region_y"
        private const val EXTRA_REGION_WIDTH = "region_width"
        private const val EXTRA_REGION_HEIGHT = "region_height"

        /** 녹화 시작 인텐트 (동의 토큰은 TokenHolder에, 세션 구성은 설정 저장소에 있어야 한다). */
        fun startIntent(
            context: Context,
            region: CaptureRegion? = null,
        ): Intent =
            Intent(context, RecordingForegroundService::class.java)
                .setAction(ACTION_START)
                .apply {
                    if (region != null) {
                        putExtra(EXTRA_REGION_X, region.x)
                        putExtra(EXTRA_REGION_Y, region.y)
                        putExtra(EXTRA_REGION_WIDTH, region.width)
                        putExtra(EXTRA_REGION_HEIGHT, region.height)
                    }
                }

        /** 녹화 중지 인텐트. */
        fun stopIntent(context: Context): Intent =
            Intent(context, RecordingForegroundService::class.java).setAction(ACTION_STOP)

        /** 일시정지 인텐트 (알림 액션과 동일 경로). */
        fun pauseIntent(context: Context): Intent =
            Intent(context, RecordingForegroundService::class.java).setAction(ACTION_PAUSE)

        /** 재개 인텐트 (알림 액션과 동일 경로). */
        fun resumeIntent(context: Context): Intent =
            Intent(context, RecordingForegroundService::class.java).setAction(ACTION_RESUME)

        /** 화면 캡처 인텐트 (동의 토큰은 TokenHolder에 있어야 한다, 기능명세서 12절). */
        fun screenshotIntent(context: Context): Intent =
            Intent(context, RecordingForegroundService::class.java).setAction(ACTION_SCREENSHOT)

        /** 음성 전용 녹음 시작 인텐트 (기능명세서 13절). */
        fun startVoiceIntent(context: Context): Intent =
            Intent(context, RecordingForegroundService::class.java).setAction(ACTION_START_VOICE)

        /** 음성 전용 녹음 중지 인텐트. */
        fun stopVoiceIntent(context: Context): Intent =
            Intent(context, RecordingForegroundService::class.java).setAction(ACTION_STOP_VOICE)
    }
}

/** 폴백 안내에 쓸 마이크 장치 이름. 자동은 폴백 대상이 아니므로 나타나지 않는다. */
private fun microphoneDeviceNameRes(device: MicrophoneDevice): Int =
    when (device) {
        MicrophoneDevice.BUILT_IN, MicrophoneDevice.AUTO -> R.string.microphone_device_built_in
        MicrophoneDevice.BLUETOOTH -> R.string.microphone_device_bluetooth
        MicrophoneDevice.WIRED -> R.string.microphone_device_wired
    }
