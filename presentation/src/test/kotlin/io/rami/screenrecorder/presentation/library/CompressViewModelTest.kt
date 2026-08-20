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
    fun `완료되면 원본 휴지통 프롬프트를 띄우고 확정 시 이동한다`() =
        runTest {
            val viewModel = viewModel()
            viewModel.uiState.test {
                skipItems(1)

                jobFlow.value = job(TranscodeStatus.SUCCEEDED)
                assertEquals(RecordingId(1), awaitItem().trashPromptFor)

                viewModel.onTrashOriginalConfirmed(RecordingId(1))
                advanceUntilIdle()

                assertNull(expectMostRecentItem().trashPromptFor) { "확정 후 프롬프트가 닫힌다" }
                assertEquals(listOf(RecordingId(1)), libraryRepository.trashedIds)
            }
        }

    @Test
    fun `프롬프트를 닫으면 재표시하지 않되 새 작업이 시작되면 초기화된다`() =
        runTest {
            val viewModel = viewModel()
            viewModel.uiState.test {
                skipItems(1)
                jobFlow.value = job(TranscodeStatus.SUCCEEDED)
                skipItems(1)

                viewModel.onTrashPromptDismissed(RecordingId(1))
                assertNull(awaitItem().trashPromptFor)

                // 같은 파일 재압축 → 완료 시 프롬프트 다시 표시 (검수 #4 회귀)
                // (null 작업 상태는 이전 상태와 동일해 StateFlow가 중복 제거한다)
                jobFlow.value = null
                viewModel.onCompressConfirmed(RecordingId(1), CompressionPreset.STANDARD)
                advanceUntilIdle()
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
