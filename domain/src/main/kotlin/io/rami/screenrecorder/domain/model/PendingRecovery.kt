package io.rami.screenrecorder.domain.model

/**
 * 이전 실행이 크래시·강제 종료되어 발행되지 못한 임시 녹화 파일 (기능명세서 6.1절).
 *
 * fMP4로 기록되므로 마지막 fragment까지 재생 가능하며, 사용자에게 복구/삭제를 제안한다.
 */
data class PendingRecovery(
    /** 임시 파일 식별자 (파일명). */
    val id: String,
    /** 사용자에게 보여줄 이름 (원래 저장될 파일명). */
    val displayName: String,
    /** 임시 파일 크기 (바이트). */
    val sizeBytes: Long,
)
