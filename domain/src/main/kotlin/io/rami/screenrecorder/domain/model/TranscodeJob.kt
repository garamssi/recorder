package io.rami.screenrecorder.domain.model

/** 트랜스코딩 작업 상태 (기능명세서 8절: 진행률 알림, 취소 가능). */
enum class TranscodeStatus {
    RUNNING,
    SUCCEEDED,
    FAILED,
    CANCELLED,
}

/** 진행 중이거나 끝난 트랜스코딩 작업. */
data class TranscodeJob(
    val recordingId: RecordingId,
    val preset: CompressionPreset,
    val progressPercent: Int,
    val status: TranscodeStatus,
)
