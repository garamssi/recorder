package io.rami.screenrecorder.data.storage

import android.content.Context
import android.media.MediaMetadataRetriever
import androidx.core.net.toUri
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * 목록에 표시할 재생 시간을 결정한다 (기능명세서 7.1절).
 *
 * fMP4는 moov에 duration을 담지 않으므로 MediaStore의 DURATION 컬럼이 0으로 기록된다 (ADR-0001).
 * 그럴 때만 파일 메타데이터를 직접 읽어 보완하고, 결과는 캐시해 목록 갱신마다 파일을 다시 열지 않는다.
 *
 * @param probeDurationMs 파일에서 재생 시간을 읽는다. 읽을 수 없으면 null.
 */
internal class VideoDurationResolver(
    private val probeDurationMs: (contentUri: String) -> Long?,
) {
    private val cache = mutableMapOf<String, Long>()

    /** [mediaStoreDurationMs]가 0일 때만 파일을 열어 보완한 재생 시간. */
    @Synchronized
    fun resolve(
        contentUri: String,
        mediaStoreDurationMs: Long,
    ): Duration {
        if (mediaStoreDurationMs > 0) return mediaStoreDurationMs.milliseconds
        val cached = cache[contentUri]
        if (cached != null) return cached.milliseconds
        val probed = probeDurationMs(contentUri) ?: 0L
        cache[contentUri] = probed
        return probed.milliseconds
    }
}

/**
 * MediaMetadataRetriever로 재생 시간을 읽는 기본 구현.
 *
 * 손상·삭제된 항목에서 다양한 RuntimeException이 나므로 null로 흡수한다 (목록 전체가 깨지면 안 된다).
 */
internal fun mediaMetadataDurationProbe(context: Context): (String) -> Long? =
    { contentUri ->
        @Suppress("TooGenericExceptionCaught")
        try {
            MediaMetadataRetriever().use { retriever ->
                retriever.setDataSource(context, contentUri.toUri())
                retriever
                    .extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                    ?.toLongOrNull()
                    ?.takeIf { it > 0 }
            }
        } catch (unreadable: RuntimeException) {
            android.util.Log.w(LOG_TAG, "재생 시간을 읽을 수 없다: $contentUri", unreadable)
            null
        }
    }

private const val LOG_TAG = "VideoDuration"
