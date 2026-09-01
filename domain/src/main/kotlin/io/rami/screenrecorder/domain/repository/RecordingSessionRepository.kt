package io.rami.screenrecorder.domain.repository

import io.rami.screenrecorder.domain.model.Recording
import io.rami.screenrecorder.domain.model.RecordingConfig
import io.rami.screenrecorder.domain.model.RecordingSessionEvent
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

    /**
     * 저장 완료 이벤트 스트림 (기능명세서 6.2절 "저장 직후 이름 변경" 진입점).
     *
     * 수동 중지뿐 아니라 타이머/저장 공간 부족/시스템 중단에 의한 자동 안전 중지도 포함한다.
     */
    val completedRecordings: Flow<Recording>

    /**
     * 홈이 아직 보여 주지 못한 저장 완료 녹화본. 없으면 null (기능명세서 2.1절 [결정]).
     *
     * [completedRecordings] 와 달리 지나가는 이벤트가 아니라 남아 있는 상태다. 완료 순간
     * 홈이 화면에 없어도 (버블로 녹화를 시작한 경우가 그렇다) 사라지지 않고, 홈이 한 번
     * 보여 준 뒤 [consumeCompletedRecording] 로 소모될 때까지 유지된다.
     */
    val pendingCompletedRecording: Flow<Recording?>

    /** 완료 표시를 소모한다. 홈이 실제로 보여 준 뒤에만 부른다 (기능명세서 2.1절 [결정]). */
    fun consumeCompletedRecording()

    /** 세션 진행 이벤트 스트림 (예고/자동 중지 사유, 기능명세서 11절 알림용). */
    val sessionEvents: Flow<RecordingSessionEvent>

    /** [config]로 새 녹화 세션을 시작한다 (카운트다운 포함). */
    suspend fun start(config: RecordingConfig)

    /** 진행 중인 카운트다운을 건너뛰고 즉시 녹화를 시작한다 (기능명세서 3절: 탭=스킵). */
    fun skipCountdown()

    /** 세션을 중지하고 파일을 안전하게 마무리한다. */
    suspend fun stop()

    /** 프레임/샘플 공급을 멈춘다 (기능명세서 11.2절). */
    suspend fun pause()

    /** 공급을 재개하고 키프레임을 강제한다 (기능명세서 11.3절). */
    suspend fun resume()
}
