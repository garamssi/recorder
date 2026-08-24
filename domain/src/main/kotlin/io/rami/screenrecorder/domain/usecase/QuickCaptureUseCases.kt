package io.rami.screenrecorder.domain.usecase

import io.rami.screenrecorder.domain.model.CapturedImage
import io.rami.screenrecorder.domain.model.MicrophoneDevice
import io.rami.screenrecorder.domain.model.VoiceMemo
import io.rami.screenrecorder.domain.model.VoiceRecordingState
import io.rami.screenrecorder.domain.repository.ScreenshotRepository
import io.rami.screenrecorder.domain.repository.VoiceRecordingRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/** 음성 전용 녹음의 상태 전이 위반 (기능명세서 13절). */
sealed class VoiceRecordingException(
    message: String,
) : IllegalStateException(message) {
    /** 이미 녹음 중인데 다시 시작을 요청했다. */
    class AlreadyRecording(
        state: VoiceRecordingState,
    ) : VoiceRecordingException("이미 음성 녹음 중이다: $state")
}

/** 현재 화면을 한 장 캡처해 저장한다 (기능명세서 12절). */
class CaptureScreenshotUseCase
    @Inject
    constructor(
        private val screenshotRepository: ScreenshotRepository,
    ) {
        /** 저장된 이미지를 반환한다. 동의 토큰 부재·프레임 미수신 등은 실패로 전달된다. */
        suspend operator fun invoke(): Result<CapturedImage> = screenshotRepository.capture()
    }

/** 음성 전용 녹음을 시작한다 (기능명세서 13절: 유휴 상태에서만). */
class StartVoiceRecordingUseCase
    @Inject
    constructor(
        private val voiceRecordingRepository: VoiceRecordingRepository,
    ) {
        /** 유휴 상태가 아니면 [VoiceRecordingException.AlreadyRecording]으로 거부한다. */
        suspend operator fun invoke(): Result<Unit> {
            val currentState = voiceRecordingRepository.observeState().first()
            if (currentState !is VoiceRecordingState.Idle) {
                return Result.failure(VoiceRecordingException.AlreadyRecording(currentState))
            }
            return runCatching { voiceRecordingRepository.start() }
        }
    }

/** 음성 전용 녹음을 중지하고 저장한다 (기능명세서 13절). */
class StopVoiceRecordingUseCase
    @Inject
    constructor(
        private val voiceRecordingRepository: VoiceRecordingRepository,
    ) {
        /** 녹음 중이 아니면 저장소를 건드리지 않고 null 성공을 반환한다 (중복 중지는 오류가 아니다). */
        suspend operator fun invoke(): Result<VoiceMemo?> {
            val currentState = voiceRecordingRepository.observeState().first()
            if (currentState is VoiceRecordingState.Idle) return Result.success(null)
            return runCatching { voiceRecordingRepository.stop() }
        }
    }

/** 음성 전용 녹음 상태 스트림 (기능명세서 13절). */
class ObserveVoiceRecordingStateUseCase
    @Inject
    constructor(
        private val voiceRecordingRepository: VoiceRecordingRepository,
    ) {
        /** 현재 상태 스트림을 반환한다. */
        operator fun invoke(): Flow<VoiceRecordingState> = voiceRecordingRepository.observeState()
    }

/** 음성 녹음 마이크 폴백 알림 스트림 (기능명세서 4.2절 [결정]). */
class ObserveVoiceMicrophoneFallbackUseCase
    @Inject
    constructor(
        private val voiceRecordingRepository: VoiceRecordingRepository,
    ) {
        /** 요청했던 마이크 장치를 쓸 수 없었던 시점 스트림을 반환한다. */
        operator fun invoke(): Flow<MicrophoneDevice> = voiceRecordingRepository.observeMicrophoneFallbacks()
    }
