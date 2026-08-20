package io.rami.screenrecorder.presentation.library

import app.cash.turbine.test
import io.rami.screenrecorder.domain.model.NameValidation
import io.rami.screenrecorder.domain.model.Recording
import io.rami.screenrecorder.domain.model.RecordingId
import io.rami.screenrecorder.domain.model.Resolution
import io.rami.screenrecorder.domain.model.SortOrder
import io.rami.screenrecorder.domain.model.TrashItem
import io.rami.screenrecorder.domain.model.VideoCodec
import io.rami.screenrecorder.domain.repository.MediaLibraryRepository
import io.rami.screenrecorder.domain.usecase.DuplicateRecordingNameException
import io.rami.screenrecorder.domain.usecase.GetRecordingsUseCase
import io.rami.screenrecorder.domain.usecase.MoveToTrashUseCase
import io.rami.screenrecorder.domain.usecase.RenameRecordingUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.minutes

@OptIn(ExperimentalCoroutinesApi::class)
class LibraryViewModelTest {
    private val recordingsFlow = MutableStateFlow<List<Recording>>(emptyList())

    private class FakeLibraryRepository(
        private val recordingsFlow: MutableStateFlow<List<Recording>>,
    ) : MediaLibraryRepository {
        val trashedIds = mutableListOf<RecordingId>()
        var renamedTo: Pair<RecordingId, String>? = null
        var duplicateSuggestion: String? = null

        override fun observeRecordings(): Flow<List<Recording>> = recordingsFlow

        override suspend fun rename(
            id: RecordingId,
            newName: String,
        ) {
            duplicateSuggestion?.let { throw DuplicateRecordingNameException(it) }
            renamedTo = id to newName
        }

        override suspend fun moveToTrash(ids: List<RecordingId>) {
            trashedIds += ids
            recordingsFlow.value = recordingsFlow.value.filterNot { it.id in ids }
        }

        override fun observeTrash(): Flow<List<TrashItem>> = emptyFlow()

        override suspend fun restore(ids: List<RecordingId>) = Unit

        override suspend fun permanentlyDelete(ids: List<RecordingId>) = Unit
    }

    private val repository = FakeLibraryRepository(recordingsFlow)

    private fun viewModel() =
        LibraryViewModel(
            getRecordings = GetRecordingsUseCase(repository),
            renameRecording = RenameRecordingUseCase(repository),
            moveToTrash = MoveToTrashUseCase(repository),
        )

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `검색어와 정렬이 목록에 반영된다`() =
        runTest {
            recordingsFlow.value =
                listOf(recording(1, "Game_intro"), recording(2, "Meeting"), recording(3, "game_play"))
            val viewModel = viewModel()

            viewModel.uiState.test {
                skipItems(1)
                assertEquals(3, awaitItem().recordings.size)

                viewModel.onQueryChanged("game")
                advanceUntilIdle()
                assertEquals(
                    listOf(3L, 1L),
                    expectMostRecentItem().recordings.map { it.id.value },
                )

                viewModel.onSortChanged(SortOrder.OLDEST_FIRST)
                advanceUntilIdle()
                assertEquals(
                    listOf(1L, 3L),
                    expectMostRecentItem().recordings.map { it.id.value },
                )
            }
        }

    @Test
    fun `길게 누르면 다중 선택 모드로 들어가고 선택을 토글한다`() =
        runTest {
            recordingsFlow.value = listOf(recording(1, "A"), recording(2, "B"))
            val viewModel = viewModel()
            viewModel.uiState.test {
                skipItems(2)

                viewModel.onItemLongPress(RecordingId(1))
                val selecting = awaitItem()
                assertTrue(selecting.isSelectionMode)
                assertEquals(setOf(RecordingId(1)), selecting.selectedIds)

                viewModel.onItemLongPress(RecordingId(1))
                val cleared = awaitItem()
                assertTrue(!cleared.isSelectionMode) { "마지막 선택 해제 시 선택 모드 종료" }
            }
        }

    @Test
    fun `선택 항목을 휴지통으로 이동하면 선택이 해제된다`() =
        runTest {
            recordingsFlow.value = listOf(recording(1, "A"), recording(2, "B"))
            val viewModel = viewModel()
            viewModel.uiState.test {
                skipItems(2)
                viewModel.onItemLongPress(RecordingId(1))
                skipItems(1)

                viewModel.onDeleteConfirmed()

                val afterDelete = awaitItem()
                assertTrue(!afterDelete.isSelectionMode)
                assertEquals(listOf(RecordingId(1)), repository.trashedIds)
            }
        }

    @Test
    fun `이름 변경이 유효하면 저장소에 위임한다`() =
        runTest {
            val viewModel = viewModel()

            viewModel.onRenameConfirmed(RecordingId(7), "새 이름")
            advanceUntilIdle()

            assertEquals(RecordingId(7) to "새 이름", repository.renamedTo)
        }

    @Test
    fun `유효하지 않은 이름은 오류 이벤트를 낸다`() =
        runTest {
            val viewModel = viewModel()
            viewModel.events.test {
                viewModel.onRenameConfirmed(RecordingId(7), "이름/불가")

                val event = awaitItem()
                assertTrue(event is LibraryEvent.RenameRejected)
                assertEquals(
                    NameValidation.ForbiddenCharacter,
                    (event as LibraryEvent.RenameRejected).reason,
                )
            }
        }

    @Test
    fun `중복 이름은 순번 제안 이벤트를 낸다`() =
        runTest {
            repository.duplicateSuggestion = "새 이름_1"
            val viewModel = viewModel()
            viewModel.events.test {
                viewModel.onRenameConfirmed(RecordingId(7), "새 이름")

                val event = awaitItem()
                assertTrue(event is LibraryEvent.RenameNeedsSuffix)
                assertEquals("새 이름_1", (event as LibraryEvent.RenameNeedsSuffix).suggestedName)
                assertEquals(RecordingId(7), event.id)
            }
        }

    private fun recording(
        id: Long,
        name: String,
    ) = Recording(
        id = RecordingId(id),
        displayName = name,
        contentUri = "content://media/$id",
        sizeBytes = 100,
        duration = 1.minutes,
        resolution = Resolution.FHD,
        frameRate = 60,
        codec = VideoCodec.H264,
        createdAtEpochMillis = id,
        bitrateBps = null,
    )
}
