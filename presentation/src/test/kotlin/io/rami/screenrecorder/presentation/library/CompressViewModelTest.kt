package io.rami.screenrecorder.presentation.library

import app.cash.turbine.test
import io.rami.screenrecorder.domain.model.CompressionPreset
import io.rami.screenrecorder.domain.model.Recording
import io.rami.screenrecorder.domain.model.RecordingConfig
import io.rami.screenrecorder.domain.model.RecordingId
import io.rami.screenrecorder.domain.model.RecordingSessionEvent
import io.rami.screenrecorder.domain.model.RecordingState
import io.rami.screenrecorder.domain.model.TranscodeJob
import io.rami.screenrecorder.domain.model.TranscodeStatus
import io.rami.screenrecorder.domain.model.TrashItem
import io.rami.screenrecorder.domain.repository.MediaLibraryRepository
import io.rami.screenrecorder.domain.repository.RecordingSessionRepository
import io.rami.screenrecorder.domain.repository.TranscodeRepository
import io.rami.screenrecorder.domain.usecase.CancelTranscodeUseCase
import io.rami.screenrecorder.domain.usecase.CompressRecordingUseCase
import io.rami.screenrecorder.domain.usecase.MoveToTrashUseCase
import io.rami.screenrecorder.domain.usecase.ObserveTranscodeJobUseCase
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
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.minutes

@OptIn(ExperimentalCoroutinesApi::class)
class CompressViewModelTest {
    private val jobFlow = MutableStateFlow<TranscodeJob?>(null)
    private val sessionState = MutableStateFlow<RecordingState>(RecordingState.Idle)

    private class FakeTranscodeRepository(
        private val jobFlow: MutableStateFlow<TranscodeJob?>,
    ) : TranscodeRepository {
        var enqueued: Pair<RecordingId, CompressionPreset>? = null
        var cancelled = false
        var cleared = false

        override fun observeJob(): Flow<TranscodeJob?> = jobFlow

        override suspend fun enqueue(
            recordingId: RecordingId,
            preset: CompressionPreset,
        ) {
            enqueued = recordingId to preset
        }

        override suspend fun cancel() {
            cancelled = true
        }

        override suspend fun clearCompleted() {
            cleared = true
            // 실제 pruneWork처럼 완료 작업을 제거해 프롬프트가 재발생하지 않게 한다.
            jobFlow.value = null
        }
    }

    private class FakeSessionRepository(
        override val state: MutableStateFlow<RecordingState>,
    ) : RecordingSessionRepository {
        override val completedRecordings: Flow<Recording> = emptyFlow()
        override val sessionEvents: Flow<RecordingSessionEvent> = emptyFlow()

        override suspend fun start(config: RecordingConfig) = Unit

        override fun skipCountdown() = Unit

        override suspend fun stop() = Unit

        override suspend fun pause() = Unit

        override suspend fun resume() = Unit
    }

    private class FakeLibraryRepository : MediaLibraryRepository {
        val trashedIds = mutableListOf<RecordingId>()

        override fun observeRecordings(): Flow<List<Recording>> = emptyFlow()

        override suspend fun rename(
            id: RecordingId,
            newName: String,
        ) = Unit

        override suspend fun moveToTrash(ids: List<RecordingId>) {
            trashedIds += ids
        }

        override fun observeTrash(): Flow<List<TrashItem>> = emptyFlow()

        override suspend fun restore(ids: List<RecordingId>) = Unit

        override suspend fun permanentlyDelete(ids: List<RecordingId>) = Unit
    }

    private val transcodeRepository = FakeTranscodeRepository(jobFlow)
    private val libraryRepository = FakeLibraryRepository()

    private fun viewModel() =
        CompressViewModel(
            observeTranscodeJob = ObserveTranscodeJobUseCase(transcodeRepository),
            compressRecording =
                CompressRecordingUseCase(
                    sessionRepository = FakeSessionRepository(sessionState),
                    transcodeRepository = transcodeRepository,
                ),
            cancelTranscode = CancelTranscodeUseCase(transcodeRepository),
            clearCompletedTranscode =
                io.rami.screenrecorder.domain.usecase
                    .ClearCompletedTranscodeUseCase(transcodeRepository),
            moveToTrash = MoveToTrashUseCase(libraryRepository),
        )

    private fun job(status: TranscodeStatus) =
        TranscodeJob(
            recordingId = RecordingId(1),
            preset = CompressionPreset.STANDARD,
            progressPercent = 50,
            status = status,
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
    fun `압축 확정 시 작업을 등록한다`() =
        runTest {
            val viewModel = viewModel()
            viewModel.uiState.test {
                skipItems(1)

                viewModel.onCompressConfirmed(RecordingId(1), CompressionPreset.MAXIMUM)
                advanceUntilIdle()

                assertEquals(RecordingId(1) to CompressionPreset.MAXIMUM, transcodeRepository.enqueued)
            }
        }

    @Test
    fun `녹화 중이면 차단 이벤트를 낸다`() =
        runTest {
            sessionState.value = RecordingState.Recording(elapsed = 1.minutes)
            val viewModel = viewModel()
            viewModel.events.test {
                viewModel.onCompressConfirmed(RecordingId(1), CompressionPreset.STANDARD)

                assertEquals(CompressEvent.BlockedByRecording, awaitItem())
                assertNull(transcodeRepository.enqueued)
            }
        }

    @Test
    fun `이미 진행 중이면 Busy 이벤트를 내고 등록하지 않는다`() =
        runTest {
            jobFlow.value = job(TranscodeStatus.RUNNING)
            val viewModel = viewModel()
            viewModel.uiState.test {
                skipItems(1)
                assertEquals(50, awaitItem().runningJob?.progressPercent)

                viewModel.events.test {
                    viewModel.onCompressConfirmed(RecordingId(2), CompressionPreset.STANDARD)

                    assertEquals(CompressEvent.Busy, awaitItem())
                    assertNull(transcodeRepository.enqueued)
                }
            }
        }

    @Test
    fun `사용자가 시작한 압축이 완료되면 프롬프트를 띄우고 확정 시 이동한다`() =
        runTest {
            val viewModel = viewModel()
            viewModel.uiState.test {
                skipItems(1)

                // 사용자가 이 화면에서 압축을 시작한다.
                viewModel.onCompressConfirmed(RecordingId(1), CompressionPreset.STANDARD)
                advanceUntilIdle()
                // 완료되면 그 대상에 프롬프트가 뜬다.
                jobFlow.value = job(TranscodeStatus.SUCCEEDED)
                assertEquals(RecordingId(1), awaitItem().trashPromptFor)

                viewModel.onTrashOriginalConfirmed(RecordingId(1))
                advanceUntilIdle()

                assertNull(expectMostRecentItem().trashPromptFor) { "확정 후 프롬프트가 닫힌다" }
                assertEquals(listOf(RecordingId(1)), libraryRepository.trashedIds)
                assertTrue(transcodeRepository.cleared) { "완료 작업 정리(pruneWork) 호출" }
            }
        }

    @Test
    fun `화면 재진입 시 이미 완료된 과거 작업은 프롬프트를 띄우지 않는다`() =
        runTest {
            // 이전 세션에서 완료돼 WorkManager에 남아 있는 SUCCEEDED 작업.
            jobFlow.value = job(TranscodeStatus.SUCCEEDED)
            // 사용자가 이 화면에서 압축을 시작하지 않았으므로(awaitingCompletion null) 프롬프트는 뜨지 않는다.
            val viewModel = viewModel()
            viewModel.uiState.test {
                assertNull(awaitItem().trashPromptFor) { "초기 상태" }
                expectNoEvents()
            }
        }

    @Test
    fun `프롬프트를 닫으면 재표시하지 않되 새 압축이 완료되면 다시 뜬다`() =
        runTest {
            val viewModel = viewModel()
            viewModel.uiState.test {
                skipItems(1)
                viewModel.onCompressConfirmed(RecordingId(1), CompressionPreset.STANDARD)
                advanceUntilIdle()
                jobFlow.value = job(TranscodeStatus.SUCCEEDED)
                skipItems(1)

                viewModel.onTrashPromptDismissed()
                advanceUntilIdle()
                assertNull(expectMostRecentItem().trashPromptFor)

                // 같은 파일 재압축을 시작하면 완료 시 프롬프트가 다시 표시된다.
                viewModel.onCompressConfirmed(RecordingId(1), CompressionPreset.STANDARD)
                advanceUntilIdle()
                jobFlow.value = job(TranscodeStatus.RUNNING)
                skipItems(1)
                jobFlow.value = job(TranscodeStatus.SUCCEEDED)

                assertEquals(RecordingId(1), awaitItem().trashPromptFor)
            }
        }

    @Test
    fun `취소를 위임한다`() =
        runTest {
            val viewModel = viewModel()
            viewModel.onCancelTranscode()
            advanceUntilIdle()

            assertTrue(transcodeRepository.cancelled)
        }
}
