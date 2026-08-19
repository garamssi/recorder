package io.rami.screenrecorder.domain.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows

class AudioSettingsTest {
    @Test
    fun `볼륨은 0에서 200퍼센트까지 허용한다`() {
        assertDoesNotThrow { VolumePercent(0) }
        assertDoesNotThrow { VolumePercent(100) }
        assertDoesNotThrow { VolumePercent(200) }
    }

    @Test
    fun `볼륨 범위를 벗어나면 거부한다`() {
        assertThrows<IllegalArgumentException> { VolumePercent(-1) }
        assertThrows<IllegalArgumentException> { VolumePercent(201) }
    }

    @Test
    fun `볼륨을 선형 게인 계수로 변환한다`() {
        assertEquals(0.0f, VolumePercent(0).asGain())
        assertEquals(1.0f, VolumePercent(100).asGain())
        assertEquals(2.0f, VolumePercent(200).asGain())
    }

    @Test
    fun `오디오 소스는 명세의 4가지 선택지를 제공한다`() {
        val sources = AudioSource.entries
        assertEquals(4, sources.size)
        assertEquals(
            listOf(
                AudioSource.SILENT,
                AudioSource.INTERNAL,
                AudioSource.MICROPHONE,
                AudioSource.INTERNAL_AND_MICROPHONE,
            ),
            sources.toList(),
        )
    }
}
