package io.rami.screenrecorder.domain.repository

import io.rami.screenrecorder.domain.model.CapturedImage
import io.rami.screenrecorder.domain.model.MicrophoneDevice
import io.rami.screenrecorder.domain.model.VoiceMemo
import io.rami.screenrecorder.domain.model.VoiceRecordingState
import kotlinx.coroutines.flow.Flow

/**
 * 화면 캡처 경계 (기능명세서 12절).
 *
 * data 계층이 MediaProjection + ImageReader로 한 프레임을 받아 MediaStore Images에 저장한다.
 */
interface ScreenshotRepository {
    /**
     * 현재 화면을 한 장 캡처해 저장한다.
     *
     * 동의 토큰이 없거나 프레임을 받지 못하면 실패를 반환한다 (외부 요인이므로 예외 대신 Result).
     */
    suspend fun capture(): Result<CapturedImage>
}

/**
 * 음성 전용 녹음 경계 (기능명세서 13절).
 *
 * data 계층이 AudioRecord + AAC 인코딩으로 구현한다. 상태 전이 유효성 검사는 UseCase가 담당한다.
 */
interface VoiceRecordingRepository {
    /** 현재 녹음 상태 스트림. */
    fun observeState(): Flow<VoiceRecordingState>

    /**
     * 선택한 마이크 입력 장치를 쓸 수 없어 시스템 기본으로 폴백한 시점 알림 (기능명세서 4.2절 [결정]).
     *
     * 값은 사용자가 요청했던 장치다. 화면 녹화의 [RecordingSessionEvent.MicrophoneFellBack]과 같은 역할이다.
     */
    fun observeMicrophoneFallbacks(): Flow<MicrophoneDevice>

    /** 마이크 녹음을 시작한다. */
    suspend fun start()

    /** 녹음을 중지하고 저장한다. 저장할 내용이 없으면 null. */
    suspend fun stop(): VoiceMemo?
}
