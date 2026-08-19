package io.rami.screenrecorder.domain.model

/** 인코딩 해상도 (픽셀 단위, 가로 기준). */
data class Resolution(
    val width: Int,
    val height: Int,
) {
    init {
        require(width > 0 && height > 0) { "해상도는 양수여야 한다: ${width}x$height" }
    }

    /** 초당 픽셀 처리량 계산용 픽셀 수. */
    val pixelCount: Long get() = width.toLong() * height

    companion object {
        /** 1080p (FHD). */
        val FHD = Resolution(1920, 1080)

        /** 720p (HD). */
        val HD = Resolution(1280, 720)
    }
}

/** 설정 화면의 해상도 선택지 (기능명세서 4.1절: 기기 최대 / 1080p / 720p). */
sealed interface ResolutionOption {
    /** 기기 디스플레이의 최대 해상도를 사용한다. */
    data object DeviceMax : ResolutionOption

    /** 고정 해상도를 사용한다. */
    data class Fixed(
        val resolution: Resolution,
    ) : ResolutionOption

    /** 기기 최대 해상도 [deviceMax]를 반영해 실제 인코딩 해상도를 결정한다. */
    fun resolve(deviceMax: Resolution): Resolution =
        when (this) {
            is DeviceMax -> deviceMax
            is Fixed -> resolution
        }
}

/** 프레임레이트 선택지 (기능명세서 4.1절). */
enum class FrameRate(
    val fps: Int,
) {
    FPS_30(30),
    FPS_60(60),
}

/** 비디오 코덱 선택지 (기능명세서 4.1절). */
enum class VideoCodec {
    H264,
    HEVC,
}

/** 비트레이트 선택지 (기능명세서 4.1절: 자동 / 8 / 12 / 15 / 20 Mbps). */
sealed interface BitrateOption {
    /** 해상도x fps 기반 자동 결정 ([AutoBitratePolicy]). */
    data object Auto : BitrateOption

    /** 고정 비트레이트. */
    data class Fixed(
        val megabitsPerSecond: Int,
    ) : BitrateOption {
        init {
            require(megabitsPerSecond > 0) { "비트레이트는 양수여야 한다: $megabitsPerSecond" }
        }
    }
}

/**
 * 자동 비트레이트 정책: 1080p60 = 15Mbps를 기준점으로 픽셀 처리량에 비례 산출한다
 * (기능명세서 4.1절 "자동(해상도x fps 기반)", 기본 1080p60 기준 15Mbps).
 */
object AutoBitratePolicy {
    private val ANCHOR_RESOLUTION = Resolution.FHD
    private val ANCHOR_FRAME_RATE = FrameRate.FPS_60
    private const val ANCHOR_BITRATE_BPS = 15_000_000L
    private const val ROUNDING_UNIT_BPS = 500_000L
    private const val MIN_BITRATE_BPS = 4_000_000L
    private const val MAX_BITRATE_BPS = 20_000_000L

    /** [resolution]과 [frameRate] 조합의 자동 비트레이트(bps)를 계산한다. */
    fun bitrateBpsFor(
        resolution: Resolution,
        frameRate: FrameRate,
    ): Int {
        val anchorPixelRate = ANCHOR_RESOLUTION.pixelCount * ANCHOR_FRAME_RATE.fps
        val pixelRate = resolution.pixelCount * frameRate.fps
        val proportional = ANCHOR_BITRATE_BPS * pixelRate / anchorPixelRate
        val rounded = (proportional + ROUNDING_UNIT_BPS / 2) / ROUNDING_UNIT_BPS * ROUNDING_UNIT_BPS
        return rounded.coerceIn(MIN_BITRATE_BPS, MAX_BITRATE_BPS).toInt()
    }
}
