package io.rami.screenrecorder.domain.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** 플레이어 볼륨 컨트롤이 쓰는 시스템 미디어 볼륨 모델 (기능명세서 10절). */
class MediaVolumeTest {
    @Test
    fun `비율은 최대값 대비 현재 단계다`() {
        assertEquals(0.5f, MediaVolume(level = 7, max = 14, isMuted = false).fraction)
        assertEquals(1f, MediaVolume(level = 14, max = 14, isMuted = false).fraction)
        assertEquals(0f, MediaVolume(level = 0, max = 14, isMuted = false).fraction)
    }

    @Test
    fun `최대값이 0이어도 비율 계산이 깨지지 않는다`() {
        // 일부 기기·프로파일에서 스트림 최대값을 0으로 보고하는 경우가 있다.
        assertEquals(0f, MediaVolume(level = 0, max = 0, isMuted = false).fraction)
    }

    @Test
    fun `음소거이거나 단계가 0이면 소리가 나지 않는 상태다`() {
        assertTrue(MediaVolume(level = 0, max = 14, isMuted = false).isSilent)
        assertTrue(MediaVolume(level = 7, max = 14, isMuted = true).isSilent)
        assertFalse(MediaVolume(level = 7, max = 14, isMuted = false).isSilent)
    }

    @Test
    fun `비율을 단계로 바꾼다`() {
        val volume = MediaVolume(level = 0, max = 14, isMuted = false)

        assertEquals(0, volume.levelFor(0f))
        assertEquals(7, volume.levelFor(0.5f))
        assertEquals(14, volume.levelFor(1f))
    }

    @Test
    fun `범위를 벗어난 비율은 잘라낸다`() {
        val volume = MediaVolume(level = 0, max = 14, isMuted = false)

        assertEquals(0, volume.levelFor(-0.3f))
        assertEquals(14, volume.levelFor(1.7f))
    }

    @Test
    fun `생성 시 단계는 0과 최대값 사이로 보정된다`() {
        // 시스템이 보고하는 값과 최대값이 순간적으로 어긋날 수 있다.
        assertEquals(14, MediaVolume(level = 20, max = 14, isMuted = false).level)
        assertEquals(0, MediaVolume(level = -1, max = 14, isMuted = false).level)
    }
}
