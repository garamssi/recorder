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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** 다중 선택 상태 (모드 여부 + 선택된 id). */
private data class Selection(
    val mode: Boolean = false,
    val ids: Set<RecordingId> = emptySet(),
)

/** 녹화 목록 화면 상태 (기능명세서 7절). */
data class LibraryUiState(
    val isLoading: Boolean = true,
    val recordings: List<Recording> = emptyList(),
    val query: String = "",
    val sortOrder: SortOrder = SortOrder.NEWEST_FIRST,
    val isGrid: Boolean = false,
    val selectedIds: Set<RecordingId> = emptySet(),
    /** 다중 선택 모드 여부 (기능명세서 7.3절: 선택 버튼 또는 길게 누르기로 진입). */
    val isSelectionMode: Boolean = false,
) {
    /** 현재 목록의 모든 항목이 선택됐는지 (전체 선택/해제 토글용). */
    val isAllSelected: Boolean get() = recordings.isNotEmpty() && selectedIds.size == recordings.size
}

/** 목록 화면 일회성 이벤트. */
sealed interface LibraryEvent {
    /** 이름 변경 유효성 위반 (기능명세서 6.3절). */
    data class RenameRejected(
        val reason: NameValidation,
    ) : LibraryEvent

    /** 중복 이름 — 순번 제안으로 저장 여부를 물어본다 (기능명세서 6.3절). */
    data class RenameNeedsSuffix(
        val id: RecordingId,
        val suggestedName: String,
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

        // 선택 상태(모드 여부 + 선택된 id)를 하나로 관리해 갱신이 단일 방출이 되게 한다.
        private val selection = MutableStateFlow(Selection())

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
                selection,
            ) { recordings, currentQuery, currentSort, grid, currentSelection ->
                LibraryUiState(
                    isLoading = false,
                    recordings = recordings,
                    query = currentQuery,
                    sortOrder = currentSort,
                    isGrid = grid,
                    selectedIds = currentSelection.ids,
                    isSelectionMode = currentSelection.mode,
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

        /** 상단 '선택' 버튼으로 선택 모드에 진입한다 (아무 것도 선택되지 않은 상태). */
        fun onEnterSelectionMode() {
            selection.value = selection.value.copy(mode = true)
        }

        /** 길게 누르기/선택 토글 — 선택 모드로 진입하며 항목을 토글한다 (기능명세서 7.3절). */
        fun onItemLongPress(id: RecordingId) {
            selection.update { current ->
                val ids = if (id in current.ids) current.ids - id else current.ids + id
                Selection(mode = true, ids = ids)
            }
        }

        /** 전체 선택 ↔ 전체 해제 토글. */
        fun onToggleSelectAll() {
            val all =
                uiState.value.recordings
                    .map { it.id }
                    .toSet()
            selection.update { current ->
                current.copy(ids = if (current.ids.size == all.size) emptySet() else all)
            }
        }

        /** 선택 모드 종료 (선택 해제). */
        fun onClearSelection() {
            selection.value = Selection()
        }

        /** 삭제 확인 후 휴지통 이동 (기능명세서 7.3절: 확인 다이얼로그는 UI 담당). */
        fun onDeleteConfirmed() {
            val targets = selection.value.ids.toList()
            selection.value = Selection()
            viewModelScope.launch {
                moveToTrash(targets).onFailure { mutableEvents.emit(LibraryEvent.OperationFailed) }
            }
        }

        /** 단일 항목 삭제 확정 (더보기 메뉴 경로, 확인 다이얼로그 이후). */
        fun onDeleteSingleConfirmed(id: RecordingId) {
            viewModelScope.launch {
                moveToTrash(listOf(id)).onFailure { mutableEvents.emit(LibraryEvent.OperationFailed) }
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
                        when (failure) {
                            is InvalidRecordingNameException -> LibraryEvent.RenameRejected(failure.reason)
                            is io.rami.screenrecorder.domain.usecase.DuplicateRecordingNameException ->
                                LibraryEvent.RenameNeedsSuffix(id, failure.suggestedName)

                            else -> LibraryEvent.OperationFailed
                        }
                    mutableEvents.emit(event)
                }
            }
        }

        private companion object {
            const val STOP_SHARING_TIMEOUT_MS = 5_000L
        }
    }
