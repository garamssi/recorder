package io.rami.screenrecorder.domain.usecase

import io.rami.screenrecorder.domain.model.NameValidation
import io.rami.screenrecorder.domain.model.RecordingState

/** 녹화 세션 제어가 거부된 사유. */
sealed class RecordingSessionException(
    message: String,
) : Exception(message) {
    /** 저장 공간이 시작 기준(500MB) 미만이다 (기능명세서 2.2절). */
    class InsufficientStorage(
        val availableBytes: Long,
    ) : RecordingSessionException("저장 공간 부족: ${availableBytes}B")

    /** 현재 상태에서 허용되지 않는 전이다. */
    class InvalidState(
        val current: RecordingState,
        expectedDescription: String,
    ) : RecordingSessionException("현재 상태($current)에서는 $expectedDescription 상태여야 한다")
}

/** 이름 변경 유효성 위반 (기능명세서 6.3절). */
class InvalidRecordingNameException(
    val reason: NameValidation,
) : Exception("유효하지 않은 이름: $reason")
