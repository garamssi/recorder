package io.rami.screenrecorder.presentation.settings

import app.cash.turbine.test
import io.rami.screenrecorder.domain.model.AppSettings
import io.rami.screenrecorder.domain.model.ThemeSetting
import io.rami.screenrecorder.domain.repository.SettingsRepository
import io.rami.screenrecorder.domain.usecase.ObserveSettingsUseCase
import io.rami.screenrecorder.domain.usecase.UpdateSettingsUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {
    private val settingsFlow = MutableStateFlow(AppSettings.DEFAULT)

    private val repository =
        object : SettingsRepository {
            override val settings: Flow<AppSettings> = settingsFlow

            override suspend fun update(transform: (AppSettings) -> AppSettings) {
                settingsFlow.value = transform(settingsFlow.value)
            }
        }

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `설정 변경이 저장소에 즉시 반영된다`() =
        runTest {
            val viewModel =
                SettingsViewModel(
                    observeSettings = ObserveSettingsUseCase(repository),
                    updateSettings = UpdateSettingsUseCase(repository),
                )

            viewModel.settings.test {
                skipItems(1) // 초기 null

                assertEquals(AppSettings.DEFAULT, awaitItem())

                viewModel.update { it.copy(theme = ThemeSetting.DARK) }

                assertEquals(ThemeSetting.DARK, awaitItem()?.theme)
                assertEquals(ThemeSetting.DARK, settingsFlow.value.theme)
            }
        }
}
