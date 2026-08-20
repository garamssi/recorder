package io.rami.screenrecorder.domain.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class CompressionPresetTest {
    private val source =
        CompressionSource(
            resolution = Resolution(1920, 1080),
            bitrateBps = 15_000_000,
            codec = VideoCodec.H264,
        )

    @Test
    fun `고효율 변환은 동일 해상도 HEVC 재인코딩이다`() {
        val plan = CompressionPreset.HIGH_EFFICIENCY.plan(source)

        assertEquals(VideoCodec.HEVC, plan.targetCodec)
        assertEquals(Resolution(1920, 1080), plan.targetResolution)
        // HEVC 동급 화질 기준 60% 비트레이트 (명세 8절: 화질 유사, 용량 30~50% 감소)
        assertEquals(9_000_000, plan.targetBitrateBps)
    }

    @Test
    fun `표준 압축은 동일 해상도 비트레이트 50 퍼센트다`() {
        val plan = CompressionPreset.STANDARD.plan(source)

        assertEquals(VideoCodec.H264, plan.targetCodec)
        assertEquals(Resolution(1920, 1080), plan.targetResolution)
        assertEquals(7_500_000, plan.targetBitrateBps)
    }

    @Test
    fun `최대 압축은 720p 다운스케일과 비트레이트 축소다`() {
        val plan = CompressionPreset.MAXIMUM.plan(source)

        assertEquals(VideoCodec.H264, plan.targetCodec)
        assertEquals(Resolution(1280, 720), plan.targetResolution)
        // 명세 8절: 용량 약 70% 이상 감소 → 30% 비트레이트
        assertEquals(4_500_000, plan.targetBitrateBps)
    }

    @Test
    fun `이미 720p 이하면 최대 압축도 다운스케일하지 않는다`() {
        val hd = source.copy(resolution = Resolution(1280, 720), bitrateBps = 8_000_000)

        val plan = CompressionPreset.MAXIMUM.plan(hd)

        assertEquals(Resolution(1280, 720), plan.targetResolution)
        assertEquals(2_400_000, plan.targetBitrateBps)
    }

    @Test
    fun `세로 영상은 세로 720p로 다운스케일한다`() {
        val portrait = source.copy(resolution = Resolution(1080, 1920))

        val plan = CompressionPreset.MAXIMUM.plan(portrait)

        assertEquals(Resolution(720, 1280), plan.targetResolution)
    }

    @Test
    fun `압축 파일 이름은 원본이름_compressed 형식이다`() {
        assertEquals(
            "회의_20260820_compressed.mp4",
            CompressionPreset.compressedFileName("회의_20260820.mp4"),
        )
        assertEquals(
            "이름_compressed.mp4",
            CompressionPreset.compressedFileName("이름"),
        )
    }
}
