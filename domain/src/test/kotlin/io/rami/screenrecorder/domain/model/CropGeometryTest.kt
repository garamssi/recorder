package io.rami.screenrecorder.domain.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class CropGeometryTest {
    @Test
    fun `크롭 없이 비율이 같으면 전체 텍스처를 전체 뷰포트에 그린다`() {
        val geometry =
            CropGeometry.compute(
                sourceSize = Resolution(1920, 1080),
                cropRegion = null,
                outputSize = Resolution(1920, 1080),
            )

        assertEquals(NormalizedRect(0f, 0f, 1f, 1f), geometry.sourceRect)
        assertEquals(Viewport(0, 0, 1920, 1080), geometry.destViewport)
    }

    @Test
    fun `크롭 영역은 정규화 텍스처 좌표로 변환된다`() {
        val geometry =
            CropGeometry.compute(
                sourceSize = Resolution(1920, 1080),
                cropRegion = CaptureRegion(x = 192, y = 108, width = 960, height = 540),
                outputSize = Resolution(960, 540),
            )

        assertEquals(NormalizedRect(0.1f, 0.1f, 0.6f, 0.6f), geometry.sourceRect)
        assertEquals(Viewport(0, 0, 960, 540), geometry.destViewport)
    }

    @Test
    fun `4대3 크롭을 16대9 출력에 넣으면 좌우 레터박스가 생긴다`() {
        val geometry =
            CropGeometry.compute(
                sourceSize = Resolution(1920, 1080),
                cropRegion = CaptureRegion(x = 0, y = 0, width = 640, height = 480),
                outputSize = Resolution(1920, 1080),
            )

        // 높이를 1080에 맞추면 너비는 1440 → 좌우 240씩 여백 (기능명세서 5절 레터박스)
        assertEquals(Viewport(240, 0, 1440, 1080), geometry.destViewport)
    }

    @Test
    fun `세로 회전 화면을 가로 출력에 넣으면 좌우 레터박스가 생긴다`() {
        val geometry =
            CropGeometry.compute(
                sourceSize = Resolution(1080, 1920),
                cropRegion = null,
                outputSize = Resolution(1920, 1080),
            )

        // 세로(9:16)를 가로(16:9)에: 높이 1080 기준 너비 = 1080*1080/1920 = 607 → 짝수 보정 606
        assertEquals(Viewport(657, 0, 606, 1080), geometry.destViewport)
    }

    @Test
    fun `가로 화면을 세로 출력에 넣으면 상하 레터박스가 생긴다`() {
        val geometry =
            CropGeometry.compute(
                sourceSize = Resolution(1920, 1080),
                cropRegion = null,
                outputSize = Resolution(1080, 1920),
            )

        // 가로(16:9)를 세로(9:16)에: 너비 1080 기준 높이 = 1080*1080/1920 = 607 → 짝수 보정 606
        assertEquals(Viewport(0, 657, 1080, 606), geometry.destViewport)
    }
}
