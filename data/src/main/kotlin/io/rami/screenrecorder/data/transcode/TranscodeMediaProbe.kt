package io.rami.screenrecorder.data.transcode

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.media3.common.MimeTypes
import io.rami.screenrecorder.domain.model.VideoCodec

/** 원본 미디어 메타데이터 실측 (검수 #5: 코덱/비트레이트를 추측하지 않는다). */
internal class TranscodeMediaProbe(
    private val context: Context,
) {
    /**
     * 실제 비트레이트를 컨테이너에서 읽는다. fMP4는 MediaStore duration이 0이므로
     * (ADR-0001) retriever가 단일 진실 공급원이고, 크기/시간 추정은 최후 폴백이다.
     */
    fun probeBitrate(
        uriString: String,
        sizeBytes: Long,
        mediaStoreDurationMs: Long,
    ): Int {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, Uri.parse(uriString))
            retriever
                .extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)
                ?.toIntOrNull()
                ?.takeIf { it > 0 }
                ?.let { return it }
            val durationMs =
                retriever
                    .extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                    ?.toLongOrNull()
                    ?.takeIf { it > 0 } ?: mediaStoreDurationMs
            return TranscodeWorker.estimateBitrate(sizeBytes, durationMs)
        } finally {
            retriever.release()
        }
    }

    /** 원본 비디오 트랙의 실제 코덱 (HEVC 원본에 코덱 강제 변환 금지). */
    fun probeCodec(uriString: String): VideoCodec {
        val extractor = MediaExtractor()
        val mimeTypes =
            try {
                extractor.setDataSource(context, Uri.parse(uriString), null)
                (0 until extractor.trackCount).map { trackIndex ->
                    extractor.getTrackFormat(trackIndex).getString(MediaFormat.KEY_MIME).orEmpty()
                }
            } finally {
                extractor.release()
            }
        return if (MimeTypes.VIDEO_H265 in mimeTypes) VideoCodec.HEVC else VideoCodec.H264
    }
}
