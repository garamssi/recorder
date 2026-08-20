package io.rami.screenrecorder.domain.model

import kotlin.time.Duration

/** 저장된 화면 캡처 이미지 한 장 (기능명세서 12절). */
data class CapturedImage(
    val displayName: String,
    val contentUri: String,
    val sizeBytes: Long,
    val widthPx: Int,
    val heightPx: Int,
    val createdAtEpochMillis: Long,
)

/** 저장된 음성 전용 녹음 한 건 (기능명세서 13절). */
data class VoiceMemo(
    val displayName: String,
    val contentUri: String,
    val sizeBytes: Long,
    val duration: Duration,
    val createdAtEpochMillis: Long,
)

/**
 * 음성 전용 녹음 상태 (기능명세서 13절).
 *
 * 화면 녹화와 독립적인 세션이므로 [RecordingState]와 분리한다 — 두 세션은 동시에 돌 수 없다는
 * 제약을 서비스 계층에서 강제하고, 상태 기계 자체는 섞지 않는다.
 */
sealed interface VoiceRecordingState {
    /** 녹음하고 있지 않음. */
    data object Idle : VoiceRecordingState

    /** 녹음 중 — [elapsed]는 시작 이후 경과 시간. */
    data class Recording(
        val elapsed: Duration,
    ) : VoiceRecordingState

    /** 중지 요청 후 파일 마무리 중. */
    data object Stopping : VoiceRecordingState
}
