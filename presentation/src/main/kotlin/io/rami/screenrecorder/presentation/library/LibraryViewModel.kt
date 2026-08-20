package io.rami.screenrecorder.presentation.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.rami.screenrecorder.domain.model.NameValidation
import io.rami.screenrecorder.domain.model.Recording
import io.rami.screenrecorder.domain.model.RecordingId
import io.rami.screenrecorder.domain.model.SortOrder
import io.rami.screenrecorder.domain.usecase.GetRecordingsUseCase
import io.rami.screenrecorder.domain.usecase.InvalidRecordingNameException
import io.rami.screenrecorder.domain.usecase.MoveToTrashUseCase
import io.rami.screenrecorder.domain.usecase.RenameRecordingUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** 녹화 목록 화면 상태 (기능명세서 7절). */
data class LibraryUiState(
    val isLoading: Boolean = true,
    val recordings: List<Recording> = emptyList(),
    val query: String = "",
    val sortOrder: SortOrder = SortOrder.NEWEST_FIRST,
    val isGrid: Boolean = false,
    val selectedIds: Set<RecordingId> = emptySet(),
) {
    /** 다중 선택 모드 여부 (기능명세서 7.3절: 길게 누르기로 진입). */
    val isSelectionMode: Boolean get() = selectedIds.isNotEmpty()
}

/** 목록 화면 일회성 이벤트. */
sealed interface LibraryEvent {
    /** 이름 변경 유효성 위반 (기능명세서 6.3절). */
    data class RenameRejected(
        val reason: NameValidation,
    ) : LibraryEvent

    /** 저장소 작업 실패. */
    data object OperationFailed : LibraryEvent
}

/** 녹화 목록 ViewModel (기능명세서 7절). */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class LibraryViewModel
    @Inject
    constructor(
        getRecordings: GetRecordingsUseCase,
        private val renameRecording: RenameRecordingUseCase,
        private val moveToTrash: MoveToTrashUseCase,
    ) : ViewModel() {
        private val query = MutableStateFlow("")
        private val sortOrder = MutableStateFlow(SortOrder.NEWEST_FIRST)
        private val isGrid = MutableStateFlow(false)
        private val selectedIds = MutableStateFlow<Set<RecordingId>>(emptySet())

        private val mutableEvents = MutableSharedFlow<LibraryEvent>(extraBufferCapacity = 4)

        /** 일회성 이벤트 스트림 (스낵바 등). */
        val events: SharedFlow<LibraryEvent> = mutableEvents

        /** 결합된 목록 상태. */
        val uiState: StateFlow<LibraryUiState> =
            combine(
                combine(query, sortOrder) { currentQuery, currentSort -> currentQuery to currentSort }
                    .flatMapLatest { (currentQuery, currentSort) ->
                        getRecordings(query = currentQuery, sortOrder = currentSort)
                    },
                query,
                sortOrder,
                isGrid,
                selectedIds,
            ) { recordings, currentQuery, currentSort, grid, selection ->
                LibraryUiState(
                    isLoading = false,
                    recordings = recordings,
                    query = currentQuery,
                    sortOrder = currentSort,
                    isGrid = grid,
                    selectedIds = selection,
                )
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_SHARING_TIMEOUT_MS),
                initialValue = LibraryUiState(),
            )

        /** 검색어 변경 (기능명세서 7.1절: 부분 일치). */
        fun onQueryChanged(newQuery: String) {
            query.value = newQuery
        }

        /** 정렬 기준 변경. */
        fun onSortChanged(newSortOrder: SortOrder) {
            sortOrder.value = newSortOrder
        }

        /** 리스트/그리드 전환 (기능명세서 7.1절: 선택값 유지). */
        fun onToggleLayout() {
            isGrid.value = !isGrid.value
        }

        /** 길게 누르기/선택 토글 (기능명세서 7.3절). */
        fun onItemLongPress(id: RecordingId) {
            selectedIds.value =
                if (id in selectedIds.value) selectedIds.value - id else selectedIds.value + id
        }

        /** 전체 선택. */
        fun onSelectAll() {
            selectedIds.value = uiState.value.recordings.map { it.id }.toSet()
        }

        /** 선택 해제. */
        fun onClearSelection() {
            selectedIds.value = emptySet()
        }

        /** 삭제 확인 후 휴지통 이동 (기능명세서 7.3절: 확인 다이얼로그는 UI 담당). */
        fun onDeleteConfirmed() {
            val targets = selectedIds.value.toList()
            selectedIds.value = emptySet()
            viewModelScope.launch {
                moveToTrash(targets).onFailure { mutableEvents.emit(LibraryEvent.OperationFailed) }
            }
        }

        /** 이름 변경 확정 (기능명세서 6.3절). */
        fun onRenameConfirmed(
            id: RecordingId,
            newName: String,
        ) {
            viewModelScope.launch {
                renameRecording(id, newName).onFailure { failure ->
                    val event =
                        if (failure is InvalidRecordingNameException) {
                            LibraryEvent.RenameRejected(failure.reason)
                        } else {
                            LibraryEvent.OperationFailed
                        }
                    mutableEvents.emit(event)
                }
            }
        }

        private companion object {
            const val STOP_SHARING_TIMEOUT_MS = 5_000L
        }
    }
