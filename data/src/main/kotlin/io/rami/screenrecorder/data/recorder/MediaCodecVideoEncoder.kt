package io.rami.screenrecorder.data.recorder

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.view.Surface
import io.rami.screenrecorder.domain.model.VideoCodec
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * MediaCodec 비동기 모드 기반 [VideoEncoder] 구현.
 *
 * Surface 입력으로 캡처 프레임을 받고, 출력 샘플을 리스너로 전달한다.
 * 일시정지는 PARAMETER_KEY_SUSPEND로 입력 프레임 공급을 중단한다 (기능명세서 11.2절).
 */
class MediaCodecVideoEncoder : VideoEncoder {
    private var codec: MediaCodec? = null
    private var callbackThread: HandlerThread? = null
    private val endOfStreamLatch = CountDownLatch(1)

    override fun prepare(
        config: VideoEncoderConfig,
        listener: VideoEncoder.Listener,
    ): Surface {
        val format = createMediaFormat(config)
        val mimeType = requireNotNull(format.getString(MediaFormat.KEY_MIME)) { "비디오 MIME 타입이 없다" }
        val thread = HandlerThread("VideoEncoderCallback").apply { start() }
        callbackThread = thread
        return MediaCodec.createEncoderByType(mimeType).let { createdCodec ->
            codec = createdCodec
            createdCodec.setCallback(encoderCallback(listener), Handler(thread.looper))
            createdCodec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            createdCodec.createInputSurface()
        }
    }

    override fun start() {
        requireCodec().start()
    }

    override fun setSuspended(suspended: Boolean) {
        val parameters =
            Bundle().apply {
                putInt(MediaCodec.PARAMETER_KEY_SUSPEND, if (suspended) 1 else 0)
            }
        requireCodec().setParameters(parameters)
    }

    override fun requestKeyFrame() {
        val parameters =
            Bundle().apply {
                putInt(MediaCodec.PARAMETER_KEY_REQUEST_SYNC_FRAME, 0)
            }
        requireCodec().setParameters(parameters)
    }

    override fun stopAndRelease() {
        val activeCodec = requireCodec()
        try {
            activeCodec.signalEndOfInputStream()
            endOfStreamLatch.await(END_OF_STREAM_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            activeCodec.stop()
        } finally {
            activeCodec.release()
            callbackThread?.quitSafely()
            codec = null
        }
    }

    private fun requireCodec(): MediaCodec = checkNotNull(codec) { "prepare()가 먼저 호출되어야 한다" }

    private fun createMediaFormat(config: VideoEncoderConfig): MediaFormat {
        val mimeType =
            when (config.codec) {
                VideoCodec.H264 -> MediaFormat.MIMETYPE_VIDEO_AVC
                VideoCodec.HEVC -> MediaFormat.MIMETYPE_VIDEO_HEVC
            }
        return MediaFormat
            .createVideoFormat(mimeType, config.resolution.width, config.resolution.height)
            .apply {
                setInteger(
                    MediaFormat.KEY_COLOR_FORMAT,
                    MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface,
                )
                setInteger(MediaFormat.KEY_BIT_RATE, config.bitrateBps)
                setInteger(MediaFormat.KEY_FRAME_RATE, config.frameRateFps)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, I_FRAME_INTERVAL_SECONDS)
                // 정적 화면에서는 VirtualDisplay가 프레임을 만들지 않아 키프레임 주기가 깨지고
                // fMP4 fragment가 디스크에 flush되지 않는다 (크래시 복구 요구의 전제).
                // 마지막 프레임을 재인코딩해 최소 프레임 공급을 보장한다.
                setLong(
                    MediaFormat.KEY_REPEAT_PREVIOUS_FRAME_AFTER,
                    REPEAT_PREVIOUS_FRAME_AFTER_US,
                )
            }
    }

    private fun encoderCallback(listener: VideoEncoder.Listener) =
        object : MediaCodec.Callback() {
            override fun onOutputBufferAvailable(
                codec: MediaCodec,
                index: Int,
                info: MediaCodec.BufferInfo,
            ) {
                val isCodecConfig = info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0
                if (info.size > 0 && !isCodecConfig) {
                    val buffer = checkNotNull(codec.getOutputBuffer(index)) { "출력 버퍼가 없다: $index" }
                    listener.onSample(
                        EncodedSample(
                            buffer = buffer,
                            presentationTimeUs = info.presentationTimeUs,
                            isKeyFrame = info.flags and MediaCodec.BUFFER_FLAG_KEY_FRAME != 0,
                        ),
                    )
                }
                codec.releaseOutputBuffer(index, false)
                // 반드시 버퍼 해제 후에 신호한다 — 먼저 내리면 stopAndRelease()의 codec.stop()과
                // 이 콜백의 releaseOutputBuffer()가 경합해 IllegalStateException이 난다
                // (EncoderThroughputTest가 재현한 실기기 레이스).
                if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                    endOfStreamLatch.countDown()
                }
            }

            override fun onOutputFormatChanged(
                codec: MediaCodec,
                format: MediaFormat,
            ) {
                listener.onOutputFormatReady(format)
            }

            override fun onError(
                codec: MediaCodec,
                error: MediaCodec.CodecException,
            ) {
                listener.onError(error)
            }

            override fun onInputBufferAvailable(
                codec: MediaCodec,
                index: Int,
            ) {
                // Surface 입력 모드에서는 호출되지 않는다.
            }
        }

    private companion object {
        const val I_FRAME_INTERVAL_SECONDS = 1
        const val END_OF_STREAM_TIMEOUT_MS = 2_000L

        /** 새 프레임이 없을 때 마지막 프레임을 반복 인코딩하는 간격 (0.1초 = 최소 10fps). */
        const val REPEAT_PREVIOUS_FRAME_AFTER_US = 100_000L
    }
}
