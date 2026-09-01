package io.rami.screenrecorder.service

import android.content.Context
import android.widget.Toast
import io.rami.screenrecorder.core.common.time.DurationFormatter
import io.rami.screenrecorder.domain.model.AutoStopReason
import io.rami.screenrecorder.domain.model.MicrophoneDevice
import io.rami.screenrecorder.domain.model.Recording
import io.rami.screenrecorder.domain.model.RecordingSessionEvent
import io.rami.screenrecorder.domain.model.RecordingState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.dropWhile

/**
 * 세션 상태와 이벤트를 알림·오버레이로 옮긴다 (기능명세서 6.1, 11절).
 *
 * 서비스는 수명과 인텐트를 다루고, "무엇을 보여 줄지"는 여기가 정한다. 자동 중지 사유처럼
 * 여러 흐름에 걸친 상태도 여기 모인다 — 서비스에 두면 수명 관리와 표시 규칙이 섞인다.
 *
 * @param onIdle 세션이 끝나 서비스를 접어도 될 때.
 * @param onSkipCountdown 카운트다운 오버레이를 탭했을 때.
 */
internal class RecordingSessionPresenter(
    private val context: Context,
    private val notifications: RecordingNotifications,
    private val countdownOverlay: CountdownOverlayWindow,
    private val onIdle: () -> Unit,
    private val onSkipCountdown: () -> Unit,
) {
    /** 자동 중지 사유. 완료 알림 문구에 쓰려고 발행이 끝날 때까지 들고 있는다. */
    private var autoStopReason: AutoStopReason? = null

    /**
     * 발행이 끝나면 완료를 알린다 (기능명세서 6.1절 [결정]).
     *
     * 저장할 내용이 없던 세션은 이 흐름에 아무것도 흘리지 않으므로 알림도 없다.
     */
    suspend fun observeCompletion(completed: Flow<Recording>) {
        completed.collectLatest {
            context.showCompletedNotification(context.completedText(autoStopReason))
            autoStopReason = null
        }
    }

    /** 마지막으로 띄운 저장 중 문구. 같은 문구를 다시 알리지 않으려고 기억한다. */
    private var lastSavingText: String? = null

    suspend fun observeState(states: Flow<RecordingState>) {
        // 세션 시작 전의 초기 Idle은 종료 신호가 아니다 (병렬 구독 레이스 방지).
        states.dropWhile { it is RecordingState.Idle }.collectLatest { state ->
            when (state) {
                is RecordingState.CountingDown -> {
                    countdownOverlay.show(state.remainingSeconds, onSkip = onSkipCountdown)
                    // 이 구간의 일시정지는 아무 일도 하지 않는다 (명세 6.1절 [결정]).
                    context.ongoingNotificationText(state)?.let { notifications.showLimited(it, stoppable = true) }
                }

                // 준비 구간에는 중지도 두지 않는다. 카운트다운과 달리 취소할 대상이 없다 —
                // 세션이 만들어지는 중이라 코디네이터의 중지가 아무 일도 하지 않는다
                // (기능명세서 6.1절 [결정]).
                is RecordingState.Preparing -> {
                    countdownOverlay.dismiss()
                    context.ongoingNotificationText(state)?.let { notifications.showLimited(it, stoppable = false) }
                }

                is RecordingState.Recording, is RecordingState.Paused -> {
                    countdownOverlay.dismiss()
                    context.ongoingNotificationText(state)?.let {
                        notifications.updateOngoing(it, isPaused = state is RecordingState.Paused)
                    }
                }

                // 발행은 취소할 수 없으므로 중지·일시정지 버튼을 주지 않는다 (명세 6.1절 [결정]).
                is RecordingState.Stopping -> {
                    countdownOverlay.dismiss()
                    // 진행률은 0.5% 단위로 올라오지만 문구는 1% 단위다. 걸러 내지 않으면
                    // 발행 2~4분 동안 같은 문자열로 수백 번 알림을 다시 만든다.
                    context.ongoingNotificationText(state)?.let { text ->
                        if (text != lastSavingText) {
                            lastSavingText = text
                            notifications.showLimited(text, stoppable = false)
                        }
                    }
                }

                is RecordingState.Idle -> {
                    countdownOverlay.dismiss()
                    lastSavingText = null
                    onIdle()
                }
            }
        }
    }

    /** 예고/자동 중지 이벤트를 알림으로 반영한다 (기능명세서 11절). */
    suspend fun observeEvents(events: Flow<RecordingSessionEvent>) {
        events.collectLatest { event ->
            when (event) {
                is RecordingSessionEvent.TimeLimitWarning ->
                    notifications.updateOngoing(
                        context.getString(
                            R.string.recording_notification_time_limit_warning,
                            DurationFormatter.formatElapsed(event.remaining),
                        ),
                        isPaused = false,
                    )

                is RecordingSessionEvent.PauseTimeoutWarning ->
                    notifications.updateOngoing(
                        context.getString(
                            R.string.recording_notification_pause_timeout_warning,
                            DurationFormatter.formatElapsed(event.remaining),
                        ),
                        isPaused = true,
                    )

                // 완료 알림은 발행이 끝난 뒤에 띄운다 (기능명세서 6.1절 [결정]). 여기서 띄우면
                // 발행이 시작도 전인데 "완료"가 뜨고, 발행 중 죽으면 없는 파일의 완료만 남는다.
                is RecordingSessionEvent.AutoStopped -> autoStopReason = event.reason

                is RecordingSessionEvent.MicrophoneFellBack -> context.showMicrophoneFallback(event.requested)

                // 완료 알림과 같은 자리에 띄운다 — 사용자는 대개 다른 앱을 쓰는 중이다.
                is RecordingSessionEvent.SaveFailed ->
                    context.showCompletedNotification(context.getString(R.string.recording_notification_save_failed))

                is RecordingSessionEvent.RegionInvalidatedByRotation ->
                    // 명세 5절 [결정]: "영역을 다시 지정하거나 중지하세요"
                    notifications.updateOngoing(
                        context.getString(R.string.recording_notification_region_rotated),
                        isPaused = true,
                    )
            }
        }
    }
}

/**
 * 선택한 마이크를 쓸 수 없을 때 알린다 (기능명세서 4.2절 [결정]).
 *
 * 다른 앱 위에서 녹화를 시작했을 수도 있으므로 앱 내 스낵바가 아닌 토스트를 쓴다.
 * 진행 알림 문구는 경과 시간 갱신으로 곧 덮이므로 쓰지 않는다.
 *
 * 화면 녹화 세션과 음성 전용 녹음이 같은 안내를 쓰므로 확장 함수로 둔다.
 */
internal fun Context.showMicrophoneFallback(requested: MicrophoneDevice) {
    val message =
        getString(
            R.string.recording_microphone_fell_back,
            getString(microphoneDeviceNameRes(requested)),
        )
    // 이벤트 수집은 백그라운드 디스패처에서 일어나므로 토스트는 메인 스레드로 올린다.
    mainExecutor.execute { Toast.makeText(this, message, Toast.LENGTH_LONG).show() }
}

/** 폴백 안내에 쓸 마이크 장치 이름. 자동은 폴백 대상이 아니므로 나타나지 않는다. */
private fun microphoneDeviceNameRes(device: MicrophoneDevice): Int =
    when (device) {
        MicrophoneDevice.BUILT_IN, MicrophoneDevice.AUTO -> R.string.microphone_device_built_in
        MicrophoneDevice.BLUETOOTH -> R.string.microphone_device_bluetooth
        MicrophoneDevice.WIRED -> R.string.microphone_device_wired
    }
