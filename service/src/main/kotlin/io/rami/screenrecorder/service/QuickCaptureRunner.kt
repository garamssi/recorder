package io.rami.screenrecorder.service

import android.app.Service
import android.content.pm.ServiceInfo
import io.rami.screenrecorder.core.common.time.DurationFormatter
import io.rami.screenrecorder.domain.model.VoiceRecordingState
import io.rami.screenrecorder.domain.usecase.CaptureScreenshotUseCase
import io.rami.screenrecorder.domain.usecase.ObserveVoiceRecordingStateUseCase
import io.rami.screenrecorder.domain.usecase.StartVoiceRecordingUseCase
import io.rami.screenrecorder.domain.usecase.StopVoiceRecordingUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * 짧은 캡처 작업(화면 캡처 1장, 음성 전용 녹음)을 포그라운드 서비스 안에서 실행한다.
 *
 * 화면 녹화와 달리 상태 기계가 없고 시작-저장-종료로 끝나므로 서비스 본체와 분리한다
 * (기능명세서 12, 13절).
 */
internal class QuickCaptureRunner(
    private val service: Service,
    private val notifications: RecordingNotifications,
    private val scope: CoroutineScope,
    private val useCases: QuickCaptureUseCases,
) {
    private var voiceObserverJob: Job? = null

    /** 음성 녹음이 진행 중인지 (다른 캡처 시작을 막는 데 쓴다). */
    val isVoiceRecording: Boolean get() = voiceObserverJob?.isActive == true

    /**
     * 화면 한 장을 캡처해 저장한다.
     *
     * MediaProjection은 startForeground 이후에만 열 수 있어 잠시 포그라운드로 올렸다가
     * 저장이 끝나면 서비스를 내린다.
     */
    fun captureScreenshot() {
        scope.launch {
            service.startForeground(
                RecordingNotifications.NOTIFICATION_ID,
                notifications.buildQuickCapture(
                    contentText = service.getString(R.string.screenshot_notification_capturing),
                    stoppable = false,
                ),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION,
            )
            val result = useCases.captureScreenshot()
            result.exceptionOrNull()?.let { failure ->
                android.util.Log.w(LOG_TAG, "화면 캡처 실패", failure)
            }
            service.showCompletedNotification(
                service.getString(
                    if (result.isSuccess) {
                        R.string.screenshot_notification_saved
                    } else {
                        R.string.screenshot_notification_failed
                    },
                ),
            )
            service.stopSelf()
        }
    }

    /** 마이크 녹음을 시작하고 경과 시간을 알림에 반영한다. */
    fun startVoiceRecording() {
        scope.launch {
            service.startForeground(
                RecordingNotifications.NOTIFICATION_ID,
                notifications.buildQuickCapture(
                    contentText = service.getString(R.string.voice_notification_recording),
                    stoppable = true,
                ),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE,
            )
            if (useCases.startVoiceRecording().isFailure) service.stopSelf()
        }
        voiceObserverJob = scope.launch { observeElapsed() }
    }

    /** 녹음을 중지하고 저장 결과를 알린 뒤 서비스를 내린다. */
    fun stopVoiceRecording() {
        scope.launch {
            val memo = useCases.stopVoiceRecording().getOrNull()
            voiceObserverJob?.cancel()
            voiceObserverJob = null
            service.showCompletedNotification(
                service.getString(
                    if (memo != null) R.string.voice_notification_saved else R.string.voice_notification_empty,
                ),
            )
            service.stopSelf()
        }
    }

    /** 다른 캡처가 이미 돌고 있어 시작할 수 없음을 알린다. */
    fun notifyBusy() {
        service.showBusyNotification(service.getString(R.string.capture_busy_recording))
    }

    private suspend fun observeElapsed() {
        useCases.observeVoiceRecordingState().collectLatest { state ->
            if (state is VoiceRecordingState.Recording) {
                notifications.updateQuickCapture(
                    service.getString(
                        R.string.voice_notification_elapsed,
                        DurationFormatter.formatElapsed(state.elapsed),
                    ),
                )
            }
        }
    }

    private companion object {
        const val LOG_TAG = "QuickCaptureRunner"
    }
}

/** [QuickCaptureRunner]가 쓰는 유스케이스 묶음 (DI 조립 단순화용). */
internal class QuickCaptureUseCases(
    val captureScreenshot: CaptureScreenshotUseCase,
    val startVoiceRecording: StartVoiceRecordingUseCase,
    val stopVoiceRecording: StopVoiceRecordingUseCase,
    val observeVoiceRecordingState: ObserveVoiceRecordingStateUseCase,
)
