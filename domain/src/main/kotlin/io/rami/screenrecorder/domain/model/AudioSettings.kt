package io.rami.screenrecorder.domain.model

/** 오디오 소스 선택지 (기능명세서 4.2절: 무음 / 기기 내부 / 마이크 / 내부+마이크). */
enum class AudioSource {
    SILENT,
    INTERNAL,
    MICROPHONE,
    INTERNAL_AND_MICROPHONE,
}

/** 마이크 입력 장치 선택지 (기능명세서 4.2절). */
enum class MicrophoneDevice {
    AUTO,
    BUILT_IN,
    BLUETOOTH,
    WIRED,
}

/**
 * 볼륨 설정값 (기능명세서 4.2절: 0~200% 슬라이더).
 */
@JvmInline
value class VolumePercent(
    val value: Int,
) {
    init {
        require(value in MIN..MAX) { "볼륨은 $MIN~$MAX%% 범위여야 한다: $value" }
    }

    /** PCM 믹싱에 적용할 선형 게인 계수 (100% = 1.0). */
    fun asGain(): Float = value / FULL_VOLUME.toFloat()

    companion object {
        /** 최소 볼륨 (%). */
        const val MIN = 0

        /** 최대 볼륨 (%). */
        const val MAX = 200

        private const val FULL_VOLUME = 100
    }
}
