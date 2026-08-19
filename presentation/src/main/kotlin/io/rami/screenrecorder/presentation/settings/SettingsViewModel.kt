package io.rami.screenrecorder.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.rami.screenrecorder.domain.model.AppSettings
import io.rami.screenrecorder.domain.usecase.ObserveSettingsUseCase
import io.rami.screenrecorder.domain.usecase.UpdateSettingsUseCase
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** 설정 화면 ViewModel (기능명세서 4절: 모든 설정은 즉시 반영). */
@HiltViewModel
class SettingsViewModel
    @Inject
    constructor(
        observeSettings: ObserveSettingsUseCase,
        private val updateSettings: UpdateSettingsUseCase,
    ) : ViewModel() {
        /** 현재 설정. 로드 전에는 null. */
        val settings: StateFlow<AppSettings?> =
            observeSettings().stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_SHARING_TIMEOUT_MS),
                initialValue = null,
            )

        /** [transform]으로 설정을 갱신한다. */
        fun update(transform: (AppSettings) -> AppSettings) {
            viewModelScope.launch { updateSettings(transform) }
        }

        private companion object {
            const val STOP_SHARING_TIMEOUT_MS = 5_000L
        }
    }
