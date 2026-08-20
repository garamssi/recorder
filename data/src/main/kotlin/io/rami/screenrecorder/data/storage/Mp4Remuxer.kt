package io.rami.screenrecorder.data.storage

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import java.io.File
import java.io.FileDescriptor
import java.nio.ByteBuffer

/** remux 결과 — 표준 moov를 만들며 알아낸 실제 재생 시간 (ADR-0001 개정). */
internal data class RemuxResult(
    val durationUs: Long,
)

/**
 * fMP4 임시 파일을 표준 MP4로 다시 담는다 (ADR-0001 개정: 결정 C).
 *
 * `FragmentedMp4Muxer`는 seek 인덱스(`sidx`/`mfra`)를 쓰지 않아 결과물이 탐색 불가이고
 * `mvhd`에 duration도 없다. 재인코딩 없이 컨테이너만 바꿔 표준 moov(`stss`/`stco`/duration)를 만든다.
 *
 * 출력은 MediaStore가 준 파일 디스크립터에 직접 쓴다 — 중간 파일이 없어 저장 공간을 두 배로 쓰지 않는다.
 */
internal object Mp4Remuxer {
    /**
     * [source]를 [target]에 표준 MP4로 다시 담는다.
     *
     * @return 기록한 샘플에서 계산한 재생 시간.
     * @throws java.io.IOException 소스를 열 수 없거나 트랙이 없을 때.
     */
    fun remux(
        source: File,
        target: FileDescriptor,
    ): RemuxResult {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(source.absolutePath)
            require(extractor.trackCount > 0) { "remux할 트랙이 없다: ${source.name}" }
            val muxer = MediaMuxer(target, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            try {
                return copyTracks(extractor, muxer)
            } finally {
                muxer.release()
            }
        } finally {
            extractor.release()
        }
    }

    private fun copyTracks(
        extractor: MediaExtractor,
        muxer: MediaMuxer,
    ): RemuxResult {
        val trackMapping = addTracks(extractor, muxer)
        muxer.start()
        val buffer = ByteBuffer.allocateDirect(SAMPLE_BUFFER_BYTES)
        val bufferInfo = MediaCodec.BufferInfo()
        var lastPresentationTimeUs = 0L
        // selectTrack으로 고른 트랙의 샘플만 돌아오므로 대응표에는 항상 값이 있다.
        var sampleSize = extractor.readSampleData(buffer, 0)
        while (sampleSize >= 0) {
            val outputTrack = checkNotNull(trackMapping[extractor.sampleTrackIndex]) { "선택하지 않은 트랙의 샘플" }
            bufferInfo.set(0, sampleSize, extractor.sampleTime, extractor.sampleFlagsAsBufferFlags())
            muxer.writeSampleData(outputTrack, buffer, bufferInfo)
            lastPresentationTimeUs = maxOf(lastPresentationTimeUs, extractor.sampleTime)
            extractor.advance()
            sampleSize = extractor.readSampleData(buffer, 0)
        }
        muxer.stop()
        return RemuxResult(durationUs = lastPresentationTimeUs)
    }

    /** 소스 트랙을 먹서에 등록하고 "소스 인덱스 → 출력 인덱스" 대응을 만든다. */
    private fun addTracks(
        extractor: MediaExtractor,
        muxer: MediaMuxer,
    ): Map<Int, Int> {
        val mapping = mutableMapOf<Int, Int>()
        for (index in 0 until extractor.trackCount) {
            val format = extractor.getTrackFormat(index)
            val mime = format.getString(MediaFormat.KEY_MIME).orEmpty()
            // 비디오/오디오만 옮긴다. 알 수 없는 트랙은 MediaMuxer가 거부해 전체 실패를 부른다.
            if (!mime.startsWith("video/") && !mime.startsWith("audio/")) continue
            mapping[index] = muxer.addTrack(format)
            extractor.selectTrack(index)
        }
        require(mapping.isNotEmpty()) { "remux할 비디오/오디오 트랙이 없다" }
        return mapping
    }

    /** MediaExtractor 샘플 플래그를 MediaCodec BufferInfo 플래그로 옮긴다. */
    private fun MediaExtractor.sampleFlagsAsBufferFlags(): Int =
        if (sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0) {
            MediaCodec.BUFFER_FLAG_KEY_FRAME
        } else {
            0
        }

    /** 1080p60 키프레임 한 장을 담기에 충분한 크기. */
    private const val SAMPLE_BUFFER_BYTES = 4 * 1024 * 1024
}
