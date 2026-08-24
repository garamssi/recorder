package io.rami.screenrecorder.domain.model

import kotlin.math.roundToInt

/**
 * 시스템 미디어 볼륨 상태 (기능명세서 10절 플레이어 볼륨 컨트롤).
 *
 * 플레이어 자체 볼륨을 따로 두지 않고 시스템 미디어 스트림에 연동하므로,
 * 하드웨어 볼륨 키와 슬라이더가 항상 같은 값을 가리킨다.
 */
data class MediaVolume private constructor(
    /** 현재 단계. 항상 0..[max] 범위다. */
    val level: Int,
    /** 이 스트림의 최대 단계. */
    val max: Int,
    /** 시스템 음소거 여부. 단계를 유지한 채 소리만 끈 상태일 수 있다. */
    val isMuted: Boolean,
) {
    /** 슬라이더가 쓰는 0f..1f 비율. 최대값이 0인 기기에서도 안전하다. */
    val fraction: Float get() = if (max <= 0) 0f else level.toFloat() / max

    /** 실제로 소리가 나지 않는 상태 (음소거이거나 단계가 0). */
    val isSilent: Boolean get() = isMuted || level == 0

    /** 슬라이더 비율을 단계로 바꾼다. 범위를 벗어난 값은 잘라낸다. */
    fun levelFor(fraction: Float): Int = (fraction.coerceIn(0f, 1f) * max).roundToInt()

    companion object {
        /**
         * 시스템이 보고한 값으로 만든다.
         *
         * 시스템 값과 최대값이 순간적으로 어긋날 수 있어 단계를 0..max 로 보정한다.
         */
        operator fun invoke(
            level: Int,
            max: Int,
            isMuted: Boolean,
        ): MediaVolume {
            require(max >= 0) { "최대 단계는 음수일 수 없다: $max" }
            return MediaVolume(level.coerceIn(0, max), max, isMuted)
        }
    }
}
