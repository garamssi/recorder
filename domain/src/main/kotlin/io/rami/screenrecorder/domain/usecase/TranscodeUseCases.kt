package io.rami.screenrecorder.domain.usecase

import io.rami.screenrecorder.domain.model.TranscodeJob
import io.rami.screenrecorder.domain.repository.TranscodeRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/** 현재 압축 작업 상태를 관찰한다 (기능명세서 8절: 진행률/완료). */
class ObserveTranscodeJobUseCase
    @Inject
    constructor(
        private val transcodeRepository: TranscodeRepository,
    ) {
        operator fun invoke(): Flow<TranscodeJob?> = transcodeRepository.observeJob()
    }

/** 진행 중인 압축 작업을 취소한다 (기능명세서 8절: 취소 가능). */
class CancelTranscodeUseCase
    @Inject
    constructor(
        private val transcodeRepository: TranscodeRepository,
    ) {
        suspend operator fun invoke() = transcodeRepository.cancel()
    }
