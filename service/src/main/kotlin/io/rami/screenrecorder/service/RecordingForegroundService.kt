package io.rami.screenrecorder.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import dagger.hilt.android.AndroidEntryPoint
import io.rami.screenrecorder.domain.model.AudioSource
import io.rami.screenrecorder.domain.model.CaptureMode
import io.rami.screenrecorder.domain.model.CaptureModeKind
import io.rami.screenrecorder.domain.model.CaptureRegion
import io.rami.screenrecorder.domain.repository.RecordingSessionRepository
import io.rami.screenrecorder.domain.usecase.CaptureScreenshotUseCase
import io.rami.screenrecorder.domain.usecase.DiscardPendingConsentUseCase
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

    @Inject lateinit var discardPendingConsent: DiscardPendingConsentUseCase

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val notifications by lazy { RecordingNotifications(this) }

    private val countdownOverlay by lazy { CountdownOverlayWindow(this) }

    /** 무엇을 보여 줄지는 여기가 정한다. 서비스는 수명과 인텐트만 다룬다. */
    private val presenter by lazy {
        RecordingSessionPresenter(
            context = this,
            notifications = notifications,
            countdownOverlay = countdownOverlay,
            onIdle = ::stopSelf,
            onSkipCountdown = skipCountdown::invoke,
        )
    }

    private var completionObserverJob: kotlinx.coroutines.Job? = null

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
        // 조용히 버리면 사용자는 MediaProjection 동의까지 하고 아무 일도 안 일어나는 것을 본다.
        // 쓰이지 않은 동의 토큰도 남으므로 함께 비운다 (기능명세서 6.1절 [결정], CLAUDE.md 7절).
        if (stateObserverJob?.isActive == true) {
            discardPendingConsent()
            quickCapture.notifyBusy()
            return
        }
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
        stateObserverJob = serviceScope.launch { presenter.observeState(observeRecordingState()) }
        eventObserverJob = serviceScope.launch { presenter.observeEvents(sessionRepository.sessionEvents) }
        completionObserverJob =
            serviceScope.launch { presenter.observeCompletion(sessionRepository.completedRecordings) }
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
