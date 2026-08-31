package io.rami.screenrecorder.domain.usecase

import io.rami.screenrecorder.domain.model.RecordableTimeEstimator
import io.rami.screenrecorder.domain.model.RecordingConfig
import io.rami.screenrecorder.domain.model.RecordingState
import io.rami.screenrecorder.domain.repository.CaptureConsentRepository
import io.rami.screenrecorder.domain.repository.RecordingSessionRepository
import io.rami.screenrecorder.domain.repository.StorageRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/** 녹화를 시작한다 (기능명세서 2.2절: 저장 공간 500MB 미만이면 거부). */
class StartRecordingUseCase
    @Inject
    constructor(
        private val sessionRepository: RecordingSessionRepository,
        private val storageRepository: StorageRepository,
    ) {
        /** [config]로 녹화를 시작한다. */
        suspend operator fun invoke(config: RecordingConfig): Result<Unit> {
            val currentState = sessionRepository.state.first()
            val availableBytes = storageRepository.observeAvailableBytes().first()
            return when {
                currentState !is RecordingState.Idle ->
                    Result.failure(RecordingSessionException.InvalidState(currentState, "유휴"))

                !RecordableTimeEstimator.canStartRecording(availableBytes) ->
                    Result.failure(RecordingSessionException.InsufficientStorage(availableBytes))

                else -> runCatching { sessionRepository.start(config) }
            }
        }
    }

/** 녹화를 중지하고 파일을 마무리한다. */
class StopRecordingUseCase
    @Inject
    constructor(
        private val sessionRepository: RecordingSessionRepository,
    ) {
        /** 진행 중(카운트다운/녹화/일시정지) 세션을 중지한다. */
        suspend operator fun invoke(): Result<Unit> {
            val currentState = sessionRepository.state.first()
            val isActive =
                currentState is RecordingState.Recording ||
                    currentState is RecordingState.Paused ||
                    currentState is RecordingState.CountingDown
            if (!isActive) {
                return Result.failure(
                    RecordingSessionException.InvalidState(currentState, "진행 중(카운트다운/녹화/일시정지)"),
                )
            }
            return runCatching { sessionRepository.stop() }
        }
    }

/** 녹화를 일시정지한다 (기능명세서 11.2절). */
class PauseRecordingUseCase
    @Inject
    constructor(
        private val sessionRepository: RecordingSessionRepository,
    ) {
        /** 녹화 중일 때만 일시정지한다. */
        suspend operator fun invoke(): Result<Unit> {
            val currentState = sessionRepository.state.first()
            if (currentState !is RecordingState.Recording) {
                return Result.failure(RecordingSessionException.InvalidState(currentState, "녹화 중"))
            }
            return runCatching { sessionRepository.pause() }
        }
    }

/** 녹화를 재개한다 (기능명세서 11.3절: 카운트다운 없이 즉시). */
class ResumeRecordingUseCase
    @Inject
    constructor(
        private val sessionRepository: RecordingSessionRepository,
    ) {
        /** 일시정지 상태일 때만 재개한다. */
        suspend operator fun invoke(): Result<Unit> {
            val currentState = sessionRepository.state.first()
            if (currentState !is RecordingState.Paused) {
                return Result.failure(RecordingSessionException.InvalidState(currentState, "일시정지"))
            }
            return runCatching { sessionRepository.resume() }
        }
    }

/**
 * 쓰지 않기로 한 캡처 동의를 버린다 (CLAUDE.md 7절).
 *
 * 이미 진행 중인 세션 때문에 새 요청을 거절할 때 쓴다. 소비된 동의가 메모리에 남으면
 * "세션마다 새로 받는다" 는 규칙이 무너진다.
 */
class DiscardPendingConsentUseCase
    @Inject
    constructor(
        private val consentRepository: CaptureConsentRepository,
    ) {
        operator fun invoke() = consentRepository.discardPending()
    }
