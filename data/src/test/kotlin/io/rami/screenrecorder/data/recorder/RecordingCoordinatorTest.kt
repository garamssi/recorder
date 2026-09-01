package io.rami.screenrecorder.data.recorder

import android.media.MediaFormat
import android.view.Surface
import app.cash.turbine.test
import io.mockk.mockk
import io.rami.screenrecorder.domain.model.AudioSource
import io.rami.screenrecorder.domain.model.CountdownDuration
import io.rami.screenrecorder.domain.model.MicrophoneDevice
import io.rami.screenrecorder.domain.model.RecordingConfig
import io.rami.screenrecorder.domain.model.RecordingSessionEvent
import io.rami.screenrecorder.domain.model.RecordingState
import io.rami.screenrecorder.domain.model.Resolution
import io.rami.screenrecorder.domain.session.MonotonicClock
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File
import java.nio.ByteBuffer
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalCoroutinesApi::class)
class RecordingCoordinatorTest {
    // --- 페이크 어댑터 ---

    private class FakeEncoder : VideoEncoder {
        val surface = mockk<Surface>()
        var listener: VideoEncoder.Listener? = null
        var lastConfig: VideoEncoderConfig? = null
        var prepareError: Throwable? = null
        var started = false
        var suspended: Boolean? = null
        var keyFrameRequests = 0
        var stopCount = 0

        override fun prepare(
            config: VideoEncoderConfig,
            listener: VideoEncoder.Listener,
        ): Surface {
            prepareError?.let { throw it }
            this.listener = listener
            lastConfig = config
            return surface
        }

        override fun start() {
            started = true
        }

        override fun setSuspended(suspended: Boolean) {
            this.suspended = suspended
        }

        override fun requestKeyFrame() {
            keyFrameRequests++
        }

        override fun stopAndRelease() {
            stopCount++
        }
    }

    private class FakeCapture : ScreenCaptureSource {
        var startedWith: Surface? = null
        var listener: ScreenCaptureSource.Listener? = null
        var stopped = false

        override fun start(
            encoderSurface: Surface,
            resolution: Resolution,
            listener: ScreenCaptureSource.Listener,
        ) {
            startedWith = encoderSurface
            this.listener = listener
        }

        override fun stop() {
            stopped = true
        }
    }

    private class FakeAudioRecorder : AudioRecorder {
        var listener: AudioRecorder.Listener? = null
        var suspended: Boolean? = null
        var stopCount = 0

        override fun start(
            listener: AudioRecorder.Listener,
            pauseOffset: PauseOffsetTracker,
        ) {
            this.listener = listener
        }

        override fun setSuspended(suspended: Boolean) {
            this.suspended = suspended
        }

        override fun stopAndRelease() {
            stopCount++
        }
    }

    private class FakeMuxer : MuxerWriter {
        var openedFile: File? = null
        var videoFormat: MediaFormat? = null
        val writtenSamples = mutableListOf<Pair<Int, Long>>()
        val writtenPts: List<Long> get() = writtenSamples.map { it.second }
        var closeCount = 0
        val closed: Boolean get() = closeCount > 0

        override fun open(outputFile: File) {
            openedFile = outputFile
        }

        override fun addVideoTrack(format: MediaFormat): Int {
            videoFormat = format
            return 0
        }

        override fun addAudioTrack(format: MediaFormat): Int = 1

        override fun writeSample(
            trackId: Int,
            sample: EncodedSample,
        ) {
            writtenSamples += trackId to sample.presentationTimeUs
        }

        override fun close() {
            closeCount++
        }
    }

    private class FakeFileStore : RecordingFileStore {
        var publishedFileName: String? = null

        /** true면 빈 녹화(저장할 내용 없음)를 흉내내 null을 반환한다. */
        var publishReturnsEmpty = false

        /** 발행 중에 흘려보낼 진행률. 저장 중 상태에 실려 오는지 검증한다. */
        var publishProgress: List<Float> = emptyList()

        /** 설정하면 발행이 이 예외로 실패한다 (저장 공간 부족 등). */
        var publishFailure: Exception? = null

        override fun createTempFile(fileName: String): File = File("build/tmp/fake/$fileName")

        override fun listTempFiles(): List<File> = emptyList()

        override suspend fun existingFileNames(): Set<String> = emptySet()

        override suspend fun discardAbandonedPublishes(): Int = 0

        override suspend fun publish(
            tempFile: File,
            fileName: String,
            onProgress: (Float) -> Unit,
        ): io.rami.screenrecorder.domain.model.Recording? {
            publishProgress.forEach(onProgress)
            publishFailure?.let { throw it }
            if (publishReturnsEmpty) return null
            publishedFileName = fileName
            return io.rami.screenrecorder.domain.model
                .Recording(
                    id =
                        io.rami.screenrecorder.domain.model
                            .RecordingId(42),
                    displayName = fileName,
                    contentUri = "content://media/42",
                    sizeBytes = 1_000,
                    duration = kotlin.time.Duration.ZERO,
                    resolution = Resolution.FHD,
                    frameRate = 60,
                    codec = io.rami.screenrecorder.domain.model.VideoCodec.H264,
                    createdAtEpochMillis = 0,
                    bitrateBps = null,
                )
        }
    }

    @Test
    fun `선택한 마이크를 쓸 수 없으면 폴백 이벤트를 알린다`() =
        runTest {
            // 조용히 다른 마이크로 녹음되면 사용자가 녹화가 끝난 뒤에야 알게 된다 (기능명세서 4.2절).
            microphoneFallback = MicrophoneDevice.BLUETOOTH
            val coordinator = coordinator()

            coordinator.sessionEvents.test {
                coordinator.start(noCountdownConfig.copy(audioSource = AudioSource.MICROPHONE))
                advanceUntilIdle()

                assertEquals(
                    RecordingSessionEvent.MicrophoneFellBack(MicrophoneDevice.BLUETOOTH),
                    awaitItem(),
                )
            }
        }

    /** 저장 중 상태에 실려야 하는 녹화 길이. 기본값 0 과 구분되도록 0 이 아닌 값을 쓴다. */
    private val recordedMillis = 90_000L

    // --- 테스트 픽스처 ---

    private val encoder = FakeEncoder()
    private val capture = FakeCapture()
    private val muxer = FakeMuxer()
    private val fileStore = FakeFileStore()
    private val audioRecorder = FakeAudioRecorder()
    private var microphoneFallback: MicrophoneDevice? = null

    private fun TestScope.coordinator(): RecordingCoordinator {
        val clock = MonotonicClock { testScheduler.currentTime }
        val factory =
            object : RecorderSessionFactory {
                override fun createVideoEncoder(): VideoEncoder = encoder

                override fun createCaptureSource(): ScreenCaptureSource = capture

                override fun createMuxer(): MuxerWriter = muxer

                override suspend fun createAudioRecorder(config: RecordingConfig): AudioSetup? =
                    if (config.audioSource == AudioSource.SILENT) {
                        null
                    } else {
                        AudioSetup(audioRecorder, microphoneFallback)
                    }

                override fun createFrameProcessor(): FrameProcessor = error("전체 화면 테스트에서는 프레임 프로세서를 만들지 않는다")
            }
        return RecordingCoordinator(
            sessionFactory = factory,
            dependencies =
                RecorderDependencies(
                    fileStore = fileStore,
                    fileNameProvider = { "Rec_test.mp4" },
                    displayInfo = { Resolution(2560, 1600) },
                    clock = clock,
                    storageRepository = { MutableStateFlow(Long.MAX_VALUE) },
                ),
            scope = backgroundScope,
            blockingDispatcher = StandardTestDispatcher(testScheduler),
        )
    }

    // 대부분의 비디오 파이프라인 테스트는 무음 설정으로 단순화한다 (오디오 통합은 별도 테스트).
    private val noCountdownConfig =
        RecordingConfig.DEFAULT.copy(countdown = CountdownDuration.NONE, audioSource = AudioSource.SILENT)

    private val audioConfig = RecordingConfig.DEFAULT.copy(countdown = CountdownDuration.NONE)

    private fun sample(ptsUs: Long) =
        EncodedSample(
            buffer = ByteBuffer.allocate(4),
            presentationTimeUs = ptsUs,
            isKeyFrame = false,
        )

    // --- 시작 ---

    /**
     * 저장 중 상태에 실리는 값 (기능명세서 2.1절 [결정]).
     *
     * 화면이 저장 진척을 보여 주려면 이 배선이 진실을 실어 보내야 한다. 이 경로에는
     * 오랫동안 테스트가 없어 Stopping 이 알림과 버블에서 새는 회귀가 살아남았다.
     */
    @Test
    fun `중지하면 저장 중 상태에 녹화 길이와 파일명을 실어 보낸다`() =
        runTest {
            val coordinator = coordinator()
            coordinator.start(noCountdownConfig)
            // 시간을 감지 않으면 길이가 0 이라, 기본값과 구분되지 않는 공허한 단정이 된다.
            advanceTimeBy(recordedMillis)

            val seen = mutableListOf<RecordingState.Stopping>()
            val watcher =
                backgroundScope.launch {
                    coordinator.state.collect { if (it is RecordingState.Stopping) seen += it }
                }
            fileStore.publishProgress = listOf(0.25f, 0.75f)
            coordinator.stop()
            advanceUntilIdle()
            watcher.cancel()

            // 상태는 합류(conflating)하는 StateFlow 라 중간 표본은 건너뛸 수 있다. 진행률은
            // 스트림이 아니라 표본이므로, 실려 오는 값과 단조성만 계약이다.
            assertEquals("Rec_test.mp4", seen.first().fileName)
            assertEquals(recordedMillis.milliseconds, seen.first().elapsed)
            assertNull(seen.first().progress)
            // 단조성은 여기서 단정하지 않는다 — 합류 때문에 관측 표본이 하나뿐이라
            // 어떤 변경으로도 실패할 수 없다. 그 계약은 RecordingPublisher 쪽에 있다.
            assertEquals(0.75f, seen.last().progress)
        }

    /**
     * 저장 실패를 알린다 (기능명세서 2.1절 [결정]).
     *
     * 중지 처리는 성공·실패·빈 세션 모두 같은 방식으로 끝나므로 상태만으로는 구분되지 않는다.
     * 실패를 삼키면 진행 게이지가 도중에 사라지고 사용자는 저장된 줄 안다.
     */
    @Test
    fun `발행이 실패하면 저장 실패를 알린다`() =
        runTest {
            val coordinator = coordinator()
            coordinator.start(noCountdownConfig)
            fileStore.publishFailure = java.io.IOException("저장 공간이 부족하다")

            coordinator.sessionEvents.test {
                coordinator.stop()
                advanceUntilIdle()

                assertEquals(RecordingSessionEvent.SaveFailed, awaitItem())
            }
        }

    @Test
    fun `발행이 실패해도 유휴로 돌아간다`() =
        runTest {
            val coordinator = coordinator()
            coordinator.start(noCountdownConfig)
            fileStore.publishFailure = java.io.IOException("저장 공간이 부족하다")

            coordinator.stop()
            advanceUntilIdle()

            assertTrue(coordinator.state.value is RecordingState.Idle)
        }

    /** 정상 발행에서는 실패를 알리지 않는다 — 알림이 늘 뜨면 의미가 없다. */
    @Test
    fun `발행이 성공하면 저장 실패를 알리지 않는다`() =
        runTest {
            val coordinator = coordinator()
            coordinator.start(noCountdownConfig)

            val seen = mutableListOf<RecordingSessionEvent>()
            val watcher = backgroundScope.launch { coordinator.sessionEvents.collect { seen += it } }
            coordinator.stop()
            advanceUntilIdle()
            watcher.cancel()

            assertTrue(seen.none { it is RecordingSessionEvent.SaveFailed })
        }

    /** 발행이 끝나면 유휴로 돌아간다 — 저장 중이 남아 있으면 다음 녹화가 막힌다. */
    @Test
    fun `발행이 끝나면 유휴로 돌아간다`() =
        runTest {
            val coordinator = coordinator()
            coordinator.start(noCountdownConfig)

            coordinator.stop()
            advanceUntilIdle()

            assertTrue(coordinator.state.value is RecordingState.Idle)
        }

    @Test
    fun `시작하면 인코더 서피스로 캡처를 시작하고 먹서를 연다`() =
        runTest {
            val coordinator = coordinator()

            coordinator.start(noCountdownConfig)

            assertTrue(encoder.started)
            assertEquals(encoder.surface, capture.startedWith)
            assertNotNull(muxer.openedFile)
            assertTrue(coordinator.state.value is RecordingState.Recording)
        }

    @Test
    fun `카운트다운이 초 단위로 진행된 후 녹화가 시작된다`() =
        runTest {
            val coordinator = coordinator()
            val observed = mutableListOf<Int>()
            val job =
                launch {
                    coordinator.state.collect {
                        if (it is RecordingState.CountingDown) observed += it.remainingSeconds
                    }
                }

            coordinator.start(RecordingConfig.DEFAULT) // 카운트다운 3초

            assertEquals(listOf(3, 2, 1), observed)
            assertTrue(coordinator.state.value is RecordingState.Recording)
            // 인코딩은 카운트다운 종료 후에만 시작 (명세 3절: 카운트다운은 영상에 미포함)
            job.cancel()
        }

    @Test
    fun `카운트다운을 스킵하면 즉시 녹화가 시작된다`() =
        runTest {
            val coordinator = coordinator()
            val job =
                launch {
                    coordinator.state.collect {
                        if (it is RecordingState.CountingDown && it.remainingSeconds == 3) {
                            coordinator.skipCountdown()
                        }
                    }
                }

            coordinator.start(RecordingConfig.DEFAULT)

            assertTrue(coordinator.state.value is RecordingState.Recording)
            assertTrue(testScheduler.currentTime < 3_000) { "스킵했으므로 3초를 기다리지 않아야 한다" }
            job.cancel()
        }

    /**
     * 페이크 화면은 2560x1600(16:10)이다. 1080p 프리셋은 짧은 변만 정하므로 1728x1080 이
     * 되어야 한다 — 1920x1080 으로 못 박으면 좌우에 검은 띠가 생긴다 (기능명세서 4.1절 [결정]).
     * 자동 비트레이트는 그 해상도에서 다시 계산된다(픽셀 처리량 0.9배 -> 13.5Mbps).
     */
    @Test
    fun `설정 해상도와 자동 비트레이트가 인코더에 전달된다`() =
        runTest {
            coordinator().start(noCountdownConfig)

            val config = requireNotNull(encoder.lastConfig)
            assertEquals(Resolution(1728, 1080), config.resolution)
            assertEquals(60, config.frameRateFps)
            assertEquals(13_500_000, config.bitrateBps)
        }

    // --- 인코더 출력 -> 먹서 ---

    @Test
    fun `출력 포맷이 준비되면 비디오 트랙을 추가하고 샘플을 기록한다`() =
        runTest {
            coordinator().start(noCountdownConfig)
            val format = mockk<MediaFormat>()

            encoder.listener?.onOutputFormatReady(format)
            encoder.listener?.onSample(sample(ptsUs = 16_666))

            assertEquals(format, muxer.videoFormat)
            assertEquals(listOf(16_666L), muxer.writtenPts)
        }

    // --- 일시정지 / 재개 ---

    @Test
    fun `일시정지하면 인코더를 중단하고 상태가 Paused가 된다`() =
        runTest {
            val coordinator = coordinator()
            coordinator.start(noCountdownConfig)
            encoder.listener?.onOutputFormatReady(mockk())

            coordinator.pause()

            assertEquals(true, encoder.suspended)
            assertTrue(coordinator.state.value is RecordingState.Paused)
        }

    @Test
    fun `재개하면 키프레임을 강제하고 일시정지 구간이 타임스탬프에서 제외된다`() =
        runTest {
            val coordinator = coordinator()
            coordinator.start(noCountdownConfig)
            encoder.listener?.onOutputFormatReady(mockk())
            encoder.listener?.onSample(sample(ptsUs = 1_000_000))

            coordinator.pause()
            testScheduler.advanceTimeBy(5_000) // 5초 일시정지
            coordinator.resume()

            assertEquals(1, encoder.keyFrameRequests)
            assertEquals(false, encoder.suspended)
            assertTrue(coordinator.state.value is RecordingState.Recording)

            encoder.listener?.onSample(sample(ptsUs = 6_100_000))
            // 6.1s - 5s 일시정지 = 1.1s
            assertEquals(listOf(1_000_000L, 1_100_000L), muxer.writtenPts)
        }

    // --- 중지 ---

    @Test
    fun `중지하면 캡처-인코더-먹서 순서로 정리하고 저장 완료 이벤트를 낸다`() =
        runTest {
            val coordinator = coordinator()
            coordinator.start(noCountdownConfig)
            encoder.listener?.onOutputFormatReady(mockk())

            coordinator.completedRecordings.test {
                coordinator.stop()

                val completed = awaitItem()
                assertEquals("Rec_test.mp4", completed.displayName)
            }
            assertTrue(capture.stopped)
            assertEquals(1, encoder.stopCount)
            assertTrue(muxer.closed)
            assertEquals("Rec_test.mp4", fileStore.publishedFileName)
            assertEquals(RecordingState.Idle, coordinator.state.value)
        }

    /**
     * 파이프라인을 세우는 동안의 상태 (기능명세서 6.1절 [결정]).
     *
     * 이 구간이 `Idle` 로 남으면 버블과 홈이 "녹화 시작" 을 그대로 내놓는다.
     */
    @Test
    fun `파이프라인을 세우는 동안 준비 상태를 알린다`() =
        runTest {
            val coordinator = coordinator()
            val seen = mutableListOf<RecordingState>()
            val collector = launch { coordinator.state.toList(seen) }

            coordinator.start(noCountdownConfig)
            runCurrent()
            collector.cancel()

            val preparingAt = seen.indexOf(RecordingState.Preparing)
            val recordingAt = seen.indexOfFirst { it is RecordingState.Recording }
            assertTrue(preparingAt >= 0, "준비 상태를 한 번도 알리지 않았다: $seen")
            assertTrue(preparingAt < recordingAt, "준비는 녹화보다 먼저여야 한다: $seen")
        }

    // --- 완료 표시 (기능명세서 2.1절 [결정]) ---

    @Test
    fun `발행이 확정되면 완료 표시가 남는다`() =
        runTest {
            val coordinator = coordinator()
            coordinator.start(noCountdownConfig)
            encoder.listener?.onOutputFormatReady(mockk())

            coordinator.stop()

            // 완료 이벤트를 구독한 곳이 없어도 남아야 한다 — 버블로 녹화하면 홈이 없다.
            assertEquals("Rec_test.mp4", coordinator.pendingCompletedRecording.value?.displayName)
        }

    @Test
    fun `홈이 보여 준 뒤 소모하면 완료 표시가 사라진다`() =
        runTest {
            val coordinator = coordinator()
            coordinator.start(noCountdownConfig)
            encoder.listener?.onOutputFormatReady(mockk())
            coordinator.stop()
            assertNotNull(coordinator.pendingCompletedRecording.value)

            coordinator.consumeCompletedRecording()

            assertNull(coordinator.pendingCompletedRecording.value)
        }

    @Test
    fun `새 세션이 시작되면 보여 주지 못한 완료 표시를 버린다`() =
        runTest {
            val coordinator = coordinator()
            coordinator.start(noCountdownConfig)
            encoder.listener?.onOutputFormatReady(mockk())
            coordinator.stop()
            assertNotNull(coordinator.pendingCompletedRecording.value)

            coordinator.start(noCountdownConfig)

            // 지난 녹화의 완료가 새 녹화를 가려서는 안 된다.
            assertNull(coordinator.pendingCompletedRecording.value)
        }

    @Test
    fun `녹화된 내용이 없으면 완료 이벤트 없이 유휴로 돌아간다`() =
        runTest {
            // 시작 직후 프레임이 하나도 인코딩되기 전에 중지하면 빈 파일이 된다 (root cause 대응).
            fileStore.publishReturnsEmpty = true
            val coordinator = coordinator()
            coordinator.start(noCountdownConfig)

            coordinator.completedRecordings.test {
                coordinator.stop()

                expectNoEvents()
            }
            assertTrue(muxer.closed)
            assertNull(fileStore.publishedFileName)
            assertEquals(RecordingState.Idle, coordinator.state.value)
        }

    @Test
    fun `시스템이 캡처를 중단해도 파일이 안전하게 마무리된다`() =
        runTest {
            val coordinator = coordinator()
            coordinator.start(noCountdownConfig)
            encoder.listener?.onOutputFormatReady(mockk())

            coordinator.completedRecordings.test {
                capture.listener?.onStoppedBySystem()
                runCurrent()

                assertNotNull(awaitItem())
            }
            assertTrue(muxer.closed)
            assertEquals(RecordingState.Idle, coordinator.state.value)
        }

    @Test
    fun `카운트다운 중 중지하면 파일 없이 유휴 상태로 돌아간다`() =
        runTest {
            val coordinator = coordinator()
            val job =
                launch {
                    coordinator.state.collect {
                        if (it is RecordingState.CountingDown && it.remainingSeconds == 3) {
                            coordinator.stop()
                        }
                    }
                }

            coordinator.start(RecordingConfig.DEFAULT)

            assertEquals(RecordingState.Idle, coordinator.state.value)
            assertNull(muxer.openedFile)
            assertNull(fileStore.publishedFileName)
            job.cancel()
        }

    // --- 오디오 통합 (기능명세서 4.2절, fMP4 트랙 게이팅) ---

    @Test
    fun `오디오가 있으면 두 트랙이 모두 준비된 후에만 샘플이 기록된다`() =
        runTest {
            coordinator().start(audioConfig)

            encoder.listener?.onOutputFormatReady(mockk())
            encoder.listener?.onSample(sample(ptsUs = 16_666))
            // 오디오 트랙이 아직 없으므로 기록 보류 (fMP4 헤더는 첫 쓰기 시 트랙 구성 확정)
            assertEquals(emptyList<Long>(), muxer.writtenPts)

            audioRecorder.listener?.onOutputFormatReady(mockk())
            audioRecorder.listener?.onSample(sample(ptsUs = 21_333))

            // 게이트 해제 후 보류분 포함 모두 기록
            assertEquals(listOf(0 to 16_666L, 1 to 21_333L), muxer.writtenSamples)
        }

    @Test
    fun `무음 설정이면 오디오 레코더 없이 비디오 트랙만 기록한다`() =
        runTest {
            coordinator().start(noCountdownConfig)

            encoder.listener?.onOutputFormatReady(mockk())
            encoder.listener?.onSample(sample(ptsUs = 16_666))

            assertNull(audioRecorder.listener) { "무음 설정에서는 오디오 레코더가 시작되지 않아야 한다" }
            assertEquals(listOf(0 to 16_666L), muxer.writtenSamples)
        }

    @Test
    fun `일시정지하면 오디오 공급도 함께 중단되고 재개 시 복구된다`() =
        runTest {
            val coordinator = coordinator()
            coordinator.start(audioConfig)

            coordinator.pause()
            assertEquals(true, audioRecorder.suspended)

            coordinator.resume()
            assertEquals(false, audioRecorder.suspended)
        }

    @Test
    fun `중지 시 오디오 레코더도 해제된다`() =
        runTest {
            val coordinator = coordinator()
            coordinator.start(audioConfig)

            coordinator.stop()

            assertEquals(1, audioRecorder.stopCount)
        }

    // --- 오류 / 재진입 경로 ---

    @Test
    fun `인코더 오류가 발생하면 세션을 안전하게 마무리한다`() =
        runTest {
            val coordinator = coordinator()
            coordinator.start(noCountdownConfig)
            encoder.listener?.onOutputFormatReady(mockk())

            encoder.listener?.onError(RuntimeException("코덱 오류"))
            runCurrent()

            assertTrue(muxer.closed)
            assertEquals(RecordingState.Idle, coordinator.state.value)
        }

    @Test
    fun `일시정지 상태에서 중지해도 정상 마무리된다`() =
        runTest {
            val coordinator = coordinator()
            coordinator.start(noCountdownConfig)
            encoder.listener?.onOutputFormatReady(mockk())
            coordinator.pause()

            coordinator.stop()

            assertTrue(muxer.closed)
            assertEquals("Rec_test.mp4", fileStore.publishedFileName)
            assertEquals(RecordingState.Idle, coordinator.state.value)
        }

    @Test
    fun `중지를 두 번 호출해도 마무리는 한 번만 실행된다`() =
        runTest {
            val coordinator = coordinator()
            coordinator.start(noCountdownConfig)

            coordinator.stop()
            coordinator.stop()

            assertEquals(1, encoder.stopCount)
            assertEquals(1, muxer.closeCount)
        }

    @Test
    fun `인코더 오류와 시스템 중지가 동시에 와도 마무리는 한 번만 실행된다`() =
        runTest {
            val coordinator = coordinator()
            coordinator.start(noCountdownConfig)
            encoder.listener?.onOutputFormatReady(mockk())

            encoder.listener?.onError(RuntimeException("코덱 오류"))
            capture.listener?.onStoppedBySystem()
            runCurrent()

            assertEquals(1, muxer.closeCount)
            assertEquals(1, encoder.stopCount)
            assertEquals(RecordingState.Idle, coordinator.state.value)
        }

    @Test
    fun `파이프라인 시작이 실패하면 먹서를 닫고 유휴 상태로 남는다`() =
        runTest {
            encoder.prepareError = IllegalStateException("인코더 초기화 실패")
            val coordinator = coordinator()

            val result = runCatching { coordinator.start(noCountdownConfig) }

            assertTrue(result.isFailure)
            assertTrue(muxer.closed) { "실패 시 열린 먹서는 닫혀야 한다" }
            assertEquals(RecordingState.Idle, coordinator.state.value)
        }

    // --- 경과 시간 ---

    @Test
    fun `녹화 중 경과 시간이 1초 단위로 갱신된다`() =
        runTest {
            val coordinator = coordinator()
            coordinator.start(noCountdownConfig)

            testScheduler.advanceTimeBy(3_500)
            runCurrent()

            val state = coordinator.state.value as RecordingState.Recording
            assertEquals(3, state.elapsed.inWholeSeconds)
            coordinator.stop()
        }
}
