package io.rami.screenrecorder.data.storage

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import io.rami.screenrecorder.domain.model.Resolution
import io.rami.screenrecorder.domain.model.VideoCodec
import java.io.File

// 녹화본 발행의 플랫폼 절반 (기능명세서 6.1절).
// 순서와 실패 처리 정책은 RecordingPublisher.kt 가 갖는다.

/** [PublishTarget] 의 MediaStore 구현. */
internal class MediaStorePublishTarget(
    private val context: Context,
) : PublishTarget {
    override fun create(fileName: String): PublishSlot {
        val values =
            ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, fileName)
                put(MediaStore.Video.Media.MIME_TYPE, MIME_TYPE)
                put(MediaStore.Video.Media.RELATIVE_PATH, RELATIVE_PATH)
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }
        val uri =
            checkNotNull(
                context.contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values),
            ) { "MediaStore insert 실패: $fileName" }
        return PublishSlot(id = ContentUris.parseId(uri), uri = uri.toString())
    }

    /**
     * 임시 fMP4 를 표준 MP4 로 remux 해 MediaStore 에 쓴다 (ADR-0001 개정).
     *
     * remux 가 실패하면 원본을 그대로 복사한다 — 시크가 안 되더라도 녹화물을 잃는 것보다 낫다.
     */
    override fun write(
        slot: PublishSlot,
        tempFile: File,
    ) {
        val uri = slot.toUri()
        val resolver = context.contentResolver
        val descriptor =
            checkNotNull(resolver.openFileDescriptor(uri, FILE_MODE_READ_WRITE)) {
                "MediaStore 파일 디스크립터 열기 실패: $uri"
            }
        val remuxed =
            descriptor.use {
                @Suppress("TooGenericExceptionCaught") // 어떤 실패든 원본 복사로 되돌린다.
                try {
                    Mp4Remuxer.remux(tempFile, it.fileDescriptor)
                    true
                } catch (remuxFailure: Exception) {
                    Log.w(LOG_TAG, "remux 실패 — 원본 fMP4로 저장한다: ${tempFile.name}", remuxFailure)
                    false
                }
            }
        if (remuxed) return
        val output =
            checkNotNull(resolver.openOutputStream(uri, FILE_MODE_TRUNCATE)) {
                "MediaStore 쓰기 스트림 열기 실패: $uri"
            }
        output.use { tempFile.inputStream().use { input -> input.copyTo(it) } }
    }

    override fun finish(slot: PublishSlot) {
        val values = ContentValues().apply { put(MediaStore.Video.Media.IS_PENDING, 0) }
        context.contentResolver.update(slot.toUri(), values, null, null)
    }

    override fun discard(slot: PublishSlot) {
        context.contentResolver.delete(slot.toUri(), null, null)
    }

    private fun PublishSlot.toUri(): Uri = Uri.parse(uri)
}

/** [RecordingMetadataReader] 의 MediaMetadataRetriever 구현. */
internal class MediaMetadataRecordingReader : RecordingMetadataReader {
    /** 빈 파일이거나 재생 가능한 비디오 트랙이 없으면 null. */
    override fun read(file: File): RecordingMetadata? {
        if (file.length() == 0L) return null
        return MediaMetadataRetriever().use { retriever ->
            if (!retriever.tryReadVideoTrack(file)) null else retriever.toMetadata(file)
        }
    }

    /** 데이터 소스를 열고 비디오 트랙이 있으면 true. 손상 파일은 저장할 내용 없음으로 본다. */
    @Suppress("TooGenericExceptionCaught") // setDataSource 는 손상 파일에 다양한 RuntimeException 을 던진다.
    private fun MediaMetadataRetriever.tryReadVideoTrack(file: File): Boolean =
        try {
            setDataSource(file.absolutePath)
            extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO) == "yes"
        } catch (unreadable: RuntimeException) {
            Log.w(LOG_TAG, "임시 파일을 읽을 수 없어 복구 대상에서 제외한다: ${file.name}", unreadable)
            false
        }

    private fun MediaMetadataRetriever.toMetadata(file: File): RecordingMetadata =
        RecordingMetadata(
            sizeBytes = file.length(),
            durationMs = longMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION),
            resolution =
                Resolution(
                    width =
                        longMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                            .toInt()
                            .coerceAtLeast(1),
                    height =
                        longMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                            .toInt()
                            .coerceAtLeast(1),
                ),
            frameRate =
                longMetadata(MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE).toInt(),
            codec = VideoCodec.H264,
            bitrateBps =
                longMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE).toInt().takeIf { it > 0 },
        )

    private fun MediaMetadataRetriever.longMetadata(key: Int): Long = extractMetadata(key)?.toLongOrNull() ?: 0L
}

internal const val RELATIVE_PATH = "Movies/ScreenRecorder"
private const val MIME_TYPE = "video/mp4"
private const val LOG_TAG = "RecordingFileStore"

/** MediaMuxer 는 탐색 가능한 디스크립터가 필요하다 (쓰기 전용 "w"로는 안 된다). */
private const val FILE_MODE_READ_WRITE = "rw"

/** remux 실패 후 재작성할 때 이전 내용을 남기지 않는다. */
private const val FILE_MODE_TRUNCATE = "wt"
