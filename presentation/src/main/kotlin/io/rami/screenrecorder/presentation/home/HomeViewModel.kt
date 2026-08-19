package io.rami.screenrecorder.presentation.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.rami.screenrecorder.domain.model.AutoBitratePolicy
import io.rami.screenrecorder.domain.model.BitrateOption
import io.rami.screenrecorder.domain.model.CaptureModeKind
import io.rami.screenrecorder.domain.model.RecordableTimeEstimator
import io.rami.screenrecorder.domain.model.Recording
import io.rami.screenrecorder.domain.model.RecordingConfig
import io.rami.screenrecorder.domain.model.RecordingState
import io.rami.screenrecorder.domain.model.Resolution
import io.rami.screenrecorder.domain.model.SortOrder
import io.rami.screenrecorder.domain.repository.StorageRepository
import io.rami.screenrecorder.domain.usecase.GetRecordingsUseCase
import io.rami.screenrecorder.domain.usecase.ObserveRecordingStateUseCase
import io.rami.screenrecorder.domain.usecase.ObserveSettingsUseCase
import io.rami.screenrecorder.domain.usecase.SkipCountdownUseCase
import io.rami.screenrecorder.domain.usecase.UpdateSettingsUseCase
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.time.Duration

/** 홈 화면 상태 (기능명세서 2.1절). */
data class HomeUiState(
    val isLoading: Boolean = true,
    val selectedMode: CaptureModeKind = CaptureModeKind.FULL_SCREEN,
    val preset: RecordingConfig = RecordingConfig.DEFAULT,
    val recordingState: RecordingState = RecordingState.Idle,
    val availableBytes: Long = 0L,
    val estimatedRecordableTime: Duration = Duration.ZERO,
    val canStartRecording: Boolean = false,
    val recentRecordings: List<Recording> = emptyList(),
)

/** 홈 화면 ViewModel (기능명세서 2절). */
@HiltViewModel
class HomeViewModel
    @Inject
    constructor(
        observeSettings: ObserveSettingsUseCase,
        private val updateSettings: UpdateSettingsUseCase,
        observeRecordingState: ObserveRecordingStateUseCase,
        private val skipCountdown: SkipCountdownUseCase,
        storageRepository: StorageRepository,
        getRecordings: GetRecordingsUseCase,
    ) : ViewModel() {
        /** 결합된 홈 상태 스트림. */
        val uiState: StateFlow<HomeUiState> =
            combine(
                observeSettings(),
                observeRecordingState(),
                storageRepository.observeAvailableBytes(),
                getRecordings(query = "", sortOrder = SortOrder.NEWEST_FIRST),
            ) { settings, recordingState, availableBytes, recordings ->
                HomeUiState(
                    isLoading = false,
                    selectedMode = settings.selectedCaptureMode,
                    preset = settings.recording,
                    recordingState = recordingState,
                    availableBytes = availableBytes,
                    estimatedRecordableTime =
                        RecordableTimeEstimator.estimate(
                            availableBytes = availableBytes,
                            videoBitrateBps = settings.recording.estimateBitrateBps(),
                        ),
                    canStartRecording = RecordableTimeEstimator.canStartRecording(availableBytes),
                    recentRecordings = recordings.take(RECENT_RECORDING_COUNT),
                )
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_SHARING_TIMEOUT_MS),
                initialValue = HomeUiState(),
            )

        /** 모드 세그먼트 선택 — 설정에 저장해 마지막 선택을 유지한다 (기능명세서 2.1절). */
        fun onModeSelected(kind: CaptureModeKind) {
            viewModelScope.launch {
                updateSettings { it.copy(selectedCaptureMode = kind) }
            }
        }

        /** 녹화 옵션 시트에서 프리셋을 변경한다 — 설정과 동일 저장소를 갱신한다. */
        fun onPresetChanged(transform: (RecordingConfig) -> RecordingConfig) {
            viewModelScope.launch {
                updateSettings { it.copy(recording = transform(it.recording)) }
            }
        }

        /** 카운트다운 오버레이 탭 = 즉시 시작 (기능명세서 3절). */
        fun onCountdownTapped() {
            skipCountdown()
        }

        private fun RecordingConfig.estimateBitrateBps(): Int =
            when (val option = bitrate) {
                is BitrateOption.Fixed -> option.megabitsPerSecond * BPS_PER_MBPS
                is BitrateOption.Auto ->
                    // 추정 용도이므로 기기 최대 해상도는 FHD로 근사한다 (표시 문구 "약 N시간").
                    AutoBitratePolicy.bitrateBpsFor(resolution.resolve(Resolution.FHD), frameRate)
            }

        private companion object {
            const val RECENT_RECORDING_COUNT = 3
            const val STOP_SHARING_TIMEOUT_MS = 5_000L
            const val BPS_PER_MBPS = 1_000_000
        }
    }
