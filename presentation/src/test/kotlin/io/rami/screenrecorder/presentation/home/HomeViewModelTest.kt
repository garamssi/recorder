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
import io.rami.screenrecorder.domain.repository.MediaLibraryRepository
import io.rami.screenrecorder.domain.repository.RecordingSessionRepository
import io.rami.screenrecorder.domain.repository.SettingsRepository
import io.rami.screenrecorder.domain.repository.StorageRepository
import io.rami.screenrecorder.domain.usecase.GetRecordingsUseCase
import io.rami.screenrecorder.domain.usecase.ObserveRecordingStateUseCase
import io.rami.screenrecorder.domain.usecase.ObserveSettingsUseCase
import io.rami.screenrecorder.domain.usecase.SkipCountdownUseCase
import io.rami.screenrecorder.domain.usecase.UpdateSettingsUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
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

        override val state: Flow<RecordingState> = stateFlow
        override val completedRecordings: Flow<Recording> = emptyFlow()
        override val sessionEvents: Flow<RecordingSessionEvent> = emptyFlow()

        override suspend fun start(config: RecordingConfig) = Unit

        override fun skipCountdown() {
            skipCount++
        }

        override suspend fun stop() = Unit

        override suspend fun pause() = Unit

        override suspend fun resume() = Unit
    }

    private val sessionRepository = FakeSessionRepository(stateFlow)

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

            override suspend fun pendingRecoveries() = pending

            override suspend fun recover(id: String): Recording? {
                recovered += id
                return null
            }

            override suspend fun discard(id: String) {
                discarded += id
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
}
