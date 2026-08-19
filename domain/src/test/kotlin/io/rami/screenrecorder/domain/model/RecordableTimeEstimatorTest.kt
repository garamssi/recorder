package io.rami.screenrecorder.domain.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

class RecordableTimeEstimatorTest {
    @Test
    fun `남은 공간과 비트레이트로 녹화 가능 시간을 추정한다`() {
        // 15Mbps 비디오 + 오디오/컨테이너 오버헤드 10% = 16.5Mbps = 2.0625MB/s
        // 7.425GB(= 7425MB) / 2.0625MB/s = 3600초 = 1시간
        val estimate =
            RecordableTimeEstimator.estimate(
                availableBytes = 7_425_000_000L,
                videoBitrateBps = 15_000_000,
            )
        assertEquals(1.hours, estimate)
    }

    @Test
    fun `저장 공간이 없으면 0을 반환한다`() {
        assertEquals(0.minutes, RecordableTimeEstimator.estimate(availableBytes = 0, videoBitrateBps = 15_000_000))
    }

    @Test
    fun `안전 여유분 500MB 미만이면 녹화 불가로 판단한다`() {
        assertEquals(false, RecordableTimeEstimator.canStartRecording(availableBytes = 499_999_999))
        assertEquals(true, RecordableTimeEstimator.canStartRecording(availableBytes = 500_000_000))
    }
}
