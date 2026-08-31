package io.rami.screenrecorder.presentation.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.rami.screenrecorder.domain.model.AutoBitratePolicy
import io.rami.screenrecorder.domain.model.BitrateOption
import io.rami.screenrecorder.domain.model.CaptureModeKind
import io.rami.screenrecorder.domain.model.PendingRecovery
import io.rami.screenrecorder.domain.model.RecordableTimeEstimator
import io.rami.screenrecorder.domain.model.Recording
import io.rami.screenrecorder.domain.model.RecordingConfig
import io.rami.screenrecorder.domain.model.RecordingId
import io.rami.screenrecorder.domain.model.RecordingState
import io.rami.screenrecorder.domain.model.Resolution
import io.rami.screenrecorder.domain.model.SortOrder
import io.rami.screenrecorder.domain.repository.StorageRepository
import io.rami.screenrecorder.domain.usecase.DiscardRecoveryUseCase
import io.rami.screenrecorder.domain.usecase.GetPendingRecoveriesUseCase
import io.rami.screenrecorder.domain.usecase.GetRecordingsUseCase
import io.rami.screenrecorder.domain.usecase.ObserveCompletedRecordingUseCase
import io.rami.screenrecorder.domain.usecase.ObserveRecordingStateUseCase
import io.rami.screenrecorder.domain.usecase.ObserveSettingsUseCase
import io.rami.screenrecorder.domain.usecase.RecoverRecordingUseCase
import io.rami.screenrecorder.domain.usecase.RenameRecordingUseCase
import io.rami.screenrecorder.domain.usecase.SkipCountdownUseCase
import io.rami.screenrecorder.domain.usecase.UpdateSettingsUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
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

/** 홈 화면이 쓰는 유스케이스 묶음 (DI 조립 단순화용). */
@Suppress("LongParameterList") // 단순 DI 홀더 — 파라미터 수가 곧 내용이다.
class HomeUseCases
    @Inject
    constructor(
        val observeSettings: ObserveSettingsUseCase,
        val updateSettings: UpdateSettingsUseCase,
        val observeRecordingState: ObserveRecordingStateUseCase,
        val skipCountdown: SkipCountdownUseCase,
        val getRecordings: GetRecordingsUseCase,
        val observeCompletedRecording: ObserveCompletedRecordingUseCase,
        val renameRecording: RenameRecordingUseCase,
        val getPendingRecoveries: GetPendingRecoveriesUseCase,
        val recoverRecording: RecoverRecordingUseCase,
        val discardRecovery: DiscardRecoveryUseCase,
    )

/** 홈 화면 ViewModel (기능명세서 2절). */
@HiltViewModel
class HomeViewModel
    @Inject
    constructor(
        useCases: HomeUseCases,
        storageRepository: StorageRepository,
    ) : ViewModel() {
        private val updateSettings = useCases.updateSettings
        private val skipCountdown = useCases.skipCountdown
        private val renameRecording = useCases.renameRecording
        private val observeSettings = useCases.observeSettings
        private val observeRecordingState = useCases.observeRecordingState
        private val getRecordings = useCases.getRecordings
        private val recoverRecording = useCases.recoverRecording
        private val discardRecovery = useCases.discardRecovery

        /** 저장 완료 이벤트 (기능명세서 6.2절: 저장 직후 스낵바에서 이름 변경). */
        val completedRecordings: Flow<Recording> = useCases.observeCompletedRecording()

        private val mutablePendingRecoveries = MutableStateFlow<List<PendingRecovery>>(emptyList())

        /** 크래시로 발행되지 못한 임시 녹화 목록 (기능명세서 6.1절: 복구/삭제 제안). */
        val pendingRecoveries: StateFlow<List<PendingRecovery>> = mutablePendingRecoveries.asStateFlow()

        private val mutableRecoveringId = MutableStateFlow<String?>(null)

        /** 지금 복구/삭제 중인 임시 파일 id. 없으면 null (기능명세서 6.1절 [결정]). */
        val recoveringId: StateFlow<String?> = mutableRecoveringId.asStateFlow()

        private val mutableRecoveryFailed = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

        /** 복구/삭제가 실패했을 때 한 번 발생하는 이벤트 (스낵바 안내용). */
        val recoveryFailed: Flow<Unit> = mutableRecoveryFailed

        init {
            viewModelScope.launch {
                // 녹화가 진행 중이 아닐 때만 고아 임시 파일을 조회한다.
                // 서비스가 녹화 중인 채로 Activity가 재생성되면 활성 temp 파일을 고아로 오인하기 때문이다.
                // finalize는 임시 파일을 정리한 뒤 Idle로 전이하므로, Idle 시점의 조회는 활성 파일을 포함하지 않는다.
                observeRecordingState().first { it is RecordingState.Idle }
                mutablePendingRecoveries.value = useCases.getPendingRecoveries()
            }
        }

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

        /** 저장 직후 이름 변경 (기능명세서 6.2절 스낵바 액션). */
        fun onRenameConfirmed(
            id: RecordingId,
            newName: String,
        ) {
            viewModelScope.launch { renameRecording(id, newName) }
        }

        /** 카운트다운 오버레이 탭 = 즉시 시작 (기능명세서 3절). */
        fun onCountdownTapped() {
            skipCountdown()
        }

        /** 임시 파일 복구 확정 (기능명세서 6.1절). MediaStore로 발행하고 목록에서 제거한다. */
        fun onRecoverConfirmed(id: String) {
            runRecoveryAction(id) { recoverRecording(id) }
        }

        /** 임시 파일 삭제 (기능명세서 6.1절). */
        fun onDiscardRecovery(id: String) {
            runRecoveryAction(id) { discardRecovery(id) }
        }

        /**
         * 복구/삭제를 실행하고 목록에서 제거한다.
         *
         * 이미 진행 중이면 아무것도 하지 않는다 (기능명세서 6.1절 [결정]). 1시간짜리 녹화는
         * remux + 발행에 수 초가 걸리는데, 그 사이 버튼을 다시 누르면 같은 임시 파일이
         * 동시에 여러 번 발행돼 똑같은 녹화본이 누른 횟수만큼 쌓인다.
         *
         * MediaStore 실패 등 앱이 해결할 수 없는 외부 오류는 크래시 대신 안내한다.
         * 임시 파일은 남겨 두어 다음 실행에서 다시 시도할 수 있게 한다(증상 은폐가 아니라 외부 오류 처리).
         */
        private fun runRecoveryAction(
            id: String,
            action: suspend () -> Unit,
        ) {
            if (mutableRecoveringId.value != null) return
            mutableRecoveringId.value = id
            viewModelScope.launch {
                val succeeded =
                    try {
                        action()
                        true
                    } catch (
                        @Suppress("TooGenericExceptionCaught") failure: Exception,
                    ) {
                        android.util.Log.w(LOG_TAG, "임시 파일 복구/삭제 실패: $id", failure)
                        false
                    } finally {
                        // 실패했더라도 내려야 사용자가 다시 시도할 수 있다.
                        mutableRecoveringId.value = null
                    }
                if (succeeded) {
                    removePending(id)
                } else {
                    mutableRecoveryFailed.emit(Unit)
                }
            }
        }

        private fun removePending(id: String) {
            mutablePendingRecoveries.update { list -> list.filterNot { it.id == id } }
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
            const val LOG_TAG = "HomeViewModel"
        }
    }
