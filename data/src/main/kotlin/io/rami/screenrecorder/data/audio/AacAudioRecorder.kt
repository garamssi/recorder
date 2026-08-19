package io.rami.screenrecorder.data.audio

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import io.rami.screenrecorder.data.recorder.AudioRecorder
import io.rami.screenrecorder.data.recorder.EncodedSample
import io.rami.screenrecorder.data.recorder.PauseOffsetTracker
import java.nio.ByteOrder
import kotlin.concurrent.thread

/**
 * PCM 캡처 → 믹싱 → AAC 인코딩 통합 [AudioRecorder] 구현 (기능명세서 4.2절).
 *
 * 전용 스레드에서 소스들을 읽어 믹싱하고 MediaCodec(AAC-LC) 동기 모드로 인코딩한다.
 * 타임스탬프는 "앵커 + 공급된 프레임 시간 + 일시정지 누적"으로 계산해,
 * 일시정지 보정 후 비디오와 같은 연속 타임라인이 되도록 한다 (기능명세서 11.2절).
 */
class AacAudioRecorder(
    private val internalSource: PcmSource?,
    private val microphoneSource: PcmSource?,
    private val internalGain: Float,
    private val microphoneGain: Float,
) : AudioRecorder {
    private val mixer = PcmMixer()

    @Volatile private var running = false

    @Volatile private var suspended = false
    private var workerThread: Thread? = null

    init {
        require(internalSource != null || microphoneSource != null) { "오디오 소스가 없다" }
    }

    override fun start(
        listener: AudioRecorder.Listener,
        pauseOffset: PauseOffsetTracker,
    ) {
        check(workerThread == null) { "이미 시작됐다" }
        running = true
        workerThread =
            thread(name = "AudioRecorder") {
                runCapture(listener, pauseOffset)
            }
    }

    override fun setSuspended(suspended: Boolean) {
        this.suspended = suspended
    }

    override fun stopAndRelease() {
        running = false
        // 블로킹 read를 강제로 깨워 워커가 제때 종료되게 한다.
        // (join 타임아웃 후 워커가 닫힌 muxer/코덱에 접근하는 것을 방지)
        internalSource?.interruptReading()
        microphoneSource?.interruptReading()
        workerThread?.join(STOP_JOIN_TIMEOUT_MS)
        workerThread = null
    }

    @Suppress("TooGenericExceptionCaught") // 오디오 스레드의 어떤 실패든 리스너로 전파해 안전 마무리를 유도한다.
    private fun runCapture(listener: AudioRecorder.Listener, pauseOffset: PauseOffsetTracker) {
        val encoder = AacEncoder(listener)
        try {
            internalSource?.start()
            microphoneSource?.start()
            encoder.start()
            captureLoop(encoder, pauseOffset)
            encoder.finish()
        } catch (audioFailure: Exception) {
            listener.onError(audioFailure)
        } finally {
            encoder.release()
            internalSource?.release()
            microphoneSource?.release()
        }
    }

    private fun captureLoop(
        encoder: AacEncoder,
        pauseOffset: PauseOffsetTracker,
    ) {
        val internalBuffer = ShortArray(CHUNK_FRAMES * CHANNEL_COUNT)
        val microphoneBuffer = ShortArray(CHUNK_FRAMES)
        // System.nanoTime() == CLOCK_MONOTONIC. VirtualDisplay 서피스 타임스탬프(비디오 PTS)와
        // 같은 시계 도메인이다 (실기기 E2E에서 A/V 길이 오차 27ms로 검증됨. 정밀 계측은 Stage 9).
        val anchorUs = System.nanoTime() / NANOS_PER_MICRO
        var framesFed = 0L
        while (running) {
            val internalPcm =
                internalSource?.let { source ->
                    source.read(internalBuffer)
                    internalBuffer
                }
            val microphonePcm =
                microphoneSource?.let { source ->
                    source.read(microphoneBuffer)
                    monoToStereo(microphoneBuffer)
                }
            if (suspended) continue
            val mixed = mixSources(internalPcm, microphonePcm)
            val ptsUs =
                anchorUs +
                    framesFed * MICROS_PER_SECOND / SAMPLE_RATE_HZ +
                    pauseOffset.totalPausedUs
            encoder.feed(mixed, ptsUs)
            framesFed += CHUNK_FRAMES
            encoder.drain()
        }
    }

    private fun mixSources(
        internalPcm: ShortArray?,
        microphonePcm: ShortArray?,
    ): ShortArray =
        when {
            internalPcm != null && microphonePcm != null ->
                mixer.mix(internalPcm, internalGain, microphonePcm, microphoneGain)

            internalPcm != null -> mixer.applyGain(internalPcm, internalGain)
            microphonePcm != null -> mixer.applyGain(microphonePcm, microphoneGain)
            else -> error("오디오 소스가 없다")
        }

    private fun monoToStereo(mono: ShortArray): ShortArray {
        val stereo = ShortArray(mono.size * 2)
        for (index in mono.indices) {
            stereo[index * 2] = mono[index]
            stereo[index * 2 + 1] = mono[index]
        }
        return stereo
    }

    /** MediaCodec AAC-LC 동기 모드 래퍼. */
    private class AacEncoder(
        private val listener: AudioRecorder.Listener,
    ) {
        private val codec: MediaCodec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
        private val bufferInfo = MediaCodec.BufferInfo()

        fun start() {
            val format =
                MediaFormat
                    .createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, SAMPLE_RATE_HZ, CHANNEL_COUNT)
                    .apply {
                        setInteger(
                            MediaFormat.KEY_AAC_PROFILE,
                            MediaCodecInfo.CodecProfileLevel.AACObjectLC,
                        )
                        setInteger(MediaFormat.KEY_BIT_RATE, AAC_BITRATE_BPS)
                    }
            codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            codec.start()
        }

        fun feed(
            pcm: ShortArray,
            ptsUs: Long,
        ) {
            val inputIndex = codec.dequeueInputBuffer(DEQUEUE_TIMEOUT_US)
            if (inputIndex < 0) return
            val inputBuffer = checkNotNull(codec.getInputBuffer(inputIndex))
            val requiredBytes = pcm.size * BYTES_PER_SAMPLE
            check(inputBuffer.capacity() >= requiredBytes) {
                "인코더 입력 버퍼 부족: capacity=${inputBuffer.capacity()}, 필요=$requiredBytes"
            }
            inputBuffer.order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().put(pcm)
            codec.queueInputBuffer(inputIndex, 0, requiredBytes, ptsUs, 0)
        }

        fun drain() {
            while (true) {
                val outputIndex = codec.dequeueOutputBuffer(bufferInfo, 0)
                when {
                    outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED ->
                        listener.onOutputFormatReady(codec.outputFormat)

                    outputIndex < 0 -> return
                    else -> {
                        emitSample(outputIndex)
                        if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) return
                    }
                }
            }
        }

        fun finish() {
            val inputIndex = codec.dequeueInputBuffer(DEQUEUE_TIMEOUT_US)
            if (inputIndex >= 0) {
                codec.queueInputBuffer(inputIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
            }
            drainUntilEndOfStream()
            codec.stop()
        }

        fun release() {
            codec.release()
        }

        private fun drainUntilEndOfStream() {
            var reachedEnd = false
            while (!reachedEnd) {
                val outputIndex = codec.dequeueOutputBuffer(bufferInfo, DEQUEUE_TIMEOUT_US)
                when {
                    outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED ->
                        listener.onOutputFormatReady(codec.outputFormat)

                    outputIndex < 0 -> reachedEnd = true
                    else -> {
                        val isEndOfStream = bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                        emitSample(outputIndex)
                        reachedEnd = isEndOfStream
                    }
                }
            }
        }

        private fun emitSample(outputIndex: Int) {
            val isCodecConfig = bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0
            if (bufferInfo.size > 0 && !isCodecConfig) {
                val outputBuffer = checkNotNull(codec.getOutputBuffer(outputIndex))
                listener.onSample(
                    EncodedSample(
                        buffer = outputBuffer,
                        presentationTimeUs = bufferInfo.presentationTimeUs,
                        isKeyFrame = true,
                    ),
                )
            }
            codec.releaseOutputBuffer(outputIndex, false)
        }
    }

    companion object {
        /** 캡처 샘플레이트 (내부/마이크 공통). */
        const val SAMPLE_RATE_HZ = 48_000

        /** 출력 채널 수 (스테레오). 마이크 모노는 업믹스한다. */
        const val CHANNEL_COUNT = 2

        private const val AAC_BITRATE_BPS = 192_000

        /** 청크 프레임 수. AAC-LC의 자연 프레임 크기(1024 PCM 샘플)와 일치시킨다. */
        private const val CHUNK_FRAMES = 1_024
        private const val BYTES_PER_SAMPLE = 2
        private const val DEQUEUE_TIMEOUT_US = 10_000L
        private const val NANOS_PER_MICRO = 1_000L
        private const val MICROS_PER_SECOND = 1_000_000L
        private const val STOP_JOIN_TIMEOUT_MS = 3_000L
    }
}

/** PCM 소스 추상화 (내부 재생 캡처 / 마이크). */
interface PcmSource {
    /** 캡처를 시작한다. */
    fun start()

    /** [buffer]를 가득 채울 때까지 블로킹으로 읽는다. 읽기 오류는 예외로 전파한다. */
    fun read(buffer: ShortArray)

    /** 블로킹 중인 [read]를 강제로 깨운다 (워커 스레드 종료용, 다른 스레드에서 호출). */
    fun interruptReading()

    /** 캡처를 중지하고 해제한다 (재호출 안전). */
    fun release()
}
