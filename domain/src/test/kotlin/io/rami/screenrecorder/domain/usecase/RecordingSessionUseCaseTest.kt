package io.rami.screenrecorder.domain.usecase

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.rami.screenrecorder.domain.model.RecordingConfig
import io.rami.screenrecorder.domain.model.RecordingState
import io.rami.screenrecorder.domain.repository.RecordingSessionRepository
import io.rami.screenrecorder.domain.repository.StorageRepository
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.seconds

class RecordingSessionUseCaseTest {
    private val sessionRepository = mockk<RecordingSessionRepository>(relaxed = true)
    private val storageRepository = mockk<StorageRepository>()

    private fun givenState(state: RecordingState) {
        every { sessionRepository.state } returns flowOf(state)
    }

    private fun givenAvailableBytes(bytes: Long) {
        every { storageRepository.observeAvailableBytes() } returns flowOf(bytes)
    }

    // --- 시작 ---

    @Test
    fun `유휴 상태이고 공간이 충분하면 녹화를 시작한다`() =
        runTest {
            givenState(RecordingState.Idle)
            givenAvailableBytes(1_000_000_000)
            val useCase = StartRecordingUseCase(sessionRepository, storageRepository)

            val result = useCase(RecordingConfig.DEFAULT)

            assertTrue(result.isSuccess)
            coVerify(exactly = 1) { sessionRepository.start(RecordingConfig.DEFAULT) }
        }

    @Test
    fun `저장 공간이 500MB 미만이면 시작을 거부한다`() =
        runTest {
            givenState(RecordingState.Idle)
            givenAvailableBytes(499_000_000)
            val useCase = StartRecordingUseCase(sessionRepository, storageRepository)

            val result = useCase(RecordingConfig.DEFAULT)

            assertTrue(result.exceptionOrNull() is RecordingSessionException.InsufficientStorage)
            coVerify(exactly = 0) { sessionRepository.start(any()) }
        }

    @Test
    fun `이미 녹화 중이면 시작을 거부한다`() =
        runTest {
            givenState(RecordingState.Recording(elapsed = 3.seconds))
            givenAvailableBytes(1_000_000_000)
            val useCase = StartRecordingUseCase(sessionRepository, storageRepository)

            val result = useCase(RecordingConfig.DEFAULT)

            assertTrue(result.exceptionOrNull() is RecordingSessionException.InvalidState)
            coVerify(exactly = 0) { sessionRepository.start(any()) }
        }

    // --- 일시정지 / 재개 (기능명세서 11.2, 11.3절) ---

    @Test
    fun `녹화 중에만 일시정지할 수 있다`() =
        runTest {
            givenState(RecordingState.Recording(elapsed = 3.seconds))
            val result = PauseRecordingUseCase(sessionRepository)()

            assertTrue(result.isSuccess)
            coVerify(exactly = 1) { sessionRepository.pause() }
        }

    @Test
    fun `녹화 중이 아니면 일시정지를 거부한다`() =
        runTest {
            givenState(RecordingState.Idle)
            val result = PauseRecordingUseCase(sessionRepository)()

            assertTrue(result.exceptionOrNull() is RecordingSessionException.InvalidState)
            coVerify(exactly = 0) { sessionRepository.pause() }
        }

    @Test
    fun `일시정지 상태에서만 재개할 수 있다`() =
        runTest {
            givenState(RecordingState.Paused(elapsed = 3.seconds))
            val result = ResumeRecordingUseCase(sessionRepository)()

            assertTrue(result.isSuccess)
            coVerify(exactly = 1) { sessionRepository.resume() }
        }

    @Test
    fun `일시정지 상태가 아니면 재개를 거부한다`() =
        runTest {
            givenState(RecordingState.Recording(elapsed = 3.seconds))
            val result = ResumeRecordingUseCase(sessionRepository)()

            assertTrue(result.exceptionOrNull() is RecordingSessionException.InvalidState)
            coVerify(exactly = 0) { sessionRepository.resume() }
        }

    // --- 중지 ---

    @Test
    fun `녹화 중이거나 일시정지 상태면 중지할 수 있다`() =
        runTest {
            givenState(RecordingState.Recording(elapsed = 3.seconds))
            assertTrue(StopRecordingUseCase(sessionRepository)().isSuccess)

            givenState(RecordingState.Paused(elapsed = 3.seconds))
            assertTrue(StopRecordingUseCase(sessionRepository)().isSuccess)

            coVerify(exactly = 2) { sessionRepository.stop() }
        }

    @Test
    fun `유휴 상태에서 중지를 거부한다`() =
        runTest {
            givenState(RecordingState.Idle)
            val result = StopRecordingUseCase(sessionRepository)()

            assertTrue(result.exceptionOrNull() is RecordingSessionException.InvalidState)
            coVerify(exactly = 0) { sessionRepository.stop() }
        }

    @Test
    fun `카운트다운 중에도 중지할 수 있다`() =
        runTest {
            givenState(RecordingState.CountingDown(remainingSeconds = 2))
            val result = StopRecordingUseCase(sessionRepository)()

            assertTrue(result.isSuccess)
            coVerify(exactly = 1) { sessionRepository.stop() }
        }

    @Test
    fun `세션 시작 실패는 Result 실패로 전달된다`() =
        runTest {
            givenState(RecordingState.Idle)
            givenAvailableBytes(1_000_000_000)
            coEvery { sessionRepository.start(any()) } throws IllegalStateException("인코더 초기화 실패")
            val useCase = StartRecordingUseCase(sessionRepository, storageRepository)

            val result = useCase(RecordingConfig.DEFAULT)

            assertTrue(result.isFailure)
        }
}
