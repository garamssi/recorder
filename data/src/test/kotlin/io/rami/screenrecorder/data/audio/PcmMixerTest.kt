package io.rami.screenrecorder.data.audio

import io.rami.screenrecorder.domain.model.VolumePercent
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Test

class PcmMixerTest {
    private val mixer = PcmMixer()

    @Test
    fun `두 PCM 스트림을 샘플 단위로 더한다`() {
        val internal = shortArrayOf(1000, -2000, 3000)
        val microphone = shortArrayOf(500, 500, -500)

        val mixed = mixer.mix(
            first = internal,
            firstGain = VolumePercent(100).asGain(),
            second = microphone,
            secondGain = VolumePercent(100).asGain(),
        )

        assertArrayEquals(shortArrayOf(1500, -1500, 2500), mixed)
    }

    @Test
    fun `게인이 곱해진 뒤 믹싱된다`() {
        val internal = shortArrayOf(1000)
        val microphone = shortArrayOf(1000)

        val mixed = mixer.mix(
            first = internal,
            firstGain = VolumePercent(50).asGain(),
            second = microphone,
            secondGain = VolumePercent(200).asGain(),
        )

        assertArrayEquals(shortArrayOf(2500), mixed)
    }

    @Test
    fun `합이 16비트 범위를 넘으면 클리핑한다`() {
        val loudA = shortArrayOf(30000, -30000)
        val loudB = shortArrayOf(10000, -10000)

        val mixed = mixer.mix(
            first = loudA,
            firstGain = 1.0f,
            second = loudB,
            secondGain = 1.0f,
        )

        assertArrayEquals(shortArrayOf(Short.MAX_VALUE, Short.MIN_VALUE), mixed)
    }

    @Test
    fun `단일 스트림에 게인만 적용할 수 있다`() {
        val samples = shortArrayOf(1000, -1000, 20000)

        val amplified = mixer.applyGain(samples, VolumePercent(200).asGain())

        assertArrayEquals(shortArrayOf(2000, -2000, Short.MAX_VALUE), amplified)
    }

    @Test
    fun `게인 100퍼센트는 원본을 변경하지 않는다`() {
        val samples = shortArrayOf(123, -456)

        assertArrayEquals(samples, mixer.applyGain(samples, VolumePercent(100).asGain()))
    }

    @Test
    fun `길이가 다른 입력은 짧은 쪽을 무음으로 채워 믹싱한다`() {
        val longer = shortArrayOf(100, 200, 300)
        val shorter = shortArrayOf(50)

        val mixed = mixer.mix(first = longer, firstGain = 1.0f, second = shorter, secondGain = 1.0f)

        assertArrayEquals(shortArrayOf(150, 200, 300), mixed)
    }
}
