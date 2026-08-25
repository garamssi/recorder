package io.rami.screenrecorder.domain.usecase

import io.rami.screenrecorder.domain.model.AppSettings
import io.rami.screenrecorder.domain.model.CaptureModeKind
import io.rami.screenrecorder.domain.model.TimeLimit
import io.rami.screenrecorder.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.minutes

/**
 * 녹화 시간 제한 유스케이스 (기능명세서 11.4절).
 *
 * 홈의 녹화 옵션 시트와 플로팅 버블이 같은 진입점을 쓰므로, 두 화면이 같은
 * [AppSettings.recording] 값을 읽고 쓰는지가 핵심이다.
 */
class TimeLimitUseCaseTest {
    private val repository = FakeSettingsRepository()

    @Test
    fun `관찰 유스케이스는 설정에 저장된 시간 제한을 내보낸다`() =
        runTest {
            repository.settingsState.value =
                AppSettings.DEFAULT.withTimeLimit(TimeLimit.Limited(10.minutes))

            val observed = ObserveTimeLimitUseCase(repository)().first()

            assertEquals(TimeLimit.Limited(10.minutes), observed)
        }

    @Test
    fun `설정 유스케이스는 시간 제한만 바꾸고 다른 설정은 건드리지 않는다`() =
        runTest {
            repository.settingsState.value =
                AppSettings.DEFAULT.copy(selectedCaptureMode = CaptureModeKind.REGION)

            SetTimeLimitUseCase(repository)(TimeLimit.Limited(10.minutes))

            val saved = repository.settingsState.value
            assertEquals(TimeLimit.Limited(10.minutes), saved.recording.timeLimit)
            assertEquals(CaptureModeKind.REGION, saved.selectedCaptureMode)
            assertEquals(AppSettings.DEFAULT.recording.frameRate, saved.recording.frameRate)
        }

    @Test
    fun `제한 없음으로 되돌릴 수 있다`() =
        runTest {
            repository.settingsState.value =
                AppSettings.DEFAULT.withTimeLimit(TimeLimit.Limited(10.minutes))

            SetTimeLimitUseCase(repository)(TimeLimit.None)

            assertEquals(TimeLimit.None, repository.settingsState.value.recording.timeLimit)
        }

    @Test
    fun `한쪽에서 바꾼 값이 관찰 스트림에 그대로 이어진다`() =
        runTest {
            val observe = ObserveTimeLimitUseCase(repository)

            SetTimeLimitUseCase(repository)(TimeLimit.Limited(10.minutes))

            assertEquals(TimeLimit.Limited(10.minutes), observe().first())
        }
}

private fun AppSettings.withTimeLimit(timeLimit: TimeLimit) = copy(recording = recording.copy(timeLimit = timeLimit))

private class FakeSettingsRepository : SettingsRepository {
    val settingsState = MutableStateFlow(AppSettings.DEFAULT)

    override val settings: Flow<AppSettings> get() = settingsState

    override suspend fun update(transform: (AppSettings) -> AppSettings) {
        settingsState.value = transform(settingsState.value)
    }
}
