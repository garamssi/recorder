package io.rami.screenrecorder.data.audio

/**
 * 16비트 PCM 믹서 (기능명세서 4.2절: 내부+마이크 믹싱, 볼륨 0~200%).
 *
 * 게인 적용 후 샘플 단위로 합산하고 16비트 범위로 클리핑한다.
 */
class PcmMixer {
    /** [first]와 [second]를 각각의 게인으로 증폭해 합산한다. 길이가 다르면 짧은 쪽은 무음 처리. */
    fun mix(
        first: ShortArray,
        firstGain: Float,
        second: ShortArray,
        secondGain: Float,
    ): ShortArray {
        val length = maxOf(first.size, second.size)
        return ShortArray(length) { index ->
            val firstSample = if (index < first.size) first[index] * firstGain else 0f
            val secondSample = if (index < second.size) second[index] * secondGain else 0f
            clipToPcm16(firstSample + secondSample)
        }
    }

    /** [samples]에 [gain]을 적용한다 (클리핑 포함). */
    fun applyGain(
        samples: ShortArray,
        gain: Float,
    ): ShortArray = ShortArray(samples.size) { index -> clipToPcm16(samples[index] * gain) }

    private fun clipToPcm16(value: Float): Short =
        value
            .coerceIn(Short.MIN_VALUE.toFloat(), Short.MAX_VALUE.toFloat())
            .toInt()
            .toShort()
}
