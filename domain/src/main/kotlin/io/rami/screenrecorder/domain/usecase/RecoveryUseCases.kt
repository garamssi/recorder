package io.rami.screenrecorder.domain.usecase

import io.rami.screenrecorder.domain.model.PendingRecovery
import io.rami.screenrecorder.domain.model.Recording
import io.rami.screenrecorder.domain.repository.RecordingRecoveryRepository
import javax.inject.Inject

/** 이전 실행의 미발행 임시 파일 목록을 가져온다 (기능명세서 6.1절). */
class GetPendingRecoveriesUseCase
    @Inject
    constructor(
        private val recoveryRepository: RecordingRecoveryRepository,
    ) {
        suspend operator fun invoke(): List<PendingRecovery> = recoveryRepository.pendingRecoveries()
    }

/** 임시 파일을 MediaStore로 복구한다. 재생 가능한 내용이 없으면 null (기능명세서 6.1절). */
class RecoverRecordingUseCase
    @Inject
    constructor(
        private val recoveryRepository: RecordingRecoveryRepository,
    ) {
        suspend operator fun invoke(id: String): Recording? = recoveryRepository.recover(id)
    }

/** 임시 파일을 삭제한다 (기능명세서 6.1절). */
class DiscardRecoveryUseCase
    @Inject
    constructor(
        private val recoveryRepository: RecordingRecoveryRepository,
    ) {
        suspend operator fun invoke(id: String) = recoveryRepository.discard(id)
    }

/**
 * 발행 도중 죽어 남은 미완성 레코드를 회수한다 (기능명세서 6.1절 [결정]).
 *
 * 복구 대기 목록 조회보다 먼저 실행해야 한다 — 순서가 반대면 복구 재발행이 고아 레코드와
 * 파일명이 충돌해 `(1)` 접미어가 붙는다.
 */
class CleanUpAbandonedPublishesUseCase
    @Inject
    constructor(
        private val recoveryRepository: RecordingRecoveryRepository,
    ) {
        suspend operator fun invoke() = recoveryRepository.cleanUpAbandonedPublishes()
    }
