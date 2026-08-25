package io.rami.screenrecorder.domain.usecase

import io.rami.screenrecorder.domain.model.TimeLimit
import io.rami.screenrecorder.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * 현재 녹화 시간 제한을 관찰한다 (기능명세서 11.4절).
 *
 * 홈의 녹화 옵션 시트와 플로팅 버블이 같은 값을 보여야 하므로 저장된 설정 하나만 바라본다.
 */
class ObserveTimeLimitUseCase
    @Inject
    constructor(
        private val settingsRepository: SettingsRepository,
    ) {
        /** 시간 제한 스트림. 값이 실제로 바뀔 때만 방출한다. */
        operator fun invoke(): Flow<TimeLimit> =
            settingsRepository.settings
                .map { it.recording.timeLimit }
                .distinctUntilChanged()
    }

/**
 * 녹화 시간 제한을 바꾼다 (기능명세서 11.4절).
 *
 * 어느 진입점에서 바꾸든 같은 설정을 갱신하므로 다른 진입점에도 즉시 반영된다.
 */
class SetTimeLimitUseCase
    @Inject
    constructor(
        private val settingsRepository: SettingsRepository,
    ) {
        /** [timeLimit]으로 설정을 갱신한다. 나머지 녹화 설정은 그대로 둔다. */
        suspend operator fun invoke(timeLimit: TimeLimit) {
            settingsRepository.update { settings ->
                settings.copy(recording = settings.recording.copy(timeLimit = timeLimit))
            }
        }
    }
