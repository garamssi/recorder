package io.rami.screenrecorder.data.audio

import android.media.AudioDeviceInfo
import io.rami.screenrecorder.domain.model.MicrophoneDevice
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * 마이크 입력 장치 라우팅 (기능명세서 4.2절).
 *
 * 블루투스 헤드셋 마이크는 SCO/LE 링크가 올라와야 실제로 녹음된다.
 * setPreferredDevice만으로는 링크가 열리지 않으므로 통신 장치 활성화가 필요하다.
 */
class MicrophoneRouterTest {
    @Test
    fun `자동은 라우팅을 건드리지 않는다`() =
        runTest {
            val controller = FakeCommunicationDeviceController(available = emptyList())

            val routing = MicrophoneRouter(controller).activate(MicrophoneDevice.AUTO)

            assertEquals(MicrophoneRouting.SystemDefault, routing)
            assertTrue(controller.activated.isEmpty()) { "자동 선택에서 통신 경로를 바꾸면 안 된다" }
        }

    @Test
    fun `내장 마이크는 통신 경로 전환 없이 입력 장치 지정만 쓴다`() =
        runTest {
            val controller = FakeCommunicationDeviceController(available = emptyList())

            val routing = MicrophoneRouter(controller).activate(MicrophoneDevice.BUILT_IN)

            assertEquals(MicrophoneRouting.SystemDefault, routing)
            assertTrue(controller.activated.isEmpty())
        }

    @Test
    fun `블루투스는 SCO 통신 장치를 활성화한다`() =
        runTest {
            val headset = AudioDeviceRef(id = 7, type = AudioDeviceInfo.TYPE_BLUETOOTH_SCO)
            val controller = FakeCommunicationDeviceController(available = listOf(headset))

            val routing = MicrophoneRouter(controller).activate(MicrophoneDevice.BLUETOOTH)

            assertEquals(MicrophoneRouting.Activated(headset), routing)
            assertEquals(listOf(headset), controller.activated)
        }

    @Test
    fun `블루투스 LE 헤드셋도 활성화 대상이다`() =
        runTest {
            val headset = AudioDeviceRef(id = 9, type = AudioDeviceInfo.TYPE_BLE_HEADSET)
            val controller = FakeCommunicationDeviceController(available = listOf(headset))

            val routing = MicrophoneRouter(controller).activate(MicrophoneDevice.BLUETOOTH)

            assertEquals(MicrophoneRouting.Activated(headset), routing)
        }

    @Test
    fun `블루투스가 연결되어 있지 않으면 폴백을 알린다`() =
        runTest {
            val controller =
                FakeCommunicationDeviceController(
                    available = listOf(AudioDeviceRef(id = 1, type = AudioDeviceInfo.TYPE_BUILTIN_MIC)),
                )

            val routing = MicrophoneRouter(controller).activate(MicrophoneDevice.BLUETOOTH)

            assertEquals(MicrophoneRouting.Unavailable, routing)
        }

    @Test
    fun `통신 장치 전환이 실패하면 폴백을 알린다`() =
        runTest {
            // SCO 링크가 제한 시간 안에 올라오지 않는 경우 — 조용히 내장 마이크로 녹음되면 안 된다.
            val headset = AudioDeviceRef(id = 7, type = AudioDeviceInfo.TYPE_BLUETOOTH_SCO)
            val controller =
                FakeCommunicationDeviceController(available = listOf(headset), activationSucceeds = false)

            val routing = MicrophoneRouter(controller).activate(MicrophoneDevice.BLUETOOTH)

            assertEquals(MicrophoneRouting.Unavailable, routing)
        }

    @Test
    fun `해제하면 통신 경로를 원상 복구한다`() =
        runTest {
            val headset = AudioDeviceRef(id = 7, type = AudioDeviceInfo.TYPE_BLUETOOTH_SCO)
            val controller = FakeCommunicationDeviceController(available = listOf(headset))
            val router = MicrophoneRouter(controller)
            router.activate(MicrophoneDevice.BLUETOOTH)

            router.release()

            assertEquals(1, controller.clearCount)
        }

    @Test
    fun `활성화하지 않았으면 해제도 하지 않는다`() =
        runTest {
            // 통신 경로를 잡은 적이 없는데 clear를 부르면 다른 앱(통화 등)의 경로를 끊을 수 있다.
            val controller = FakeCommunicationDeviceController(available = emptyList())
            val router = MicrophoneRouter(controller)
            router.activate(MicrophoneDevice.AUTO)

            router.release()

            assertEquals(0, controller.clearCount)
        }

    @Test
    fun `입력 장치 지정에 쓸 장치 타입을 선택지별로 매핑한다`() {
        assertEquals(null, microphoneInputTypes(MicrophoneDevice.AUTO))
        assertEquals(
            listOf(AudioDeviceInfo.TYPE_BUILTIN_MIC),
            microphoneInputTypes(MicrophoneDevice.BUILT_IN),
        )
        assertEquals(
            listOf(AudioDeviceInfo.TYPE_BLUETOOTH_SCO, AudioDeviceInfo.TYPE_BLE_HEADSET),
            microphoneInputTypes(MicrophoneDevice.BLUETOOTH),
        )
        assertEquals(
            listOf(
                AudioDeviceInfo.TYPE_WIRED_HEADSET,
                AudioDeviceInfo.TYPE_USB_DEVICE,
                AudioDeviceInfo.TYPE_USB_HEADSET,
            ),
            microphoneInputTypes(MicrophoneDevice.WIRED),
        )
    }
}

private class FakeCommunicationDeviceController(
    private val available: List<AudioDeviceRef>,
    private val activationSucceeds: Boolean = true,
) : CommunicationDeviceController {
    val activated = mutableListOf<AudioDeviceRef>()
    var clearCount = 0

    override fun available(): List<AudioDeviceRef> = available

    override suspend fun activate(device: AudioDeviceRef): Boolean {
        if (!activationSucceeds) return false
        activated += device
        return true
    }

    override fun clear() {
        clearCount++
    }
}
