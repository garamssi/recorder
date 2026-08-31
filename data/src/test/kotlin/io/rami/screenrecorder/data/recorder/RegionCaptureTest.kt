package io.rami.screenrecorder.data.recorder

import android.view.Surface
import app.cash.turbine.test
import io.mockk.mockk
import io.rami.screenrecorder.domain.model.AudioSource
import io.rami.screenrecorder.domain.model.CaptureMode
import io.rami.screenrecorder.domain.model.CaptureRegion
import io.rami.screenrecorder.domain.model.CountdownDuration
import io.rami.screenrecorder.domain.model.CropGeometry
import io.rami.screenrecorder.domain.model.RecordingConfig
import io.rami.screenrecorder.domain.model.RecordingSessionEvent
import io.rami.screenrecorder.domain.model.RecordingState
import io.rami.screenrecorder.domain.model.Resolution
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** 부분 영역 캡처 파이프라인 배선 (기능명세서 2.2절, 5절 [결정]). */
@OptIn(ExperimentalCoroutinesApi::class)
class RegionCaptureTest {
    private val harness = CoordinatorHarness()

    private val regionConfig =
        RecordingConfig.DEFAULT.copy(
            countdown = CountdownDuration.NONE,
            audioSource = AudioSource.SILENT,
            captureMode =
                CaptureMode.Region(
                    CaptureRegion(x = 100, y = 200, width = 641, height = 480),
                ),
        )

    @Test
    fun `부분 영역이면 프레임 프로세서 입력 서피스로 캡처한다`() =
        runTest {
            val coordinator = harness.coordinator(this)

            coordinator.start(regionConfig)

            // 캡처(VirtualDisplay)는 디스플레이 전체를 프로세서 입력에 그린다.
            assertEquals(harness.frameProcessor.inputSurface, harness.capture.startedWith)
            assertEquals(Resolution(2560, 1600), harness.capture.startedResolution)
            // 프로세서는 인코더 서피스에 크롭 결과를 그린다.
            assertEquals(harness.encoder.surface, harness.frameProcessor.outputSurface)
            assertEquals(
                CropGeometry.compute(
                    sourceSize = Resolution(2560, 1600),
                    cropRegion = CaptureRegion(x = 100, y = 200, width = 641, height = 480),
                    outputSize = Resolution(640, 480),
                ),
                harness.frameProcessor.geometry,
            )
        }

    @Test
    fun `부분 영역 인코더 해상도는 짝수 정렬된 영역 크기다`() =
        runTest {
            val coordinator = harness.coordinator(this)

            coordinator.start(regionConfig)

            // 641 → 640 (H.264 색차 정렬)
            assertEquals(Resolution(640, 480), harness.encoder.lastConfig?.resolution)
        }

    @Test
    fun `전체 화면이면 프레임 프로세서를 만들지 않는다`() =
        runTest {
            val coordinator = harness.coordinator(this)

            coordinator.start(
                RecordingConfig.DEFAULT.copy(
                    countdown = CountdownDuration.NONE,
                    audioSource = AudioSource.SILENT,
                ),
            )

            assertNull(harness.frameProcessor.outputSurface)
            assertEquals(harness.encoder.surface, harness.capture.startedWith)
        }

    @Test
    fun `부분 영역 녹화 중 회전이 감지되면 자동 일시정지하고 이벤트를 낸다`() =
        runTest {
            val coordinator = harness.coordinator(this)
            coordinator.start(regionConfig)

            coordinator.sessionEvents.test {
                // 회전 = 캡처 대상 크기 변화 (가로/세로 뒤집힘)
                harness.capture.listener?.onContentResize(1600, 2560)
                runCurrent()

                assertEquals(RecordingSessionEvent.RegionInvalidatedByRotation, awaitItem())
                assertTrue(coordinator.state.value is RecordingState.Paused)
                assertEquals(true, harness.encoder.suspended)
            }
        }

    @Test
    fun `전체 화면 녹화 중 회전은 일시정지하지 않는다`() =
        runTest {
            val coordinator = harness.coordinator(this)
            coordinator.start(
                RecordingConfig.DEFAULT.copy(
                    countdown = CountdownDuration.NONE,
                    audioSource = AudioSource.SILENT,
                ),
            )

            harness.capture.listener?.onContentResize(1600, 2560)
            runCurrent()

            assertTrue(coordinator.state.value is RecordingState.Recording)
        }

    @Test
    fun `중지 시 프레임 프로세서도 해제한다`() =
        runTest {
            val coordinator = harness.coordinator(this)
            coordinator.start(regionConfig)

            coordinator.stop()

            assertTrue(harness.frameProcessor.stopped)
        }
}

/** 코디네이터 페이크 조립 (RecordingCoordinatorTest와 동일 패턴, 프레임 프로세서 포함). */
@OptIn(ExperimentalCoroutinesApi::class)
internal class CoordinatorHarness {
    class FakeFrameProcessor : FrameProcessor {
        val inputSurface = mockk<Surface>()
        var outputSurface: Surface? = null
        var sourceSize: Resolution? = null
        var geometry: CropGeometry? = null
        var stopped = false

        override fun start(
            outputSurface: Surface,
            sourceSize: Resolution,
            geometry: CropGeometry,
        ): Surface {
            this.outputSurface = outputSurface
            this.sourceSize = sourceSize
            this.geometry = geometry
            return inputSurface
        }

        override fun stop() {
            stopped = true
        }
    }

    class FakeCaptureWithResolution : ScreenCaptureSource {
        var startedWith: Surface? = null
        var startedResolution: Resolution? = null
        var listener: ScreenCaptureSource.Listener? = null
        var stopped = false

        override fun start(
            encoderSurface: Surface,
            resolution: Resolution,
            listener: ScreenCaptureSource.Listener,
        ) {
            startedWith = encoderSurface
            startedResolution = resolution
            this.listener = listener
        }

        override fun stop() {
            stopped = true
        }
    }

    class FakeEncoderForRegion : VideoEncoder {
        val surface = mockk<Surface>()
        var lastConfig: VideoEncoderConfig? = null
        var suspended: Boolean? = null

        override fun prepare(
            config: VideoEncoderConfig,
            listener: VideoEncoder.Listener,
        ): Surface {
            lastConfig = config
            return surface
        }

        override fun start() = Unit

        override fun setSuspended(suspended: Boolean) {
            this.suspended = suspended
        }

        override fun requestKeyFrame() = Unit

        override fun stopAndRelease() = Unit
    }

    val encoder = FakeEncoderForRegion()
    val capture = FakeCaptureWithResolution()
    val frameProcessor = FakeFrameProcessor()

    fun coordinator(testScope: kotlinx.coroutines.test.TestScope): RecordingCoordinator {
        val factory =
            object : RecorderSessionFactory {
                override fun createVideoEncoder(): VideoEncoder = encoder

                override fun createCaptureSource(): ScreenCaptureSource = capture

                override fun createMuxer(): MuxerWriter = NoopMuxer()

                override suspend fun createAudioRecorder(config: RecordingConfig): AudioSetup? = null

                override fun createFrameProcessor(): FrameProcessor = frameProcessor
            }
        return RecordingCoordinator(
            sessionFactory = factory,
            dependencies =
                RecorderDependencies(
                    fileStore = NoopFileStore(),
                    fileNameProvider = { "Rec_test.mp4" },
                    displayInfo = { Resolution(2560, 1600) },
                    clock =
                        io.rami.screenrecorder.domain.session
                            .MonotonicClock { testScope.testScheduler.currentTime },
                    storageRepository = {
                        kotlinx.coroutines.flow
                            .MutableStateFlow(Long.MAX_VALUE)
                    },
                ),
            scope = testScope.backgroundScope,
            blockingDispatcher =
                kotlinx.coroutines.test
                    .StandardTestDispatcher(testScope.testScheduler),
        )
    }

    class NoopMuxer : MuxerWriter {
        override fun open(outputFile: java.io.File) = Unit

        override fun addVideoTrack(format: android.media.MediaFormat): Int = 0

        override fun addAudioTrack(format: android.media.MediaFormat): Int = 1

        override fun writeSample(
            trackId: Int,
            sample: EncodedSample,
        ) = Unit

        override fun close() = Unit
    }

    class NoopFileStore : RecordingFileStore {
        override fun createTempFile(fileName: String): java.io.File = java.io.File("build/tmp/fake/$fileName")

        override fun listTempFiles(): List<java.io.File> = emptyList()

        override suspend fun existingFileNames(): Set<String> = emptySet()

        var abandonedDiscardCount = 0

        override suspend fun discardAbandonedPublishes(): Int {
            abandonedDiscardCount++
            return 0
        }

        override suspend fun publish(
            tempFile: java.io.File,
            fileName: String,
        ) = io.rami.screenrecorder.domain.model.Recording(
            id =
                io.rami.screenrecorder.domain.model
                    .RecordingId(1),
            displayName = fileName,
            contentUri = "content://media/1",
            sizeBytes = 1,
            duration = kotlin.time.Duration.ZERO,
            resolution = Resolution.FHD,
            frameRate = 60,
            codec =
                io.rami.screenrecorder.domain.model.VideoCodec.H264,
            createdAtEpochMillis = 0,
            bitrateBps = null,
        )
    }
}
