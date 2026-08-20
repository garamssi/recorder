package io.rami.screenrecorder.domain.model

/** 압축(트랜스코딩) 입력 메타데이터 (기능명세서 8절). */
data class CompressionSource(
    val resolution: Resolution,
    val bitrateBps: Int,
    val codec: VideoCodec,
)

/** 압축 프리셋이 결정한 트랜스코딩 목표. */
data class CompressionPlan(
    val targetCodec: VideoCodec,
    val targetResolution: Resolution,
    val targetBitrateBps: Int,
)

/** 압축 프리셋 3종 (기능명세서 8절 [결정]). */
enum class CompressionPreset {
    /** 동일 해상도, HEVC 재인코딩 — 화질 유사, 용량 30~50% 감소. */
    HIGH_EFFICIENCY,

    /** 동일 해상도, 비트레이트 50% — 용량 약 50% 감소. */
    STANDARD,

    /** 720p 다운스케일 + 비트레이트 축소 — 용량 약 70% 이상 감소. */
    MAXIMUM,

    ;

    fun plan(source: CompressionSource): CompressionPlan =
        when (this) {
            HIGH_EFFICIENCY ->
                CompressionPlan(
                    targetCodec = VideoCodec.HEVC,
                    targetResolution = source.resolution,
                    // HEVC 동급 화질 기준 비트레이트 60% (명세 8절: 화질 유사, 용량 30~50% 감소)
                    targetBitrateBps = scaleBitrate(source.bitrateBps, HEVC_BITRATE_PERCENT),
                )

            STANDARD ->
                CompressionPlan(
                    targetCodec = source.codec,
                    targetResolution = source.resolution,
                    targetBitrateBps = scaleBitrate(source.bitrateBps, STANDARD_BITRATE_PERCENT),
                )

            MAXIMUM ->
                CompressionPlan(
                    targetCodec = source.codec,
                    targetResolution = downscaleTo720p(source.resolution),
                    targetBitrateBps = scaleBitrate(source.bitrateBps, MAXIMUM_BITRATE_PERCENT),
                )
        }

    private fun scaleBitrate(
        bitrateBps: Int,
        percent: Int,
    ): Int = (bitrateBps.toLong() * percent / PERCENT_DENOMINATOR).toInt()

    /** 짧은 변을 720으로 맞추되, 이미 720p 이하면 그대로 둔다 (업스케일 금지). */
    private fun downscaleTo720p(resolution: Resolution): Resolution {
        val shortSide = minOf(resolution.width, resolution.height)
        if (shortSide <= TARGET_SHORT_SIDE) return resolution
        return if (resolution.width >= resolution.height) {
            Resolution(
                width = evenDown(resolution.width.toLong() * TARGET_SHORT_SIDE / resolution.height),
                height = TARGET_SHORT_SIDE,
            )
        } else {
            Resolution(
                width = TARGET_SHORT_SIDE,
                height = evenDown(resolution.height.toLong() * TARGET_SHORT_SIDE / resolution.width),
            )
        }
    }

    companion object {
        /** 원본을 보존하고 새 파일을 만든다 (명세 8절: "원본이름_compressed.mp4"). */
        fun compressedFileName(originalName: String): String =
            "${originalName.removeSuffix(MP4_EXTENSION)}$COMPRESSED_SUFFIX$MP4_EXTENSION"

        private fun evenDown(value: Long): Int = (value - (value % 2)).toInt()

        private const val HEVC_BITRATE_PERCENT = 60
        private const val STANDARD_BITRATE_PERCENT = 50
        private const val MAXIMUM_BITRATE_PERCENT = 30
        private const val PERCENT_DENOMINATOR = 100
        private const val TARGET_SHORT_SIDE = 720
        private const val COMPRESSED_SUFFIX = "_compressed"
        private const val MP4_EXTENSION = ".mp4"
    }
}
