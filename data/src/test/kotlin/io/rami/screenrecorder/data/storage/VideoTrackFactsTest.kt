package io.rami.screenrecorder.data.storage

import io.rami.screenrecorder.domain.model.VideoCodec
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * 트랙에서 읽어 낸 코덱·fps 해석 (기능명세서 6.1절 [결정]).
 *
 * 코덱이 H264 로 하드코딩돼 있어 HEVC 로 녹화해도 라이브러리가 H264 라고 보여 줬다.
 * 플랫폼 호출 뒤에 두면 또 조용히 틀려도 아무도 모른다.
 */
class VideoTrackFactsTest {
    @Test
    fun `avc mime 은 H264 로 읽는다`() {
        assertEquals(VideoCodec.H264, videoCodecOf("video/avc"))
    }

    @Test
    fun `hevc mime 은 HEVC 로 읽는다`() {
        assertEquals(VideoCodec.HEVC, videoCodecOf("video/hevc"))
    }

    /** 알 수 없는 트랙은 이 앱이 쓰는 기본 코덱으로 본다 — 틀린 값을 단정하는 것보다 낫다. */
    @Test
    fun `알 수 없는 mime 은 기본값으로 둔다`() {
        assertEquals(VideoCodec.H264, videoCodecOf("video/x-unknown"))
        assertEquals(VideoCodec.H264, videoCodecOf(null))
    }

    @Test
    fun `프레임레이트가 없으면 0으로 둔다`() {
        assertEquals(0, frameRateOf(reportedFrameRate = null, frameCount = 0, durationMs = 0))
    }

    @Test
    fun `트랙이 프레임레이트를 알려 주면 그대로 쓴다`() {
        assertEquals(60, frameRateOf(reportedFrameRate = 60, frameCount = 0, durationMs = 0))
    }

    /** fMP4 트랙 포맷에는 프레임레이트가 없을 수 있다. 그때는 프레임 수로 되짚는다. */
    @Test
    fun `프레임레이트가 없으면 프레임 수와 재생 시간으로 되짚는다`() {
        assertEquals(30, frameRateOf(reportedFrameRate = null, frameCount = 3_000, durationMs = 100_000))
    }

    @Test
    fun `재생 시간이 0이면 되짚지 않는다`() {
        assertEquals(0, frameRateOf(reportedFrameRate = null, frameCount = 3_000, durationMs = 0))
    }

    /** 버리면 60fps 녹화가 59fps 로 보인다 (기능명세서 6.1절 [결정]). */
    @Test
    fun `되짚기는 반올림한다`() {
        assertEquals(60, frameRateOf(reportedFrameRate = null, frameCount = 3_597, durationMs = 60_000))
    }
}
