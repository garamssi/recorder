package io.rami.screenrecorder.presentation.trash

import app.cash.turbine.test
import io.rami.screenrecorder.domain.model.Recording
import io.rami.screenrecorder.domain.model.RecordingId
import io.rami.screenrecorder.domain.model.Resolution
import io.rami.screenrecorder.domain.model.TrashItem
import io.rami.screenrecorder.domain.model.VideoCodec
import io.rami.screenrecorder.domain.repository.MediaLibraryRepository
import io.rami.screenrecorder.domain.usecase.ObserveTrashUseCase
import io.rami.screenrecorder.domain.usecase.PermanentlyDeleteUseCase
import io.rami.screenrecorder.domain.usecase.RestoreRecordingUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.minutes

@OptIn(ExperimentalCoroutinesApi::class)
class TrashViewModelTest {
    private val trashFlow = MutableStateFlow<List<TrashItem>>(emptyList())

    private class FakeTrashRepository(
        private val trashFlow: MutableStateFlow<List<TrashItem>>,
    ) : MediaLibraryRepository {
        val restoredIds = mutableListOf<RecordingId>()
        val deletedIds = mutableListOf<RecordingId>()

        override fun observeRecordings(): Flow<List<Recording>> = MutableStateFlow(emptyList())

        override suspend fun rename(
            id: RecordingId,
            newName: String,
        ) = Unit

        override suspend fun moveToTrash(ids: List<RecordingId>) = Unit

        override fun observeTrash(): Flow<List<TrashItem>> = trashFlow

        override suspend fun restore(ids: List<RecordingId>) {
            restoredIds += ids
            removeFromTrash(ids)
        }

        override suspend fun permanentlyDelete(ids: List<RecordingId>) {
            deletedIds += ids
            removeFromTrash(ids)
        }

        private fun removeFromTrash(ids: List<RecordingId>) {
            trashFlow.value = trashFlow.value.filterNot { it.recording.id in ids }
        }
    }

    private val repository = FakeTrashRepository(trashFlow)

    private fun viewModel() =
        TrashViewModel(
            observeTrash = ObserveTrashUseCase(repository),
            restoreRecording = RestoreRecordingUseCase(repository),
            permanentlyDelete = PermanentlyDeleteUseCase(repository),
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
    fun `휴지통 항목을 관찰한다`() =
        runTest {
            trashFlow.value = listOf(trashItem(1), trashItem(2))
            viewModel().items.test {
                assertEquals(null, awaitItem()) { "로드 전에는 null" }
                assertEquals(listOf(1L, 2L), awaitItem()?.map { it.recording.id.value })
            }
        }

    @Test
    fun `복원하면 저장소에 위임하고 목록에서 사라진다`() =
        runTest {
            trashFlow.value = listOf(trashItem(1), trashItem(2))
            val viewModel = viewModel()
            viewModel.items.test {
                skipItems(2)

                viewModel.onRestore(listOf(RecordingId(1)))
                advanceUntilIdle()

                assertEquals(listOf(RecordingId(1)), repository.restoredIds)
                assertEquals(listOf(2L), expectMostRecentItem()?.map { it.recording.id.value })
            }
        }

    @Test
    fun `영구 삭제 확정 시 저장소에 위임한다`() =
        runTest {
            trashFlow.value = listOf(trashItem(1))
            val viewModel = viewModel()
            viewModel.items.test {
                skipItems(2)

                viewModel.onPermanentlyDeleteConfirmed(listOf(RecordingId(1)))
                advanceUntilIdle()

                assertEquals(listOf(RecordingId(1)), repository.deletedIds)
                assertEquals(emptyList<Long>(), expectMostRecentItem()?.map { it.recording.id.value })
            }
        }

    private fun trashItem(id: Long) =
        TrashItem(
            recording =
                Recording(
                    id = RecordingId(id),
                    displayName = "녹화_$id",
                    contentUri = "content://media/$id",
                    sizeBytes = 100,
                    duration = 1.minutes,
                    resolution = Resolution.FHD,
                    frameRate = 60,
                    codec = VideoCodec.H264,
                    createdAtEpochMillis = id,
                    bitrateBps = null,
                ),
            daysUntilDeletion = 29,
        )
}
