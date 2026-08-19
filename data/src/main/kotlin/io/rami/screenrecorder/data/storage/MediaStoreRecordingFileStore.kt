package io.rami.screenrecorder.data.storage

import android.content.ContentValues
import android.content.Context
import android.media.MediaMetadataRetriever
import android.provider.MediaStore
import dagger.hilt.android.qualifiers.ApplicationContext
import io.rami.screenrecorder.data.recorder.RecordingFileStore
import io.rami.screenrecorder.domain.model.Recording
import io.rami.screenrecorder.domain.model.RecordingId
import io.rami.screenrecorder.domain.model.Resolution
import io.rami.screenrecorder.domain.model.VideoCodec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.milliseconds

/**
 * [RecordingFileStore]의 MediaStore 구현 (기능명세서 6.1절).
 *
 * 녹화 중에는 앱 전용 캐시에 기록하고, 완료 시 Movies/ScreenRecorder로
 * IS_PENDING insert → 스트림 복사 → IS_PENDING 해제 순서로 이동한다.
 */
@Singleton
class MediaStoreRecordingFileStore
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : RecordingFileStore {
        override fun createTempFile(fileName: String): File {
            val directory = File(context.cacheDir, TEMP_DIRECTORY).apply { mkdirs() }
            return File(directory, fileName)
        }

        override suspend fun existingFileNames(): Set<String> =
            withContext(Dispatchers.IO) {
                val names = mutableSetOf<String>()
                context.contentResolver
                    .query(
                        MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                        arrayOf(MediaStore.Video.Media.DISPLAY_NAME),
                        "${MediaStore.Video.Media.RELATIVE_PATH} = ?",
                        arrayOf("$RELATIVE_PATH/"),
                        null,
                    )?.use { cursor ->
                        while (cursor.moveToNext()) {
                            names += cursor.getString(0)
                        }
                    }
                names
            }

        override suspend fun publish(
            tempFile: File,
            fileName: String,
        ): Recording =
            withContext(Dispatchers.IO) {
                val metadata = readMetadata(tempFile)
                val values =
                    ContentValues().apply {
                        put(MediaStore.Video.Media.DISPLAY_NAME, fileName)
                        put(MediaStore.Video.Media.MIME_TYPE, MIME_TYPE)
                        put(MediaStore.Video.Media.RELATIVE_PATH, RELATIVE_PATH)
                        put(MediaStore.Video.Media.IS_PENDING, 1)
                    }
                val resolver = context.contentResolver
                val uri =
                    checkNotNull(
                        resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values),
                    ) { "MediaStore insert 실패: $fileName" }
                resolver.openOutputStream(uri)?.use { output ->
                    tempFile.inputStream().use { input -> input.copyTo(output) }
                }
                values.clear()
                values.put(MediaStore.Video.Media.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
                tempFile.delete()

                Recording(
                    id = RecordingId(uri.lastPathSegment?.toLongOrNull() ?: 0L),
                    displayName = fileName,
                    contentUri = uri.toString(),
                    sizeBytes = metadata.sizeBytes,
                    duration = metadata.durationMs.milliseconds,
                    resolution = metadata.resolution,
                    frameRate = metadata.frameRate,
                    codec = metadata.codec,
                    createdAtEpochMillis = System.currentTimeMillis(),
                    bitrateBps = metadata.bitrateBps,
                )
            }

        private fun readMetadata(file: File): VideoMetadata =
            MediaMetadataRetriever().use { retriever ->
                retriever.setDataSource(file.absolutePath)
                VideoMetadata(
                    sizeBytes = file.length(),
                    durationMs = retriever.longMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION),
                    resolution =
                        Resolution(
                            width =
                                retriever
                                    .longMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                                    .toInt()
                                    .coerceAtLeast(1),
                            height =
                                retriever
                                    .longMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                                    .toInt()
                                    .coerceAtLeast(1),
                        ),
                    frameRate =
                        retriever
                            .longMetadata(MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE)
                            .toInt(),
                    codec = VideoCodec.H264,
                    bitrateBps =
                        retriever
                            .longMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)
                            .toInt()
                            .takeIf { it > 0 },
                )
            }

        private fun MediaMetadataRetriever.longMetadata(key: Int): Long = extractMetadata(key)?.toLongOrNull() ?: 0L

        private data class VideoMetadata(
            val sizeBytes: Long,
            val durationMs: Long,
            val resolution: Resolution,
            val frameRate: Int,
            val codec: VideoCodec,
            val bitrateBps: Int?,
        )

        private companion object {
            const val TEMP_DIRECTORY = "recordings"
            const val RELATIVE_PATH = "Movies/ScreenRecorder"
            const val MIME_TYPE = "video/mp4"
        }
    }
