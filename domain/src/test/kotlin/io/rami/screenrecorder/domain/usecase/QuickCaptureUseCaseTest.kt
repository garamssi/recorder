package io.rami.screenrecorder.domain.usecase

import io.rami.screenrecorder.domain.model.CapturedImage
import io.rami.screenrecorder.domain.model.VoiceMemo
import io.rami.screenrecorder.domain.model.VoiceRecordingState
import io.rami.screenrecorder.domain.repository.ScreenshotRepository
import io.rami.screenrecorder.domain.repository.VoiceRecordingRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.seconds

/** 화면 캡처(기능명세서 12절)와 음성 전용 녹음(13절) 유스케이스. */
class QuickCaptureUseCaseTest {
    private val capturedImage =
        CapturedImage(
            displayName = "Rec_20260820_101500.png",
            contentUri = "content://media/external/images/media/42",
            sizeBytes = 1_200_000,
            widthPx = 2560,
            heightPx = 1600,
            createdAtEpochMillis = 1_755_000_000_000,
        )

    private val voiceMemo =
        VoiceMemo(
            displayName = "Rec_20260820_101500.m4a",
            contentUri = "content://media/external/audio/media/7",
            sizeBytes = 250_000,
            duration = 30.seconds,
            createdAtEpochMillis = 1_755_000_000_000,
        )

    @Test
    fun `화면 캡처는 저장된 이미지를 반환한다`() =
        runTest {
            val repository = FakeScreenshotRepository(Result.success(capturedImage))

            val result = CaptureScreenshotUseCase(repository)()

            assertEquals(capturedImage, result.getOrNull())
        }

    @Test
    fun `화면 캡처 실패는 그대로 전파한다`() =
        runTest {
            val failure = IllegalStateException("동의 토큰 없음")
            val repository = FakeScreenshotRepository(Result.failure(failure))

            val result = CaptureScreenshotUseCase(repository)()

            assertEquals(failure, result.exceptionOrNull())
        }

    @Test
    fun `음성 녹음은 유휴 상태에서만 시작한다`() =
        runTest {
            val repository = FakeVoiceRecordingRepository()

            val started = StartVoiceRecordingUseCase(repository)()

            assertTrue(started.isSuccess)
            assertEquals(1, repository.startCount)
        }

    @Test
    fun `이미 녹음 중이면 시작을 거부한다`() =
        runTest {
            val repository = FakeVoiceRecordingRepository()
            repository.state.value = VoiceRecordingState.Recording(elapsed = 5.seconds)

            val started = StartVoiceRecordingUseCase(repository)()

            assertTrue(started.exceptionOrNull() is VoiceRecordingException.AlreadyRecording)
            assertEquals(0, repository.startCount)
        }

    @Test
    fun `음성 녹음 중지는 저장된 파일을 반환한다`() =
        runTest {
            val repository = FakeVoiceRecordingRepository(stopResult = voiceMemo)
            repository.state.value = VoiceRecordingState.Recording(elapsed = 30.seconds)

            val stopped = StopVoiceRecordingUseCase(repository)()

            assertEquals(voiceMemo, stopped.getOrNull())
        }

    @Test
    fun `녹음 중이 아니면 중지는 아무것도 반환하지 않는다`() =
        runTest {
            val repository = FakeVoiceRecordingRepository(stopResult = voiceMemo)

            val stopped = StopVoiceRecordingUseCase(repository)()

            assertNull(stopped.getOrNull())
            assertEquals(0, repository.stopCount)
        }

    @Test
    fun `상태 관찰 유스케이스는 저장소 스트림을 그대로 노출한다`() =
        runTest {
            val repository = FakeVoiceRecordingRepository()
            repository.state.value = VoiceRecordingState.Recording(elapsed = 3.seconds)

            val state = ObserveVoiceRecordingStateUseCase(repository)()

            assertEquals(VoiceRecordingState.Recording(3.seconds), state.first())
        }
}

private class FakeScreenshotRepository(
    private val result: Result<CapturedImage>,
) : ScreenshotRepository {
    override suspend fun capture(): Result<CapturedImage> = result
}

private class FakeVoiceRecordingRepository(
    private val stopResult: VoiceMemo? = null,
) : VoiceRecordingRepository {
    val state = MutableStateFlow<VoiceRecordingState>(VoiceRecordingState.Idle)
    var startCount = 0
    var stopCount = 0

    override fun observeState(): Flow<VoiceRecordingState> = state

    override suspend fun start() {
        startCount++
    }

    override suspend fun stop(): VoiceMemo? {
        stopCount++
        return stopResult
    }
}
