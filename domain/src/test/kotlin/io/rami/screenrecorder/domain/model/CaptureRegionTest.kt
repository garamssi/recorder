package io.rami.screenrecorder.domain.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows

class CaptureRegionTest {
    @Test
    fun `최소 크기 320x240 영역은 유효하다`() {
        assertDoesNotThrow {
            CaptureRegion(x = 0, y = 0, width = 320, height = 240)
        }
    }

    @Test
    fun `너비가 최소 크기 미만이면 거부한다`() {
        assertThrows<IllegalArgumentException> {
            CaptureRegion(x = 0, y = 0, width = 319, height = 240)
        }
    }

    @Test
    fun `높이가 최소 크기 미만이면 거부한다`() {
        assertThrows<IllegalArgumentException> {
            CaptureRegion(x = 0, y = 0, width = 320, height = 239)
        }
    }

    @Test
    fun `음수 좌표는 거부한다`() {
        assertThrows<IllegalArgumentException> {
            CaptureRegion(x = -1, y = 0, width = 320, height = 240)
        }
        assertThrows<IllegalArgumentException> {
            CaptureRegion(x = 0, y = -1, width = 320, height = 240)
        }
    }

    @Test
    fun `영역의 우하단 좌표를 계산한다`() {
        val region = CaptureRegion(x = 100, y = 50, width = 640, height = 480)
        assertEquals(740, region.right)
        assertEquals(530, region.bottom)
    }
}
