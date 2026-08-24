package io.rami.screenrecorder.presentation.player

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.rami.screenrecorder.domain.model.MediaVolume
import io.rami.screenrecorder.domain.model.Recording
import io.rami.screenrecorder.domain.model.RecordingId
import io.rami.screenrecorder.domain.model.SortOrder
import io.rami.screenrecorder.domain.usecase.GetRecordingsUseCase
import io.rami.screenrecorder.domain.usecase.MediaVolumeUseCases
import io.rami.screenrecorder.domain.usecase.MoveToTrashUseCase
import io.rami.screenrecorder.domain.usecase.RenameRecordingUseCase
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** 플레이어 재생 대상 상태. */
sealed interface PlayerTarget {
    /** 목록 로딩 중. */
    data object Loading : PlayerTarget

    /** 재생 대상 확보. */
    data class Found(
        val recording: Recording,
    ) : PlayerTarget

    /** 대상 없음 — 삭제(휴지통 이동) 등. 목록 복귀 신호다 (기능명세서 10절). */
    data object Missing : PlayerTarget
}

/** 플레이어 화면 ViewModel (기능명세서 10절). */
@HiltViewModel
class PlayerViewModel
    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
        getRecordings: GetRecordingsUseCase,
        private val volumeUseCases: MediaVolumeUseCases,
        private val renameRecording: RenameRecordingUseCase,
        private val moveToTrash: MoveToTrashUseCase,
    ) : ViewModel() {
        /** 시스템 미디어 볼륨 (기능명세서 10절). 하드웨어 키로 바뀌어도 갱신된다. */
        val volume: StateFlow<MediaVolume> =
            volumeUseCases.observe().stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_SHARING_TIMEOUT_MS),
                initialValue = MediaVolume(level = 0, max = 0, isMuted = false),
            )
        private val recordingId =
            RecordingId(checkNotNull(savedStateHandle.get<Long>(ARG_RECORDING_ID)))

        /** 재생 대상 상태. 로딩과 부재(삭제됨)를 구분한다. */
        val target: StateFlow<PlayerTarget> =
            getRecordings(query = "", sortOrder = SortOrder.NEWEST_FIRST)
                .map { recordings ->
                    val found = recordings.firstOrNull { it.id == recordingId }
                    if (found != null) PlayerTarget.Found(found) else PlayerTarget.Missing
                }.stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(STOP_SHARING_TIMEOUT_MS),
                    initialValue = PlayerTarget.Loading,
                )

        /** 슬라이더 비율로 볼륨을 바꾼다 (기능명세서 10절). */
        fun onVolumeChanged(fraction: Float) {
            viewModelScope.launch { volumeUseCases.set(fraction) }
        }

        /** 음소거 토글 (기능명세서 10절). */
        fun onToggleMute() {
            viewModelScope.launch { volumeUseCases.toggleMute() }
        }

        /** 이름 변경 (기능명세서 6.3절). */
        fun onRenameConfirmed(newName: String) {
            viewModelScope.launch { renameRecording(recordingId, newName) }
        }

        /** 휴지통 이동 확정 — 완료 후 목록 복귀는 화면이 recording=null 전이로 처리한다 (기능명세서 10절). */
        fun onDeleteConfirmed() {
            viewModelScope.launch { moveToTrash(listOf(recordingId)) }
        }

        companion object {
            /** 네비게이션 인자 키. */
            const val ARG_RECORDING_ID = "recordingId"

            private const val STOP_SHARING_TIMEOUT_MS = 5_000L
        }
    }
