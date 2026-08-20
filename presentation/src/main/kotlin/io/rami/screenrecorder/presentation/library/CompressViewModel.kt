package io.rami.screenrecorder.presentation.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.rami.screenrecorder.domain.model.CompressionPreset
import io.rami.screenrecorder.domain.model.RecordingId
import io.rami.screenrecorder.domain.model.TranscodeJob
import io.rami.screenrecorder.domain.model.TranscodeStatus
import io.rami.screenrecorder.domain.usecase.CancelTranscodeUseCase
import io.rami.screenrecorder.domain.usecase.CompressRecordingUseCase
import io.rami.screenrecorder.domain.usecase.CompressionBlockedException
import io.rami.screenrecorder.domain.usecase.MoveToTrashUseCase
import io.rami.screenrecorder.domain.usecase.ObserveTranscodeJobUseCase
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** 압축 화면 상태 (기능명세서 8절). */
data class CompressUiState(
    /** 진행 중 작업. 없으면 null. */
    val runningJob: TranscodeJob? = null,
    /** 완료 후 "원본을 휴지통으로 이동할까요?" 대상. */
    val trashPromptFor: RecordingId? = null,
)

/** 압축 일회성 이벤트. */
sealed interface CompressEvent {
    /** 녹화 중이라 시작 불가 (명세 8절: 사유 안내). */
    data object BlockedByRecording : CompressEvent

    /** 압축 실패. */
    data object Failed : CompressEvent
}

/** 압축 다이얼로그/진행률 ViewModel (기능명세서 8절). */
@HiltViewModel
class CompressViewModel
    @Inject
    constructor(
        observeTranscodeJob: ObserveTranscodeJobUseCase,
        private val compressRecording: CompressRecordingUseCase,
        private val cancelTranscode: CancelTranscodeUseCase,
        private val moveToTrash: MoveToTrashUseCase,
    ) : ViewModel() {
        private val mutableEvents = MutableSharedFlow<CompressEvent>(extraBufferCapacity = 4)

        /** 일회성 이벤트 스트림. */
        val events: SharedFlow<CompressEvent> = mutableEvents

        /** 완료 안내를 이미 처리한 작업 (재프롬프트 방지). */
        private val dismissedPrompt = MutableStateFlow<RecordingId?>(null)

        val uiState: StateFlow<CompressUiState> =
            combine(observeTranscodeJob(), dismissedPrompt) { job, dismissed ->
                CompressUiState(
                    runningJob = job?.takeIf { it.status == TranscodeStatus.RUNNING },
                    trashPromptFor =
                        job
                            ?.takeIf { it.status == TranscodeStatus.SUCCEEDED }
                            ?.recordingId
                            ?.takeIf { it != dismissed },
                )
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_SHARING_TIMEOUT_MS),
                initialValue = CompressUiState(),
            )

        /** 프리셋 확정 → 압축 작업 등록 (명세 8절: 녹화 중 불가). */
        fun onCompressConfirmed(
            id: RecordingId,
            preset: CompressionPreset,
        ) {
            viewModelScope.launch {
                compressRecording(id, preset).onFailure { failure ->
                    val event =
                        if (failure is CompressionBlockedException) {
                            CompressEvent.BlockedByRecording
                        } else {
                            CompressEvent.Failed
                        }
                    mutableEvents.emit(event)
                }
            }
        }

        /** 진행 중 작업 취소. */
        fun onCancelTranscode() {
            viewModelScope.launch { cancelTranscode() }
        }

        /** 완료 후 원본 휴지통 이동 확정 (명세 8절 [결정]). */
        fun onTrashOriginalConfirmed(id: RecordingId) {
            dismissedPrompt.value = id
            viewModelScope.launch {
                moveToTrash(listOf(id)).onFailure { mutableEvents.emit(CompressEvent.Failed) }
            }
        }

        /** 완료 안내 닫기 (원본 유지). */
        fun onTrashPromptDismissed(id: RecordingId) {
            dismissedPrompt.value = id
        }

        private companion object {
            const val STOP_SHARING_TIMEOUT_MS = 5_000L
        }
    }
