package io.rami.screenrecorder.domain.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds

class RecordingConfigTest {
    @Test
    fun `기본 설정은 기능명세서 4절의 기본값과 일치한다`() {
        val config = RecordingConfig.DEFAULT

        assertEquals(ResolutionOption.Fixed(Resolution.FHD), config.resolution)
        assertEquals(FrameRate.FPS_60, config.frameRate)
        assertEquals(BitrateOption.Auto, config.bitrate)
        assertEquals(VideoCodec.H264, config.codec)
        assertEquals(AudioSource.INTERNAL, config.audioSource)
        assertEquals(MicrophoneDevice.AUTO, config.microphoneDevice)
        assertEquals(VolumePercent(100), config.microphoneVolume)
        assertEquals(VolumePercent(100), config.internalVolume)
        assertEquals(CountdownDuration.THREE_SECONDS, config.countdown)
        assertEquals(OrientationPolicy.FOLLOW_ROTATION, config.orientationPolicy)
        assertEquals(TimeLimit.None, config.timeLimit)
        assertEquals(CaptureMode.FullScreen, config.captureMode)
    }

    @Test
    fun `카운트다운 선택지는 없음_3_5_10초다`() {
        assertEquals(listOf(0, 3, 5, 10), CountdownDuration.entries.map { it.seconds })
    }

    @Test
    fun `시간 제한은 10초에서 12시간까지 허용한다`() {
        assertDoesNotThrow { TimeLimit.Limited(10.seconds) }
        assertDoesNotThrow { TimeLimit.Limited(12.hours) }
    }

    @Test
    fun `시간 제한 유효 범위를 벗어나면 거부한다`() {
        assertThrows<IllegalArgumentException> { TimeLimit.Limited(9.seconds) }
        assertThrows<IllegalArgumentException> { TimeLimit.Limited(12.hours + 1.seconds) }
    }

    @Test
    fun `기기 최대를 고르면 화면 해상도를 그대로 쓴다`() {
        val deviceMax = Resolution(2560, 1600)

        assertEquals(deviceMax, ResolutionOption.DeviceMax.resolve(deviceMax))
    }

    // --- 프리셋 비율 (기능명세서 4.1절 [결정]) ---

    /**
     * "1080p" 는 짧은 변이 1080 이라는 뜻이다. 16:9 로 못 박으면 16:10 태블릿에서 미러링이
     * 좌우에 검은 띠를 넣어 화면의 10%를 버린다.
     */
    @Test
    fun `고정 프리셋은 기기 화면 비율을 따른다`() {
        val tablet = Resolution(3200, 2000)

        assertEquals(Resolution(1728, 1080), ResolutionOption.Fixed(Resolution.FHD).resolve(tablet))
    }

    @Test
    fun `세로 화면에서도 짧은 변을 프리셋에 맞춘다`() {
        val portrait = Resolution(2000, 3200)

        assertEquals(Resolution(1080, 1728), ResolutionOption.Fixed(Resolution.FHD).resolve(portrait))
    }

    @Test
    fun `16 대 9 기기에서는 프리셋 값이 그대로 나온다`() {
        val phone = Resolution(2400, 1350)

        assertEquals(Resolution.FHD, ResolutionOption.Fixed(Resolution.FHD).resolve(phone))
    }

    @Test
    fun `기기 해상도보다 크게 올리지 않는다`() {
        // 없는 화소를 만들어 내는 확대는 용량만 늘린다.
        val small = Resolution(1280, 800)

        assertEquals(small, ResolutionOption.Fixed(Resolution.FHD).resolve(small))
    }

    @Test
    fun `비율을 맞추다 남는 화소는 짝수로 버린다`() {
        // 2001 은 홀수 화면 — H.264 색차 정렬을 위해 결과는 항상 짝수여야 한다.
        val odd = Resolution(3201, 2001)

        val resolved = ResolutionOption.Fixed(Resolution.HD).resolve(odd)

        assertEquals(0, resolved.width % 2)
        assertEquals(0, resolved.height % 2)
        assertEquals(720, resolved.height)
    }

    // --- 비트레이트 추정 (기능명세서 2.1절 "약 N시간" 표시) ---

    @Test
    fun `고정 비트레이트는 Mbps 를 그대로 bps 로 옮긴다`() {
        val config = RecordingConfig.DEFAULT.copy(bitrate = BitrateOption.Fixed(megabitsPerSecond = 12))

        assertEquals(12_000_000, config.estimateBitrateBps())
    }

    @Test
    fun `자동 비트레이트는 해상도와 프레임레이트에서 계산한다`() {
        val config =
            RecordingConfig.DEFAULT.copy(
                bitrate = BitrateOption.Auto,
                resolution = ResolutionOption.Fixed(Resolution.HD),
                frameRate = FrameRate.FPS_60,
            )

        assertEquals(AutoBitratePolicy.bitrateBpsFor(Resolution.HD, FrameRate.FPS_60), config.estimateBitrateBps())
    }

    @Test
    fun `기기 최대 해상도는 넘겨받은 근사값으로 푼다`() {
        val config =
            RecordingConfig.DEFAULT.copy(
                bitrate = BitrateOption.Auto,
                resolution = ResolutionOption.DeviceMax,
                frameRate = FrameRate.FPS_30,
            )

        val approximate = Resolution(2560, 1600)

        assertEquals(
            AutoBitratePolicy.bitrateBpsFor(approximate, FrameRate.FPS_30),
            config.estimateBitrateBps(approximateDeviceMax = approximate),
        )
    }
}
