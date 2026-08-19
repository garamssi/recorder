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
    private lateinit var codec: MediaCodec
    private lateinit var callbackThread: HandlerThread
    private val endOfStreamLatch = CountDownLatch(1)

    override fun prepare(
        config: VideoEncoderConfig,
        listener: VideoEncoder.Listener,
    ): Surface {
        val format = createMediaFormat(config)
        callbackThread = HandlerThread("VideoEncoderCallback").apply { start() }
        codec = MediaCodec.createEncoderByType(format.getString(MediaFormat.KEY_MIME).orEmpty())
        codec.setCallback(encoderCallback(listener), Handler(callbackThread.looper))
        codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        return codec.createInputSurface()
    }

    override fun start() {
        codec.start()
    }

    override fun setSuspended(suspended: Boolean) {
        val parameters =
            Bundle().apply {
                putInt(MediaCodec.PARAMETER_KEY_SUSPEND, if (suspended) 1 else 0)
            }
        codec.setParameters(parameters)
    }

    override fun requestKeyFrame() {
        val parameters =
            Bundle().apply {
                putInt(MediaCodec.PARAMETER_KEY_REQUEST_SYNC_FRAME, 0)
            }
        codec.setParameters(parameters)
    }

    override fun stopAndRelease() {
        codec.signalEndOfInputStream()
        endOfStreamLatch.await(END_OF_STREAM_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        codec.stop()
        codec.release()
        callbackThread.quitSafely()
    }

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
            }
    }

    private fun encoderCallback(listener: VideoEncoder.Listener) =
        object : MediaCodec.Callback() {
            override fun onOutputBufferAvailable(
                codec: MediaCodec,
                index: Int,
                info: MediaCodec.BufferInfo,
            ) {
                if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                    endOfStreamLatch.countDown()
                }
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
    }
}
