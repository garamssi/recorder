package io.rami.screenrecorder.domain.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class VideoSettingsTest {
    @Test
    fun `자동 비트레이트는 1080p 60fps 기준 15Mbps다`() {
        val bps = AutoBitratePolicy.bitrateBpsFor(Resolution.FHD, FrameRate.FPS_60)
        assertEquals(15_000_000, bps)
    }

    @Test
    fun `자동 비트레이트는 픽셀 처리량에 비례해 줄어든다`() {
        // 1080p30은 1080p60의 절반 픽셀 처리량 -> 7.5Mbps
        assertEquals(7_500_000, AutoBitratePolicy.bitrateBpsFor(Resolution.FHD, FrameRate.FPS_30))
        // 720p60은 1080p60의 4/9 픽셀 처리량 -> 6.666..Mbps -> 500kbps 단위 반올림 6.5Mbps
        assertEquals(6_500_000, AutoBitratePolicy.bitrateBpsFor(Resolution.HD, FrameRate.FPS_60))
    }

    @Test
    fun `자동 비트레이트는 하한 4Mbps 아래로 내려가지 않는다`() {
        val tiny = Resolution(width = 320, height = 240)
        assertEquals(4_000_000, AutoBitratePolicy.bitrateBpsFor(tiny, FrameRate.FPS_30))
    }

    @Test
    fun `고정 비트레이트는 양수만 허용한다`() {
        assertThrows<IllegalArgumentException> {
            BitrateOption.Fixed(megabitsPerSecond = 0)
        }
    }

    @Test
    fun `해상도는 양수 크기만 허용한다`() {
        assertThrows<IllegalArgumentException> {
            Resolution(width = 0, height = 1080)
        }
    }

    @Test
    fun `프리셋 해상도 값이 명세와 일치한다`() {
        assertEquals(Resolution(1920, 1080), Resolution.FHD)
        assertEquals(Resolution(1280, 720), Resolution.HD)
    }
}
