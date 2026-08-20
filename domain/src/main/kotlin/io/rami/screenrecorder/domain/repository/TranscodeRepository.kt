package io.rami.screenrecorder.domain.repository

import io.rami.screenrecorder.domain.model.CompressionPreset
import io.rami.screenrecorder.domain.model.RecordingId
import io.rami.screenrecorder.domain.model.TranscodeJob
import kotlinx.coroutines.flow.Flow

/**
 * 압축(트랜스코딩) 작업 경계 (기능명세서 8절).
 *
 * data 계층이 Media3 Transformer + WorkManager로 구현한다.
 * 인코더 자원 경합을 피하기 위해 동시에 하나의 작업만 실행한다.
 */
interface TranscodeRepository {
    /** 현재 작업 스트림. 작업이 없으면 null. */
    fun observeJob(): Flow<TranscodeJob?>

    /** [recordingId]를 [preset]으로 압축하는 백그라운드 작업을 등록한다. */
    suspend fun enqueue(
        recordingId: RecordingId,
        preset: CompressionPreset,
    )

    /** 진행 중인 작업을 취소한다. */
    suspend fun cancel()

    /**
     * 완료(성공/실패/취소)된 작업 기록을 정리한다.
     *
     * 완료 안내(원본 휴지통 이동 프롬프트)를 처리한 뒤 호출해, 다음에 화면을 열 때
     * 같은 완료 작업이 프롬프트를 다시 띄우지 않게 한다 (기능명세서 8절).
     */
    suspend fun clearCompleted()
}
