package io.rami.screenrecorder.domain.usecase

import io.rami.screenrecorder.domain.model.CompressionPreset
import io.rami.screenrecorder.domain.model.RecordingId
import io.rami.screenrecorder.domain.model.RecordingState
import io.rami.screenrecorder.domain.repository.RecordingSessionRepository
import io.rami.screenrecorder.domain.repository.TranscodeRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/** 녹화 세션이 인코더를 점유 중이라 압축을 시작할 수 없다 (기능명세서 8절). */
class CompressionBlockedException : Exception("녹화 중에는 압축을 시작할 수 없다")

/** 압축 작업을 등록한다 (기능명세서 8절: 녹화 중 불가, 원본 보존). */
class CompressRecordingUseCase
    @Inject
    constructor(
        private val sessionRepository: RecordingSessionRepository,
        private val transcodeRepository: TranscodeRepository,
    ) {
        suspend operator fun invoke(
            id: RecordingId,
            preset: CompressionPreset,
        ): Result<Unit> {
            // 인코더 자원 경합 방지: 세션이 살아있는 동안(카운트다운~중지 처리 중) 전부 차단한다.
            if (sessionRepository.state.first() != RecordingState.Idle) {
                return Result.failure(CompressionBlockedException())
            }
            return runCatching { transcodeRepository.enqueue(id, preset) }
        }
    }
