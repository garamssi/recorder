package io.rami.screenrecorder.data.recorder

import android.media.MediaFormat
import androidx.media3.common.C
import androidx.media3.common.util.MediaFormatUtil
import androidx.media3.common.util.UnstableApi
import androidx.media3.muxer.BufferInfo
import androidx.media3.muxer.FragmentedMp4Muxer
import java.io.File
import java.io.FileOutputStream

/**
 * Media3 [FragmentedMp4Muxer] 기반 [MuxerWriter] 구현 (ADR-0001).
 *
 * fMP4(기본 2초 fragment)로 기록하므로 크래시 시에도 직전 fragment까지 재생 가능하다.
 * 비디오/오디오 콜백 스레드가 다르므로 쓰기 메서드는 동기화한다.
 */
@UnstableApi
class Media3FragmentedMp4Writer : MuxerWriter {
    private var muxer: FragmentedMp4Muxer? = null
    private var outputStream: FileOutputStream? = null

    override fun open(outputFile: File) {
        outputFile.parentFile?.mkdirs()
        val stream = FileOutputStream(outputFile)
        outputStream = stream
        // 샘플 복사 필수: 어댑터는 writeSampleData 직후 코덱 출력 버퍼를 반환하는데,
        // 먹서는 fragment 완성(키프레임 경계 + 2초) 시점까지 샘플을 보관하기 때문이다.
        muxer = FragmentedMp4Muxer
            .Builder(stream.channel)
            .setSampleCopyingEnabled(true)
            .build()
    }

    @Synchronized
    override fun addVideoTrack(format: MediaFormat): Int = addTrack(format)

    @Synchronized
    override fun addAudioTrack(format: MediaFormat): Int = addTrack(format)

    @Synchronized
    override fun writeSample(
        trackId: Int,
        sample: EncodedSample,
    ) {
        val bufferInfo =
            BufferInfo(
                sample.presentationTimeUs,
                sample.buffer.remaining(),
                if (sample.isKeyFrame) C.BUFFER_FLAG_KEY_FRAME else 0,
            )
        requireOpenMuxer().writeSampleData(trackId, sample.buffer, bufferInfo)
    }

    @Synchronized
    override fun close() {
        muxer?.close()
        muxer = null
        outputStream?.close()
        outputStream = null
    }

    private fun addTrack(format: MediaFormat): Int =
        requireOpenMuxer().addTrack(MediaFormatUtil.createFormatFromMediaFormat(format))

    private fun requireOpenMuxer(): FragmentedMp4Muxer = checkNotNull(muxer) { "먹서가 열려 있지 않다" }
}
