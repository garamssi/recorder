package io.rami.screenrecorder.presentation.home

import app.cash.turbine.test
import io.rami.screenrecorder.domain.model.AppSettings
import io.rami.screenrecorder.domain.model.CaptureModeKind
import io.rami.screenrecorder.domain.model.Recording
import io.rami.screenrecorder.domain.model.RecordingConfig
import io.rami.screenrecorder.domain.model.RecordingId
import io.rami.screenrecorder.domain.model.RecordingSessionEvent
import io.rami.screenrecorder.domain.model.RecordingState
import io.rami.screenrecorder.domain.model.Resolution
import io.rami.screenrecorder.domain.model.TrashItem
import io.rami.screenrecorder.domain.model.VideoCodec
import io.rami.screenrecorder.domain.repository.CompletedRecordingAnnouncer
import io.rami.screenrecorder.domain.repository.MediaLibraryRepository
import io.rami.screenrecorder.domain.repository.RecordingSessionRepository
import io.rami.screenrecorder.domain.repository.SettingsRepository
import io.rami.screenrecorder.domain.repository.StorageRepository
import io.rami.screenrecorder.domain.usecase.GetRecordingsUseCase
import io.rami.screenrecorder.domain.usecase.ObserveRecordingStateUseCase
import io.rami.screenrecorder.domain.usecase.ObserveSettingsUseCase
import io.rami.screenrecorder.domain.usecase.SkipCountdownUseCase
import io.rami.screenrecorder.domain.usecase.UpdateSettingsUseCase
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.minutes

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {
    private val settingsFlow = MutableStateFlow(AppSettings.DEFAULT)
    private val stateFlow = MutableStateFlow<RecordingState>(RecordingState.Idle)
    private val availableBytes = MutableStateFlow(10_000_000_000L)
    private val recordingsFlow = MutableStateFlow<List<Recording>>(emptyList())

    private val settingsRepository =
        object : SettingsRepository {
            override val settings: Flow<AppSettings> = settingsFlow

            override suspend fun update(transform: (AppSettings) -> AppSettings) {
                settingsFlow.value = transform(settingsFlow.value)
            }
        }

    private class FakeSessionRepository(
        stateFlow: MutableStateFlow<RecordingState>,
    ) : RecordingSessionRepository {
        var skipCount = 0

        /** 발행이 확정된 녹화본만 흐르는 이벤트. 저장 완료 표시가 이 축으로만 켜져야 한다. */
        val completed = MutableSharedFlow<Recording>(extraBufferCapacity = 1)

        override val state: Flow<RecordingState> = stateFlow
        override val completedRecordings: Flow<Recording> = completed
        override val sessionEvents: Flow<RecordingSessionEvent> = emptyFlow()

        override suspend fun start(config: RecordingConfig) = Unit

        override fun skipCountdown() {
            skipCount++
        }

        override suspend fun stop() = Unit

        override suspend fun pause() = Unit

        override suspend fun resume() = Unit
    }

    /** 홈이 보여 줄 때까지 남는 완료 공지 (기능명세서 2.1절 [결정]). */
    private class FakeCompletionAnnouncer : CompletedRecordingAnnouncer {
        val pending = MutableStateFlow<Recording?>(null)
        var consumeCount = 0

        override val pendingCompletedRecording: StateFlow<Recording?> = pending

        override fun consumeCompletedRecording() {
            consumeCount++
            pending.value = null
        }
    }

    private val sessionRepository = FakeSessionRepository(stateFlow)
    private val completionAnnouncer = FakeCompletionAnnouncer()

    private val libraryRepository =
        object : MediaLibraryRepository {
            override fun observeRecordings(): Flow<List<Recording>> = recordingsFlow

            override suspend fun rename(
                id: RecordingId,
                newName: String,
            ) = Unit

            override suspend fun moveToTrash(ids: List<RecordingId>) = Unit

            override fun observeTrash(): Flow<List<TrashItem>> = emptyFlow()

            override suspend fun restore(ids: List<RecordingId>) = Unit

            override suspend fun permanentlyDelete(ids: List<RecordingId>) = Unit
        }

    private val recoveryRepository =
        object : io.rami.screenrecorder.domain.repository.RecordingRecoveryRepository {
            var pending =
                listOf(
                    io.rami.screenrecorder.domain.model
                        .PendingRecovery("t.mp4", "t.mp4", 10),
                )
            val discarded = mutableListOf<String>()
            val recovered = mutableListOf<String>()

            /** true면 복구가 MediaStore 실패를 흉내내 예외를 던진다. */
            var recoverThrows = false

            /**
             * 설정하면 복구가 여기서 멈춰 선다.
             *
             * 실제 복구는 1시간짜리 파일을 remux 하느라 수 초가 걸린다. 그 사이 사용자가
             * 버튼을 다시 누르는 상황을 재현하려면 "아직 안 끝난 복구"가 있어야 한다.
             */
            var recoverGate: CompletableDeferred<Unit>? = null

            /** true면 삭제가 파일 시스템 실패를 흉내내 예외를 던진다. */
            var discardThrows = false

            override suspend fun pendingRecoveries(): List<io.rami.screenrecorder.domain.model.PendingRecovery> {
                calls += "list"
                return pending
            }

            override suspend fun recover(id: String): Recording? {
                check(!recoverThrows) { "MediaStore insert 실패" }
                recoverGate?.await()
                recovered += id
                return null
            }

            override suspend fun discard(id: String) {
                check(!discardThrows) { "임시 파일 삭제 실패" }
                discarded += id
            }

            /** 정리와 조회의 순서를 검증할 수 있도록 호출 순서를 남긴다. */
            val calls = mutableListOf<String>()

            /** true면 정리가 MediaStore 실패를 흉내내 예외를 던진다. */
            var cleanUpThrows = false

            override suspend fun cleanUpAbandonedPublishes() {
                calls += "cleanUp"
                check(!cleanUpThrows) { "MediaStore 조회 실패" }
            }
        }

    private fun viewModel(): HomeViewModel =
        HomeViewModel(
            useCases =
                HomeUseCases(
                    observeSettings = ObserveSettingsUseCase(settingsRepository),
                    updateSettings = UpdateSettingsUseCase(settingsRepository),
                    observeRecordingState = ObserveRecordingStateUseCase(sessionRepository),
                    skipCountdown = SkipCountdownUseCase(sessionRepository),
                    getRecordings = GetRecordingsUseCase(libraryRepository),
                    observeCompletedRecording =
                        io.rami.screenrecorder.domain.usecase
                            .ObserveCompletedRecordingUseCase(sessionRepository),
                    observePendingCompletedRecording =
                        io.rami.screenrecorder.domain.usecase
                            .ObservePendingCompletedRecordingUseCase(completionAnnouncer),
                    consumeCompletedRecording =
                        io.rami.screenrecorder.domain.usecase
                            .ConsumeCompletedRecordingUseCase(completionAnnouncer),
                    renameRecording =
                        io.rami.screenrecorder.domain.usecase
                            .RenameRecordingUseCase(libraryRepository),
                    getPendingRecoveries =
                        io.rami.screenrecorder.domain.usecase
                            .GetPendingRecoveriesUseCase(recoveryRepository),
                    recoverRecording =
                        io.rami.screenrecorder.domain.usecase
                            .RecoverRecordingUseCase(recoveryRepository),
                    discardRecovery =
                        io.rami.screenrecorder.domain.usecase
                            .DiscardRecoveryUseCase(recoveryRepository),
                    cleanUpAbandonedPublishes =
                        io.rami.screenrecorder.domain.usecase
                            .CleanUpAbandonedPublishesUseCase(recoveryRepository),
                ),
            storageRepository = StorageRepository { availableBytes },
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
    fun `설정과 저장 공간을 결합해 홈 상태를 만든다`() =
        runTest {
            viewModel().uiState.test {
                skipItems(1) // 초기 로딩 상태

                val state = awaitItem()
                assertEquals(CaptureModeKind.FULL_SCREEN, state.selectedMode)
                assertEquals(AppSettings.DEFAULT.recording, state.preset)
                assertEquals(10_000_000_000L, state.availableBytes)
                assertTrue(state.canStartRecording)
                assertTrue(state.estimatedRecordableTime > 30.minutes)
            }
        }

    @Test
    fun `초기화 시 미발행 임시 파일을 복구 대기 목록으로 노출한다`() =
        runTest {
            val viewModel = viewModel()
            viewModel.pendingRecoveries.test {
                assertEquals(emptyList<Any>(), awaitItem()) // 초기값
                assertEquals(listOf("t.mp4"), awaitItem().map { it.id })
            }
        }

    @Test
    fun `녹화 중에는 임시 파일을 고아로 오인하지 않고 중지 후 조회한다`() =
        runTest {
            // 서비스가 녹화 중인 채로 ViewModel이 재생성된 상황 (활성 temp 파일 = 고아 아님).
            stateFlow.value = RecordingState.Recording(elapsed = 1.minutes)
            val viewModel = viewModel()
            viewModel.pendingRecoveries.test {
                assertEquals(emptyList<Any>(), awaitItem()) // 녹화 중에는 빈 목록 유지
                expectNoEvents()

                // 녹화가 끝나 finalize가 활성 temp를 정리한 뒤 Idle이 되면 그때 조회한다.
                stateFlow.value = RecordingState.Idle
                assertEquals(listOf("t.mp4"), awaitItem().map { it.id })
            }
        }

    @Test
    fun `복구를 확정하면 저장소에 위임하고 목록에서 제거한다`() =
        runTest {
            val viewModel = viewModel()
            viewModel.pendingRecoveries.test {
                skipItems(1)
                assertEquals(listOf("t.mp4"), awaitItem().map { it.id })

                viewModel.onRecoverConfirmed("t.mp4")

                assertEquals(emptyList<Any>(), awaitItem())
                assertEquals(listOf("t.mp4"), recoveryRepository.recovered)
            }
        }

    @Test
    fun `복구를 거부하면 임시 파일을 삭제하고 목록에서 제거한다`() =
        runTest {
            val viewModel = viewModel()
            viewModel.pendingRecoveries.test {
                skipItems(1)
                assertEquals(listOf("t.mp4"), awaitItem().map { it.id })

                viewModel.onDiscardRecovery("t.mp4")

                assertEquals(emptyList<Any>(), awaitItem())
                assertEquals(listOf("t.mp4"), recoveryRepository.discarded)
            }
        }

    @Test
    fun `복구 실패 시 크래시 대신 실패 이벤트를 낸다`() =
        runTest {
            recoveryRepository.recoverThrows = true
            val viewModel = viewModel()
            advanceUntilIdle() // init의 복구 목록 로드 완료

            viewModel.recoveryFailed.test {
                viewModel.onRecoverConfirmed("t.mp4")

                awaitItem() // 실패 이벤트 발생 (예외로 크래시하지 않음)
            }
        }

    /**
     * 실패한 복구는 그 실행에서 제안을 내린다 (기능명세서 6.1절 [결정]).
     *
     * 임시 파일은 남으므로 다음 실행에서 다시 제안된다. 같은 이유로 계속 실패하는 파일을
     * 계속 띄우면, 닫을 수 없는 다이얼로그 때문에 사용자가 "삭제" 말고는 앱을 쓸 수 없다.
     */
    @Test
    fun `복구가 실패하면 그 실행에서는 다시 제안하지 않는다`() =
        runTest {
            recoveryRepository.recoverThrows = true
            val viewModel = viewModel()
            advanceUntilIdle()

            viewModel.onRecoverConfirmed("t.mp4")
            advanceUntilIdle()

            assertEquals(emptyList<String>(), viewModel.pendingRecoveries.value.map { it.id })
        }

    @Test
    fun `저장 공간이 500MB 미만이면 시작할 수 없다`() =
        runTest {
            availableBytes.value = 400_000_000L
            viewModel().uiState.test {
                skipItems(1)
                assertFalse(awaitItem().canStartRecording)
            }
        }

    @Test
    fun `모드를 선택하면 설정에 저장된다 (마지막 선택 유지)`() =
        runTest {
            val viewModel = viewModel()
            viewModel.uiState.test {
                skipItems(2)

                viewModel.onModeSelected(CaptureModeKind.REGION)

                assertEquals(CaptureModeKind.REGION, awaitItem().selectedMode)
                assertEquals(CaptureModeKind.REGION, settingsFlow.value.selectedCaptureMode)
            }
        }

    @Test
    fun `카운트다운 화면 탭은 스킵을 요청한다`() =
        runTest {
            viewModel().onCountdownTapped()

            assertEquals(1, sessionRepository.skipCount)
        }

    @Test
    fun `최근 녹화는 최대 3개만 노출한다`() =
        runTest {
            recordingsFlow.value = (1L..5L).map { recording(it) }
            viewModel().uiState.test {
                skipItems(1)
                assertEquals(listOf(5L, 4L, 3L), awaitItem().recentRecordings.map { it.id.value })
            }
        }

    private fun recording(id: Long) =
        Recording(
            id = RecordingId(id),
            displayName = "Rec_$id.mp4",
            contentUri = "content://media/$id",
            sizeBytes = 100,
            duration = 1.minutes,
            resolution = Resolution.FHD,
            frameRate = 60,
            codec = VideoCodec.H264,
            createdAtEpochMillis = id,
            bitrateBps = null,
        )

    /**
     * 같은 임시 파일을 두 번 발행하면 MediaStore 에 똑같은 녹화본이 두 개 쌓인다
     * (기능명세서 6.1절 [결정]). 실제로 433MB 사본 10개가 만들어진 적이 있다.
     */
    @Test
    fun `복구가 진행 중이면 다시 눌러도 한 번만 실행한다`() =
        runTest {
            val gate = CompletableDeferred<Unit>()
            recoveryRepository.recoverGate = gate
            val viewModel = viewModel()
            advanceUntilIdle()

            repeat(TAP_BURST) { viewModel.onRecoverConfirmed("t.mp4") }
            advanceUntilIdle()
            gate.complete(Unit)
            advanceUntilIdle()

            assertEquals(listOf("t.mp4"), recoveryRepository.recovered)
        }

    @Test
    fun `복구가 진행 중이면 삭제도 실행되지 않는다`() =
        runTest {
            val gate = CompletableDeferred<Unit>()
            recoveryRepository.recoverGate = gate
            val viewModel = viewModel()
            advanceUntilIdle()

            viewModel.onRecoverConfirmed("t.mp4")
            advanceUntilIdle()
            viewModel.onDiscardRecovery("t.mp4")
            advanceUntilIdle()
            gate.complete(Unit)
            advanceUntilIdle()

            assertEquals(emptyList<String>(), recoveryRepository.discarded)
        }

    @Test
    fun `복구하는 동안 진행 중임을 알리고 끝나면 내린다`() =
        runTest {
            val gate = CompletableDeferred<Unit>()
            recoveryRepository.recoverGate = gate
            val viewModel = viewModel()
            advanceUntilIdle()

            assertEquals(null, viewModel.recoveringId.value)

            viewModel.onRecoverConfirmed("t.mp4")
            advanceUntilIdle()
            assertEquals("t.mp4", viewModel.recoveringId.value)

            gate.complete(Unit)
            advanceUntilIdle()
            assertEquals(null, viewModel.recoveringId.value)
        }

    @Test
    fun `복구가 실패해도 진행 표시를 내려 다시 시도할 수 있게 한다`() =
        runTest {
            recoveryRepository.recoverThrows = true
            val viewModel = viewModel()
            advanceUntilIdle()

            viewModel.onRecoverConfirmed("t.mp4")
            advanceUntilIdle()

            assertEquals(null, viewModel.recoveringId.value)
        }

    /**
     * 삭제 실패는 복구 실패와 다르게 다룬다 (기능명세서 6.1절 [결정]).
     *
     * 목록에서 내리면 사용자는 지웠다고 믿지만, 그 파일은 다음 진입에서 다시 나타난다.
     */
    @Test
    fun `삭제가 실패하면 목록에 남긴다`() =
        runTest {
            recoveryRepository.discardThrows = true
            val viewModel = viewModel()
            advanceUntilIdle()

            viewModel.onDiscardRecovery("t.mp4")
            advanceUntilIdle()

            assertEquals(listOf("t.mp4"), viewModel.pendingRecoveries.value.map { it.id })
        }

    /**
     * 정리가 조회보다 먼저다 (기능명세서 6.1절 [결정]).
     *
     * 순서가 반대면 복구 재발행이 고아 레코드와 파일명이 충돌해 "(1)" 접미어가 붙는다.
     */
    @Test
    fun `버려진 발행을 정리한 뒤에 복구 목록을 읽는다`() =
        runTest {
            viewModel()
            advanceUntilIdle()

            assertEquals(listOf("cleanUp", "list"), recoveryRepository.calls)
        }

    /**
     * 정리는 조기 회수일 뿐이므로 실패해도 앱 동작을 막지 않는다 (기능명세서 6.1절 [결정]).
     *
     * 감싸지 않으면 복구 목록 조회를 건너뛰는 데 그치지 않고 viewModelScope 가 크래시한다.
     */
    @Test
    fun `정리가 실패해도 복구 목록은 띄운다`() =
        runTest {
            recoveryRepository.cleanUpThrows = true
            val viewModel = viewModel()
            advanceUntilIdle()

            assertEquals(listOf("t.mp4"), viewModel.pendingRecoveries.value.map { it.id })
        }

    /**
     * 저장 완료 표시 (기능명세서 2.1절 [결정]).
     *
     * 발행이 확정될 때만 켜지고, 홈이 실제로 보여 준 뒤에 소모한다. 시간만으로 꺼 버리면
     * 버블로 녹화를 시작해 다른 앱에 있던 사용자는 표시를 영영 보지 못한다.
     */
    @Test
    fun `발행이 확정되면 저장 완료 표시를 켠다`() =
        runTest {
            val viewModel = viewModel()
            advanceUntilIdle()

            completionAnnouncer.pending.value = SAVED
            runCurrent()

            assertEquals(SAVED, viewModel.justSaved.value)
        }

    @Test
    fun `보여 주기 전에는 시간이 지나도 완료 표시가 꺼지지 않는다`() =
        runTest {
            val viewModel = viewModel()
            advanceUntilIdle()
            completionAnnouncer.pending.value = SAVED
            runCurrent()
            // 켜진 것을 먼저 확인해야 "안 꺼졌다"와 "애초에 켜지지 않았다"가 구분된다.
            assertEquals(SAVED, viewModel.justSaved.value)

            advanceUntilIdle()

            assertEquals(SAVED, viewModel.justSaved.value)
        }

    @Test
    fun `홈이 보여 줬다고 알리면 완료 표시를 소모한다`() =
        runTest {
            val viewModel = viewModel()
            advanceUntilIdle()
            completionAnnouncer.pending.value = SAVED
            runCurrent()

            viewModel.onSavedDisplayed()
            advanceUntilIdle()

            assertEquals(1, completionAnnouncer.consumeCount)
            assertNull(viewModel.justSaved.value)
        }

    /**
     * 중지 처리는 발행 실패와 빈 세션(프레임 0개)에서도 똑같이 끝난다. 상태 전이로 판정하면
     * 저장되지 않은 녹화를 "저장했습니다" 로 알린다.
     */
    @Test
    fun `저장되지 않고 중지만 끝나면 완료 표시를 켜지 않는다`() =
        runTest {
            val viewModel = viewModel()
            advanceUntilIdle()

            stateFlow.value = RecordingState.Stopping(1.minutes, "Rec.mp4", progress = 0.4f)
            runCurrent()
            stateFlow.value = RecordingState.Idle
            advanceUntilIdle()

            assertNull(viewModel.justSaved.value)
        }

    private companion object {
        /** 응답이 없다고 느낀 사용자가 연타하는 횟수. */
        const val TAP_BURST = 5

        val SAVED =
            Recording(
                id = RecordingId(7L),
                displayName = "ScreenRecorder_20260831_143020.mp4",
                contentUri = "content://media/external/video/media/7",
                sizeBytes = 12_345L,
                duration = 3.minutes,
                resolution = Resolution.FHD,
                frameRate = 60,
                codec = VideoCodec.H264,
                createdAtEpochMillis = 1_788_155_923_000L,
                bitrateBps = 12_000_000,
            )
    }
}
