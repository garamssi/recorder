package io.rami.screenrecorder.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import dagger.hilt.android.AndroidEntryPoint
import io.rami.screenrecorder.core.common.time.DurationFormatter
import io.rami.screenrecorder.domain.model.RecordingState
import io.rami.screenrecorder.domain.model.TimeLimit
import io.rami.screenrecorder.domain.model.VoiceRecordingState
import io.rami.screenrecorder.domain.usecase.ObserveRecordingStateUseCase
import io.rami.screenrecorder.domain.usecase.ObserveTimeLimitUseCase
import io.rami.screenrecorder.domain.usecase.ObserveVoiceRecordingStateUseCase
import io.rami.screenrecorder.domain.usecase.SetTimeLimitUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 플로팅 캡처 버블을 띄워 두는 포그라운드 서비스 (기능명세서 11.1절).
 *
 * 버블은 다른 앱 위에 떠 있어야 하므로 앱이 백그라운드로 가도 살아 있어야 한다.
 * Android 8+의 백그라운드 실행 제한 때문에 일반 서비스로는 유지할 수 없어
 * `specialUse` 타입 포그라운드 서비스로 둔다 (녹화 세션과는 별개의 서비스다).
 */
@AndroidEntryPoint
class FloatingCaptureService : Service() {
    @Inject lateinit var observeRecordingState: ObserveRecordingStateUseCase

    @Inject lateinit var observeVoiceRecordingState: ObserveVoiceRecordingStateUseCase

    @Inject lateinit var observeTimeLimit: ObserveTimeLimitUseCase

    @Inject lateinit var setTimeLimit: SetTimeLimitUseCase

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val notifications by lazy { RecordingNotifications(this) }
    private val bubble by lazy { FloatingCaptureBubble(this) }
    private val timeLimitInput by lazy { TimeLimitInputWindow(this) }

    // 입력 창을 열 때 미리 채울 값. 관찰 스트림이 갱신하므로 별도 조회가 필요 없다.
    private var currentTimeLimit: TimeLimit = TimeLimit.None
    private val mainHandler = Handler(Looper.getMainLooper())
    private var stateObserverJob: kotlinx.coroutines.Job? = null

    override fun onCreate() {
        super.onCreate()
        notifications.createChannel()
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        if (intent?.action == ACTION_HIDE || !Settings.canDrawOverlays(this)) {
            stopSelf()
            return START_NOT_STICKY
        }
        startForeground(
            NOTIFICATION_ID,
            notifications.buildFloatingBubble(),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
        )
        showBubble()
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        mainHandler.post {
            timeLimitInput.dismiss()
            bubble.dismiss()
        }
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun showBubble() {
        // 중복 START는 무시된다 (show가 이미 떠 있으면 아무것도 하지 않는다).
        bubble.show(BubbleActionDelegate(this, onEditTimeLimit = ::showTimeLimitInput))
        // 구독도 한 번만 — START가 반복돼도 수집기가 겹쳐 같은 상태를 여러 번 그리지 않게 한다.
        if (stateObserverJob?.isActive == true) return
        stateObserverJob = serviceScope.launch { observeCaptureState() }
    }

    /**
     * 진행 중인 캡처와 설정에 따라 버블 모양을 바꾼다.
     *
     * 시간 제한은 유휴 메뉴가 현재 값을 보여 줘야 해서 함께 구독한다 (기능명세서 11.4절).
     */
    private suspend fun observeCaptureState() {
        combine(
            observeRecordingState(),
            observeVoiceRecordingState(),
            observeTimeLimit(),
        ) { screen, voice, timeLimit ->
            currentTimeLimit = timeLimit
            bubbleStateFor(screen, voice, settingTimeLimit = timeLimit)
        }.collect(bubble::render)
    }

    /** 시간 제한 직접 입력 창을 띄우고, 확정된 값을 설정에 저장한다. */
    private fun showTimeLimitInput() {
        timeLimitInput.show(currentTimeLimit) { limit ->
            serviceScope.launch { setTimeLimit(limit) }
        }
    }

    companion object {
        /** 플로팅 버블 알림 ID (녹화 알림과 분리). */
        const val NOTIFICATION_ID = 3

        internal const val ACTION_HIDE = "io.rami.screenrecorder.action.HIDE_FLOATING_BUBBLE"

        /** 버블을 띄운다. 오버레이 권한이 없으면 서비스가 즉시 스스로 멈춘다. */
        fun startIntent(context: Context): Intent = Intent(context, FloatingCaptureService::class.java)

        /** 버블을 내린다 (설정 끄기 또는 알림의 "숨기기"). */
        fun hideIntent(context: Context): Intent =
            Intent(context, FloatingCaptureService::class.java).setAction(ACTION_HIDE)
    }
}

/**
 * 캡처 상태를 버블이 그릴 모양으로 옮긴다 (기능명세서 11.1·11.4절).
 *
 * 진행 중인 세션의 시간 제한은 [screen]이 들고 있는 값을 쓴다. 설정([settingTimeLimit])은
 * 녹화 중에도 바뀔 수 있지만 세션을 멈출 시각은 시작할 때 정해지므로, 설정을 따라가면
 * 남은 시간을 잘못 알려 주게 된다.
 *
 * @param settingTimeLimit 유휴 상태의 펼침 메뉴가 보여 줄 현재 설정값.
 */
internal fun bubbleStateFor(
    screen: RecordingState,
    voice: VoiceRecordingState,
    settingTimeLimit: TimeLimit,
): BubbleState =
    when {
        screen is RecordingState.Recording ->
            BubbleState.ScreenRecording(
                elapsedWithLimit(screen.elapsed, screen.timeLimit),
                isPaused = false,
            )

        screen is RecordingState.Paused ->
            BubbleState.ScreenRecording(
                elapsedWithLimit(screen.elapsed, screen.timeLimit),
                isPaused = true,
            )

        voice is VoiceRecordingState.Recording ->
            BubbleState.VoiceRecording(DurationFormatter.formatElapsed(voice.elapsed))

        else -> BubbleState.Idle(settingTimeLimit)
    }

/**
 * "경과" 또는 "경과 / 제한" (기능명세서 11.4절: 예 "03:24 / 10:00").
 *
 * 알림과 같은 표기를 쓴다. 구분 기호뿐이라 번역할 문구가 없어 문자열 리소스를 두지 않는다.
 */
private fun elapsedWithLimit(
    elapsed: kotlin.time.Duration,
    timeLimit: TimeLimit,
): String {
    val elapsedText = DurationFormatter.formatElapsed(elapsed)
    if (timeLimit !is TimeLimit.Limited) return elapsedText
    return "$elapsedText / ${DurationFormatter.formatElapsed(timeLimit.duration)}"
}

/**
 * 버블 탭을 실제 동작으로 옮긴다.
 *
 * MediaProjection 동의는 Activity에서만 받을 수 있으므로, 화면 녹화·화면 캡처는
 * 투명 트램폴린 액티비티를 띄운다. 음성 녹음은 동의가 필요 없어 서비스로 바로 보낸다.
 */
private class BubbleActionDelegate(
    private val context: Context,
    private val onEditTimeLimit: () -> Unit,
) : BubbleActions {
    override fun onStartRecording() = launchConsent(CONSENT_ACTION_RECORD)

    override fun onCaptureScreenshot() = launchConsent(CONSENT_ACTION_SCREENSHOT)

    override fun onEditTimeLimit() = onEditTimeLimit.invoke()

    override fun onStopRecording() {
        context.startService(RecordingForegroundService.stopIntent(context))
    }

    override fun onPauseRecording() {
        context.startService(RecordingForegroundService.pauseIntent(context))
    }

    override fun onResumeRecording() {
        context.startService(RecordingForegroundService.resumeIntent(context))
    }

    override fun onStartVoiceRecording() {
        context.startForegroundService(RecordingForegroundService.startVoiceIntent(context))
    }

    override fun onStopVoiceRecording() {
        context.startService(RecordingForegroundService.stopVoiceIntent(context))
    }

    /**
     * 앱 화면을 연다.
     *
     * service 모듈은 app 모듈을 참조할 수 없으므로 런처 인텐트로 실행한다 (알림 탭과 같은 경로).
     */
    override fun onOpenApp() {
        val launchIntent =
            context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return
        context.startActivity(launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private fun launchConsent(action: String) {
        val intent =
            Intent(action)
                .setPackage(context.packageName)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    private companion object {
        /** app 모듈의 투명 동의 액티비티가 받는 액션 (manifest의 intent-filter와 일치해야 한다). */
        const val CONSENT_ACTION_RECORD = "io.rami.screenrecorder.action.CONSENT_RECORD"
        const val CONSENT_ACTION_SCREENSHOT = "io.rami.screenrecorder.action.CONSENT_SCREENSHOT"
    }
}
