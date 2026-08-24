package io.rami.screenrecorder.data.audio

import android.media.AudioManager
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.Executor
import kotlin.time.Duration.Companion.seconds

/**
 * [AudioManager] 통신 장치 API 기반 [CommunicationDeviceController].
 *
 * 블루투스 헤드셋 마이크는 SCO/LE 링크가 열려야 입력 장치로 존재한다.
 * `setCommunicationDevice`는 요청만 접수하고 전환은 비동기로 일어나므로,
 * 콜백으로 실제 전환을 확인한 뒤에야 AudioRecord를 만들어야 한다.
 */
class AndroidCommunicationDeviceController(
    private val audioManager: AudioManager,
    private val callbackExecutor: Executor,
) : CommunicationDeviceController {
    override fun available(): List<AudioDeviceRef> =
        audioManager.availableCommunicationDevices.map { AudioDeviceRef(id = it.id, type = it.type) }

    override suspend fun activate(device: AudioDeviceRef): Boolean {
        val target = audioManager.availableCommunicationDevices.firstOrNull { it.id == device.id } ?: return false
        if (!audioManager.setCommunicationDevice(target)) return false
        return awaitActive(device.id)
    }

    override fun clear() {
        audioManager.clearCommunicationDevice()
    }

    private suspend fun awaitActive(deviceId: Int): Boolean {
        val switched = CompletableDeferred<Boolean>()
        val listener =
            AudioManager.OnCommunicationDeviceChangedListener { current ->
                if (current?.id == deviceId) switched.complete(true)
            }
        audioManager.addOnCommunicationDeviceChangedListener(callbackExecutor, listener)
        return try {
            // 리스너 등록 전에 이미 전환됐을 수 있다.
            audioManager.communicationDevice?.id == deviceId ||
                withTimeoutOrNull(ROUTE_ACTIVATION_TIMEOUT) { switched.await() } == true
        } finally {
            audioManager.removeOnCommunicationDeviceChangedListener(listener)
        }
    }

    private companion object {
        /** SCO 링크 수립 대기 한도. 초과하면 폴백을 사용자에게 알린다. */
        val ROUTE_ACTIVATION_TIMEOUT = 3.seconds
    }
}
