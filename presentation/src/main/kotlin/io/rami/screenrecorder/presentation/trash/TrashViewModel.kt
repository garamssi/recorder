package io.rami.screenrecorder.presentation.trash

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.rami.screenrecorder.domain.model.RecordingId
import io.rami.screenrecorder.domain.model.TrashItem
import io.rami.screenrecorder.domain.usecase.ObserveTrashUseCase
import io.rami.screenrecorder.domain.usecase.PermanentlyDeleteUseCase
import io.rami.screenrecorder.domain.usecase.RestoreRecordingUseCase
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** 휴지통 화면 ViewModel (기능명세서 9절). */
@HiltViewModel
class TrashViewModel
    @Inject
    constructor(
        observeTrash: ObserveTrashUseCase,
        private val restoreRecording: RestoreRecordingUseCase,
        private val permanentlyDelete: PermanentlyDeleteUseCase,
    ) : ViewModel() {
        /** 휴지통 항목 목록. 로드 전 null. */
        val items: StateFlow<List<TrashItem>?> =
            observeTrash().stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_SHARING_TIMEOUT_MS),
                initialValue = null,
            )

        /** 복원 (기능명세서 9절). */
        fun onRestore(ids: List<RecordingId>) {
            viewModelScope.launch { restoreRecording(ids) }
        }

        /** 영구 삭제 확정 (확인 다이얼로그 이후, 기능명세서 9절). */
        fun onPermanentlyDeleteConfirmed(ids: List<RecordingId>) {
            viewModelScope.launch { permanentlyDelete(ids) }
        }

        private companion object {
            const val STOP_SHARING_TIMEOUT_MS = 5_000L
        }
    }
