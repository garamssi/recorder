package io.rami.screenrecorder.data.storage

import io.rami.screenrecorder.domain.model.VideoCodec

// 트랙에서 읽어 낸 값의 해석 (기능명세서 6.1절 [결정]).
// 플랫폼에서 값을 꺼내는 일은 MediaStorePublishTarget.kt 가 한다.

/**
 * 트랙 MIME 으로 코덱을 가른다.
 *
 * 하드코딩돼 있던 자리다 — HEVC 로 녹화해도 라이브러리 상세가 H264 라고 보여 줬다.
 * 알 수 없는 MIME 은 이 앱의 기본 코덱으로 둔다. 틀린 값을 단정하는 것보다 낫다.
 */
internal fun videoCodecOf(mimeType: String?): VideoCodec =
    when (mimeType) {
        MIME_HEVC -> VideoCodec.HEVC
        else -> VideoCodec.H264
    }

/**
 * 초당 프레임 수.
 *
 * @param reportedFrameRate 트랙 포맷이 알려 준 값. fMP4 에는 없을 수 있다.
 * @param frameCount 되짚기용 프레임 수. 알 수 없으면 0.
 * @param durationMs 되짚기용 재생 시간. 알 수 없으면 0.
 */
internal fun frameRateOf(
    reportedFrameRate: Int?,
    frameCount: Int,
    durationMs: Long,
): Int =
    when {
        reportedFrameRate != null && reportedFrameRate > 0 -> reportedFrameRate
        frameCount > 0 && durationMs > 0 -> (frameCount * MILLIS_PER_SECOND / durationMs).toInt()
        else -> 0
    }

private const val MIME_HEVC = "video/hevc"
private const val MILLIS_PER_SECOND = 1_000L
