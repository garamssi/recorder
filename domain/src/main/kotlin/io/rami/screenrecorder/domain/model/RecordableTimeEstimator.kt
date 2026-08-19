package io.rami.screenrecorder.domain.model

import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * 저장 공간 기반 녹화 가능 시간 추정 (기능명세서 2.1절 "23.5GB 남음, 약 3시간 녹화 가능").
 */
object RecordableTimeEstimator {
    /** 오디오 트랙 + 컨테이너 오버헤드 여유 계수. 부동소수점 오차를 피하려고 백분율 정수(110/100)를 쓴다. */
    private const val OVERHEAD_PERCENT = 110L

    private const val PERCENT_BASE = 100L

    private const val BITS_PER_BYTE = 8L

    /** 녹화 시작에 필요한 최소 여유 공간 (기능명세서 2.2절). */
    const val MIN_FREE_BYTES_TO_START = 500_000_000L

    /** 녹화 유지에 필요한 최소 여유 공간. 이하로 떨어지면 자동 안전 중지한다 (기능명세서 11.1절). */
    const val MIN_FREE_BYTES_TO_CONTINUE = 200_000_000L

    /** [availableBytes]와 [videoBitrateBps]로 녹화 가능 시간을 추정한다. 오디오/컨테이너 오버헤드를 반영한다. */
    fun estimate(
        availableBytes: Long,
        videoBitrateBps: Int,
    ): Duration {
        val effectiveBitsPerSecond = videoBitrateBps * OVERHEAD_PERCENT / PERCENT_BASE
        val bytesPerSecond = effectiveBitsPerSecond / BITS_PER_BYTE
        return (availableBytes / bytesPerSecond).seconds
    }

    /** 녹화 시작 가능 여부 (기능명세서 2.2절: 500MB 미만이면 시작 불가). */
    fun canStartRecording(availableBytes: Long): Boolean = availableBytes >= MIN_FREE_BYTES_TO_START
}
