package io.rami.screenrecorder.domain.usecase

import io.rami.screenrecorder.domain.model.CompressionPreset
import io.rami.screenrecorder.domain.model.Recording
import io.rami.screenrecorder.domain.model.RecordingConfig
import io.rami.screenrecorder.domain.model.RecordingId
import io.rami.screenrecorder.domain.model.RecordingSessionEvent
import io.rami.screenrecorder.domain.model.RecordingState
import io.rami.screenrecorder.domain.model.TranscodeJob
import io.rami.screenrecorder.domain.model.TranscodeStatus
import io.rami.screenrecorder.domain.repository.RecordingSessionRepository
import io.rami.screenrecorder.domain.repository.TranscodeRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.minutes

class CompressRecordingUseCaseTest {
    private val sessionState = MutableStateFlow<RecordingState>(RecordingState.Idle)

    private class FakeSessionRepository(
        override val state: MutableStateFlow<RecordingState>,
    ) : RecordingSessionRepository {
        override val completedRecordings: Flow<Recording> = emptyFlow()
        override val pendingCompletedRecording: Flow<Recording?> = MutableStateFlow(null)
        override val sessionEvents: Flow<RecordingSessionEvent> = emptyFlow()

        override fun consumeCompletedRecording() = Unit

        override suspend fun start(config: RecordingConfig) = Unit

        override fun skipCountdown() = Unit

        override suspend fun stop() = Unit

        override suspend fun pause() = Unit

        override suspend fun resume() = Unit
    }

    private class FakeTranscodeRepository : TranscodeRepository {
        var enqueued: Pair<RecordingId, CompressionPreset>? = null
        var cancelled = false

        override fun observeJob(): Flow<TranscodeJob?> = MutableStateFlow(null)

        override suspend fun enqueue(
            recordingId: RecordingId,
            preset: CompressionPreset,
        ) {
            enqueued = recordingId to preset
        }

        override suspend fun cancel() {
            cancelled = true
        }

        override suspend fun clearCompleted() = Unit
    }

    private val transcodeRepository = FakeTranscodeRepository()

    private fun useCase() =
        CompressRecordingUseCase(
            sessionRepository = FakeSessionRepository(sessionState),
            transcodeRepository = transcodeRepository,
        )

    @Test
    fun `대기 상태에서는 압축 작업을 등록한다`() =
        runTest {
            val result = useCase()(RecordingId(1), CompressionPreset.STANDARD)

            assertTrue(result.isSuccess)
            assertEquals(RecordingId(1) to CompressionPreset.STANDARD, transcodeRepository.enqueued)
        }

    @Test
    fun `녹화 중에는 압축을 거부한다`() =
        runTest {
            sessionState.value = RecordingState.Recording(elapsed = 1.minutes)

            val result = useCase()(RecordingId(1), CompressionPreset.MAXIMUM)

            assertTrue(result.exceptionOrNull() is CompressionBlockedException)
            assertNull(transcodeRepository.enqueued)
        }

    @Test
    fun `일시정지 중에도 압축을 거부한다 - 세션이 인코더를 점유 중이다`() =
        runTest {
            sessionState.value = RecordingState.Paused(elapsed = 1.minutes)

            val result = useCase()(RecordingId(1), CompressionPreset.HIGH_EFFICIENCY)

            assertTrue(result.exceptionOrNull() is CompressionBlockedException)
        }

    @Test
    fun `트랜스코드 작업 상태 모델은 진행률을 담는다`() {
        val job =
            TranscodeJob(
                recordingId = RecordingId(1),
                preset = CompressionPreset.STANDARD,
                progressPercent = 42,
                status = TranscodeStatus.RUNNING,
            )

        assertEquals(42, job.progressPercent)
        assertEquals(TranscodeStatus.RUNNING, job.status)
    }
}
