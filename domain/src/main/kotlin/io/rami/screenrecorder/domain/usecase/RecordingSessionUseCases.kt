package io.rami.screenrecorder.domain.usecase

import io.rami.screenrecorder.domain.model.RecordingConfig
import io.rami.screenrecorder.domain.repository.RecordingSessionRepository
import io.rami.screenrecorder.domain.repository.StorageRepository
import javax.inject.Inject

/** 녹화를 시작한다 (기능명세서 2.2절: 저장 공간 500MB 미만이면 거부). */
class StartRecordingUseCase
    @Inject
    constructor(
        private val sessionRepository: RecordingSessionRepository,
        private val storageRepository: StorageRepository,
    ) {
        /** [config]로 녹화를 시작한다. */
        suspend operator fun invoke(config: RecordingConfig): Result<Unit> = TODO()
    }

/** 녹화를 중지하고 파일을 마무리한다. */
class StopRecordingUseCase
    @Inject
    constructor(
        private val sessionRepository: RecordingSessionRepository,
    ) {
        /** 진행 중(카운트다운/녹화/일시정지) 세션을 중지한다. */
        suspend operator fun invoke(): Result<Unit> = TODO()
    }

/** 녹화를 일시정지한다 (기능명세서 11.2절). */
class PauseRecordingUseCase
    @Inject
    constructor(
        private val sessionRepository: RecordingSessionRepository,
    ) {
        /** 녹화 중일 때만 일시정지한다. */
        suspend operator fun invoke(): Result<Unit> = TODO()
    }

/** 녹화를 재개한다 (기능명세서 11.3절: 카운트다운 없이 즉시). */
class ResumeRecordingUseCase
    @Inject
    constructor(
        private val sessionRepository: RecordingSessionRepository,
    ) {
        /** 일시정지 상태일 때만 재개한다. */
        suspend operator fun invoke(): Result<Unit> = TODO()
    }
