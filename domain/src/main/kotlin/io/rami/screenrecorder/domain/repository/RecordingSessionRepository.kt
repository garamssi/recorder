package io.rami.screenrecorder.domain.repository

import io.rami.screenrecorder.domain.model.RecordingConfig
import io.rami.screenrecorder.domain.model.RecordingState
import kotlinx.coroutines.flow.Flow

/**
 * 녹화 세션 제어 경계.
 *
 * data 계층이 MediaProjection/MediaCodec 파이프라인으로 구현한다.
 * 상태 전이 유효성 검사는 UseCase가 담당하고, 구현체는 실제 파이프라인 제어만 한다.
 */
interface RecordingSessionRepository {
    /** 현재 세션 상태 스트림. */
    val state: Flow<RecordingState>

    /** [config]로 새 녹화 세션을 시작한다 (카운트다운 포함). */
    suspend fun start(config: RecordingConfig)

    /** 세션을 중지하고 파일을 안전하게 마무리한다. */
    suspend fun stop()

    /** 프레임/샘플 공급을 멈춘다 (기능명세서 11.2절). */
    suspend fun pause()

    /** 공급을 재개하고 키프레임을 강제한다 (기능명세서 11.3절). */
    suspend fun resume()
}
