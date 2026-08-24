package io.rami.screenrecorder.data.audio

import android.media.AudioDeviceInfo
import io.rami.screenrecorder.domain.model.MicrophoneDevice

/** 라우팅 대상 오디오 장치의 최소 식별 정보. AudioManager 타입에 테스트가 묶이지 않게 분리한다. */
data class AudioDeviceRef(
    val id: Int,
    val type: Int,
)

/** 마이크 라우팅 결과 (기능명세서 4.2절). */
sealed interface MicrophoneRouting {
    /** 시스템 기본 경로를 그대로 쓴다. 입력 장치 지정만으로 충분한 경우를 포함한다. */
    data object SystemDefault : MicrophoneRouting

    /** 통신 장치 경로를 실제로 활성화했다. 세션 종료 시 반드시 해제해야 한다. */
    data class Activated(
        val device: AudioDeviceRef,
    ) : MicrophoneRouting

    /** 선택한 장치가 연결되어 있지 않거나 경로 전환에 실패했다. 시스템 기본 마이크로 폴백한다. */
    data object Unavailable : MicrophoneRouting
}

/**
 * 통신(양방향) 오디오 경로 제어. 블루투스 헤드셋 마이크는 이 경로가 열려야 실제로 녹음된다.
 *
 * [android.media.AudioManager.setCommunicationDevice] 계열을 감싸며,
 * 테스트에서는 페이크로 대체한다.
 */
interface CommunicationDeviceController {
    /** 통신 경로로 지정 가능한 장치 목록. */
    fun available(): List<AudioDeviceRef>

    /** [device]를 통신 장치로 요청하고 실제 전환이 확인될 때까지 기다린다. */
    suspend fun activate(device: AudioDeviceRef): Boolean

    /** 통신 장치 지정을 해제해 시스템 기본 경로로 되돌린다. */
    fun clear()
}

/**
 * 선택한 마이크 입력 장치로 오디오 경로를 준비한다 (기능명세서 4.2절).
 *
 * 내장/유선 마이크는 `AudioRecord.setPreferredDevice`만으로 충분하지만,
 * 블루투스 헤드셋은 SCO/LE 링크가 열려야 입력 장치로 존재하므로 통신 장치 활성화가 필요하다.
 */
class MicrophoneRouter(
    private val controller: CommunicationDeviceController,
) {
    private var activatedDevice: AudioDeviceRef? = null

    /** [preferred] 경로를 준비하고 결과를 돌려준다. 호출자는 [MicrophoneRouting.Unavailable]을 사용자에게 알린다. */
    suspend fun activate(preferred: MicrophoneDevice): MicrophoneRouting {
        if (!preferred.needsCommunicationRoute()) return MicrophoneRouting.SystemDefault
        val wantedTypes = microphoneInputTypes(preferred) ?: return MicrophoneRouting.SystemDefault
        val device =
            controller.available().firstOrNull { it.type in wantedTypes }
                ?: return MicrophoneRouting.Unavailable
        if (!controller.activate(device)) return MicrophoneRouting.Unavailable
        activatedDevice = device
        return MicrophoneRouting.Activated(device)
    }

    /** 세션 종료 시 통신 경로를 원상 복구한다. 활성화한 적이 없으면 아무것도 하지 않는다. */
    fun release() {
        if (activatedDevice == null) return
        activatedDevice = null
        controller.clear()
    }
}

/** 블루투스만 통신 경로 전환이 필요하다. 나머지는 출력 경로를 건드리지 않는 편이 안전하다. */
private fun MicrophoneDevice.needsCommunicationRoute(): Boolean = this == MicrophoneDevice.BLUETOOTH

/** 선택지에 대응하는 [AudioDeviceInfo] 입력 타입. `null`이면 시스템 기본을 그대로 쓴다. */
fun microphoneInputTypes(preferred: MicrophoneDevice): List<Int>? =
    when (preferred) {
        MicrophoneDevice.AUTO -> null
        MicrophoneDevice.BUILT_IN -> listOf(AudioDeviceInfo.TYPE_BUILTIN_MIC)
        MicrophoneDevice.BLUETOOTH ->
            listOf(AudioDeviceInfo.TYPE_BLUETOOTH_SCO, AudioDeviceInfo.TYPE_BLE_HEADSET)

        MicrophoneDevice.WIRED ->
            listOf(
                AudioDeviceInfo.TYPE_WIRED_HEADSET,
                AudioDeviceInfo.TYPE_USB_DEVICE,
                AudioDeviceInfo.TYPE_USB_HEADSET,
            )
    }
