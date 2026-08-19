package io.rami.screenrecorder.domain.model

import kotlin.time.Duration

/**
 * 저장 공간 기반 녹화 가능 시간 추정 (기능명세서 2.1절 "23.5GB 남음, 약 3시간 녹화 가능").
 */
object RecordableTimeEstimator {
    /** [availableBytes]와 [videoBitrateBps]로 녹화 가능 시간을 추정한다. 오디오/컨테이너 오버헤드를 반영한다. */
    fun estimate(availableBytes: Long, videoBitrateBps: Int): Duration = TODO()

    /** 녹화 시작 가능 여부 (기능명세서 2.2절: 500MB 미만이면 시작 불가). */
    fun canStartRecording(availableBytes: Long): Boolean = TODO()
}
