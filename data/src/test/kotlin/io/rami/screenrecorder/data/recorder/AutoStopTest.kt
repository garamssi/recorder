package io.rami.screenrecorder.data.recorder

import app.cash.turbine.test
import io.mockk.mockk
import io.rami.screenrecorder.domain.model.AudioSource
import io.rami.screenrecorder.domain.model.AutoStopReason
import io.rami.screenrecorder.domain.model.CountdownDuration
import io.rami.screenrecorder.domain.model.RecordingConfig
import io.rami.screenrecorder.domain.model.RecordingSessionEvent
import io.rami.screenrecorder.domain.model.RecordingState
import io.rami.screenrecorder.domain.model.Resolution
import io.rami.screenrecorder.domain.model.TimeLimit
import io.rami.screenrecorder.domain.session.MonotonicClock
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * 자동 안전 중지 동작 검증 (기능명세서 11절):
 * 타이머 도달, 저장 공간 부족, 일시정지 30분 초과.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AutoStopTest {
    private class NoopEncoder : VideoEncoder {
        var listener: VideoEncoder.Listener? = null

        override fun prepare(
            config: VideoEncoderConfig,
            listener: VideoEncoder.Listener,
        ) = mockk<android.view.Surface>().also { this.listener = listener }

        override fun start() = Unit

        override fun setSuspended(suspended: Boolean) = Unit

        override fun requestKeyFrame() = Unit

        override fun stopAndRelease() = Unit
    }

    private class NoopCapture : ScreenCaptureSource {
        override fun start(
            encoderSurface: android.view.Surface,
            resolution: Resolution,
            listener: ScreenCaptureSource.Listener,
        ) = Unit

        override fun stop() = Unit
    }

    private class CountingMuxer : MuxerWriter {
        var closeCount = 0

        override fun open(outputFile: File) = Unit

        override fun addVideoTrack(format: android.media.MediaFormat): Int = 0

        override fun addAudioTrack(format: android.media.MediaFormat): Int = 1

        override fun writeSample(
            trackId: Int,
            sample: EncodedSample,
        ) = Unit

        override fun close() {
            closeCount++
        }
    }

    private class NoopFileStore : RecordingFileStore {
        override fun createTempFile(fileName: String) = File("build/tmp/fake/$fileName")

        override fun listTempFiles(): List<java.io.File> = emptyList()

        override suspend fun existingFileNames(): Set<String> = emptySet()

        override suspend fun discardAbandonedPublishes(): Int = 0

        override suspend fun publish(
            tempFile: File,
            fileName: String,
            onProgress: (Float) -> Unit,
        ) = io.rami.screenrecorder.domain.model
            .Recording(
                id =
                    io.rami.screenrecorder.domain.model
                        .RecordingId(1),
                displayName = fileName,
                contentUri = "content://media/1",
                sizeBytes = 1,
                duration = kotlin.time.Duration.ZERO,
                resolution = Resolution.FHD,
                frameRate = 60,
                codec = io.rami.screenrecorder.domain.model.VideoCodec.H264,
                createdAtEpochMillis = 0,
                bitrateBps = null,
            )
    }

    private val muxer = CountingMuxer()
    private val availableBytes = MutableStateFlow(10_000_000_000L)

    private fun TestScope.coordinator(): RecordingCoordinator {
        val factory =
            object : RecorderSessionFactory {
                override fun createVideoEncoder(): VideoEncoder = NoopEncoder()

                override fun createCaptureSource(): ScreenCaptureSource = NoopCapture()

                override fun createMuxer(): MuxerWriter = muxer

                override suspend fun createAudioRecorder(config: RecordingConfig): AudioSetup? = null

                override fun createFrameProcessor(): FrameProcessor = error("사용하지 않는다")
            }
        return RecordingCoordinator(
            sessionFactory = factory,
            dependencies =
                RecorderDependencies(
                    fileStore = NoopFileStore(),
                    fileNameProvider = { "Rec_test.mp4" },
                    displayInfo = { Resolution.FHD },
                    clock = MonotonicClock { testScheduler.currentTime },
                    storageRepository = { availableBytes },
                ),
            scope = backgroundScope,
            blockingDispatcher = StandardTestDispatcher(testScheduler),
        )
    }

    private fun config(timeLimit: TimeLimit = TimeLimit.None) =
        RecordingConfig.DEFAULT.copy(
            countdown = CountdownDuration.NONE,
            audioSource = AudioSource.SILENT,
            timeLimit = timeLimit,
        )

    @Test
    fun `시간 제한에 도달하면 자동으로 안전 중지한다`() =
        runTest {
            val coordinator = coordinator()
            coordinator.sessionEvents.test {
                coordinator.start(config(TimeLimit.Limited(30.seconds)))

                testScheduler.advanceTimeBy(31_000)
                runCurrent()

                // 10초 전 예고 후 자동 중지 (30초 제한은 1분 미만이라 1분 전 예고 없음)
                assertEquals(
                    RecordingSessionEvent.TimeLimitWarning(10.seconds),
                    awaitItem(),
                )
                assertEquals(
                    RecordingSessionEvent.AutoStopped(AutoStopReason.TIME_LIMIT_REACHED),
                    awaitItem(),
                )
            }
            assertEquals(1, muxer.closeCount)
            assertEquals(RecordingState.Idle, coordinator.state.value)
        }

    /**
     * 알림과 플로팅 버블이 남은 시간을 병기하려면 세션이 시작할 때 정해진 제한을 알아야 한다
     * (기능명세서 11.4절). 설정을 다시 읽으면 녹화 중 설정이 바뀌었을 때 잘못 알려 준다.
     */
    @Test
    fun `녹화 상태는 세션이 시작한 시간 제한을 함께 알린다`() =
        runTest {
            val coordinator = coordinator()

            coordinator.start(config(TimeLimit.Limited(10.minutes)))
            runCurrent()

            assertEquals(
                TimeLimit.Limited(10.minutes),
                (coordinator.state.value as RecordingState.Recording).timeLimit,
            )
        }

    @Test
    fun `일시정지 상태도 세션의 시간 제한을 이어서 알린다`() =
        runTest {
            val coordinator = coordinator()
            coordinator.start(config(TimeLimit.Limited(10.minutes)))
            runCurrent()

            coordinator.pause()
            runCurrent()

            assertEquals(
                TimeLimit.Limited(10.minutes),
                (coordinator.state.value as RecordingState.Paused).timeLimit,
            )
        }

    @Test
    fun `제한 없이 시작한 세션은 제한 없음을 알린다`() =
        runTest {
            val coordinator = coordinator()

            coordinator.start(config())
            runCurrent()

            assertEquals(
                TimeLimit.None,
                (coordinator.state.value as RecordingState.Recording).timeLimit,
            )
        }

    @Test
    fun `1분 이상 제한은 1분 전과 10초 전에 예고한다`() =
        runTest {
            val coordinator = coordinator()
            coordinator.sessionEvents.test {
                coordinator.start(config(TimeLimit.Limited(2.minutes)))

                testScheduler.advanceTimeBy(61_000)
                runCurrent()
                assertEquals(
                    RecordingSessionEvent.TimeLimitWarning(60.seconds),
                    awaitItem(),
                )

                testScheduler.advanceTimeBy(50_000)
                runCurrent()
                assertEquals(
                    RecordingSessionEvent.TimeLimitWarning(10.seconds),
                    awaitItem(),
                )
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `정확히 1분 제한은 1분 전 예고 없이 10초 전 예고만 발생한다`() =
        runTest {
            val coordinator = coordinator()
            coordinator.sessionEvents.test {
                coordinator.start(config(TimeLimit.Limited(1.minutes)))

                testScheduler.advanceTimeBy(61_000)
                runCurrent()

                assertEquals(
                    RecordingSessionEvent.TimeLimitWarning(10.seconds),
                    awaitItem(),
                )
                assertEquals(
                    RecordingSessionEvent.AutoStopped(AutoStopReason.TIME_LIMIT_REACHED),
                    awaitItem(),
                )
            }
        }

    @Test
    fun `일시정지 중에는 타이머가 진행되지 않는다`() =
        runTest {
            val coordinator = coordinator()
            coordinator.start(config(TimeLimit.Limited(30.seconds)))

            testScheduler.advanceTimeBy(10_000)
            runCurrent()
            coordinator.pause()
            testScheduler.advanceTimeBy(60_000) // 1분 일시정지
            runCurrent()
            coordinator.resume()

            assertTrue(coordinator.state.value is RecordingState.Recording) {
                "일시정지 시간은 타이머에 포함되지 않아야 한다"
            }
            testScheduler.advanceTimeBy(21_000) // 실녹화 누적 31초
            runCurrent()
            assertEquals(RecordingState.Idle, coordinator.state.value)
        }

    @Test
    fun `저장 공간이 200MB 이하로 떨어지면 자동 중지한다`() =
        runTest {
            val coordinator = coordinator()
            coordinator.sessionEvents.test {
                coordinator.start(config())
                testScheduler.advanceTimeBy(2_000)
                runCurrent()

                availableBytes.value = 150_000_000L
                runCurrent()

                assertEquals(
                    RecordingSessionEvent.AutoStopped(AutoStopReason.STORAGE_LOW),
                    awaitItem(),
                )
            }
            assertEquals(RecordingState.Idle, coordinator.state.value)
        }

    @Test
    fun `일시정지 30분 경과 시 5분 전 예고 후 자동 중지한다`() =
        runTest {
            val coordinator = coordinator()
            coordinator.sessionEvents.test {
                coordinator.start(config())
                coordinator.pause()

                testScheduler.advanceTimeBy(25.minutes.inWholeMilliseconds + 1_000)
                runCurrent()
                assertEquals(
                    RecordingSessionEvent.PauseTimeoutWarning(5.minutes),
                    awaitItem(),
                )

                testScheduler.advanceTimeBy(5.minutes.inWholeMilliseconds)
                runCurrent()
                assertEquals(
                    RecordingSessionEvent.AutoStopped(AutoStopReason.PAUSE_TIMEOUT),
                    awaitItem(),
                )
            }
            assertEquals(RecordingState.Idle, coordinator.state.value)
        }

    @Test
    fun `재개하면 일시정지 자동 중지 타이머가 취소된다`() =
        runTest {
            val coordinator = coordinator()
            coordinator.start(config())
            coordinator.pause()

            testScheduler.advanceTimeBy(10.minutes.inWholeMilliseconds)
            runCurrent()
            coordinator.resume()

            testScheduler.advanceTimeBy(40.minutes.inWholeMilliseconds)
            runCurrent()

            assertTrue(coordinator.state.value is RecordingState.Recording) {
                "재개 후에는 일시정지 타임아웃이 적용되지 않아야 한다"
            }
            coordinator.stop()
        }
}
