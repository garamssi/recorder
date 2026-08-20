package io.rami.screenrecorder.domain.repository

import io.rami.screenrecorder.domain.model.PendingRecovery
import io.rami.screenrecorder.domain.model.Recording

/**
 * 크래시 복구 경계 (기능명세서 6.1절).
 *
 * data 계층이 앱 전용 캐시의 고아 임시 파일을 관리한다.
 */
interface RecordingRecoveryRepository {
    /** 발행되지 못하고 남은 임시 파일 목록. 없으면 빈 리스트. */
    suspend fun pendingRecoveries(): List<PendingRecovery>

    /**
     * [id] 임시 파일을 MediaStore로 발행(복구)한다.
     *
     * 재생 가능한 내용이 없으면 임시 파일을 정리하고 null을 반환한다.
     */
    suspend fun recover(id: String): Recording?

    /** [id] 임시 파일을 삭제한다. */
    suspend fun discard(id: String)
}
