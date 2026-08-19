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
    fun `해상도가 설정에서 실제 인코딩 해상도로 풀린다`() {
        val deviceMax = Resolution(2560, 1600)
        assertEquals(deviceMax, ResolutionOption.DeviceMax.resolve(deviceMax))
        assertEquals(Resolution.FHD, ResolutionOption.Fixed(Resolution.FHD).resolve(deviceMax))
    }
}
