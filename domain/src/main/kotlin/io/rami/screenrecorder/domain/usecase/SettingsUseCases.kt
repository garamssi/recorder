package io.rami.screenrecorder.domain.usecase

import io.rami.screenrecorder.domain.model.AppSettings
import io.rami.screenrecorder.domain.repository.RecordingSessionRepository
import io.rami.screenrecorder.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/** 앱 설정을 관찰한다 (기능명세서 4절). */
class ObserveSettingsUseCase
    @Inject
    constructor(
        private val settingsRepository: SettingsRepository,
    ) {
        /** 설정 스트림. */
        operator fun invoke(): Flow<AppSettings> = settingsRepository.settings
    }

/** 앱 설정을 갱신한다 (즉시 반영). */
class UpdateSettingsUseCase
    @Inject
    constructor(
        private val settingsRepository: SettingsRepository,
    ) {
        /** [transform] 결과로 설정을 갱신한다. */
        suspend operator fun invoke(transform: (AppSettings) -> AppSettings): Result<Unit> =
            runCatching { settingsRepository.update(transform) }
    }

/** 진행 중인 카운트다운을 건너뛴다 (기능명세서 3절: 탭=스킵). */
class SkipCountdownUseCase
    @Inject
    constructor(
        private val sessionRepository: RecordingSessionRepository,
    ) {
        /** 카운트다운 스킵 요청. 카운트다운 중이 아니면 무시된다. */
        operator fun invoke() = sessionRepository.skipCountdown()
    }
