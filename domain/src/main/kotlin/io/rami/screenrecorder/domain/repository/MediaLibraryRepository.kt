package io.rami.screenrecorder.domain.repository

import io.rami.screenrecorder.domain.model.Recording
import io.rami.screenrecorder.domain.model.RecordingId
import io.rami.screenrecorder.domain.model.TrashItem
import kotlinx.coroutines.flow.Flow

/**
 * 저장된 녹화본 관리 경계 (기능명세서 6, 7, 9절).
 *
 * data 계층이 MediaStore로 구현한다.
 */
interface MediaLibraryRepository {
    /** 저장된 녹화본 목록 스트림 (원본 순서, 정렬/검색은 UseCase 담당). */
    fun observeRecordings(): Flow<List<Recording>>

    /** DISPLAY_NAME을 [newName]으로 변경한다 (확장자 제외한 이름). */
    suspend fun rename(id: RecordingId, newName: String)

    /** 휴지통으로 이동한다 (기능명세서 9절, IS_TRASHED). */
    suspend fun moveToTrash(ids: List<RecordingId>)

    /** 휴지통 항목 스트림 (남은 보관일 포함). */
    fun observeTrash(): Flow<List<TrashItem>>

    /** 휴지통에서 복원한다. */
    suspend fun restore(ids: List<RecordingId>)

    /** 영구 삭제한다. */
    suspend fun permanentlyDelete(ids: List<RecordingId>)
}
