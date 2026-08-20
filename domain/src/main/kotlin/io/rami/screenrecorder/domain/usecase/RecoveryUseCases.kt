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
