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

    fun plan(source: CompressionSource): CompressionPlan {
        TODO("RED: 구현 전")
    }

    companion object {
        /** 원본을 보존하고 새 파일을 만든다 (명세 8절: "원본이름_compressed.mp4"). */
        fun compressedFileName(originalName: String): String {
            TODO("RED: 구현 전")
        }
    }
}
