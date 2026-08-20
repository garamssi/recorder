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

        override fun listTempFiles(): List<File> {
            val directory = File(context.cacheDir, TEMP_DIRECTORY)
            return directory.listFiles()?.filter { it.isFile }?.sortedBy { it.name } ?: emptyList()
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
        ): Recording? =
            withContext(Dispatchers.IO) {
                // 프레임이 인코딩되기 전에 중지되면 빈/재생 불가 파일이 남는다.
                // 저장할 내용이 없으므로 임시 파일만 정리하고 null을 반환한다 (오류 아님).
                val metadata = readMetadata(tempFile)
                if (metadata == null) {
                    tempFile.delete()
                    return@withContext null
                }
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
                try {
                    writeToMediaStore(tempFile, uri)
                    values.clear()
                    values.put(MediaStore.Video.Media.IS_PENDING, 0)
                    resolver.update(uri, values, null, null)
                } catch (
                    // 복사 실패 시 IS_PENDING 고아 레코드가 남지 않도록 정리 후 원인을 그대로 전파한다.
                    @Suppress("TooGenericExceptionCaught") publishFailure: Exception,
                ) {
                    resolver.delete(uri, null, null)
                    throw publishFailure
                } finally {
                    tempFile.delete()
                }

                Recording(
                    id = RecordingId(android.content.ContentUris.parseId(uri)),
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

        /**
         * 임시 fMP4를 표준 MP4로 remux해 MediaStore에 쓴다 (ADR-0001 개정).
         *
         * remux가 실패하면 원본을 그대로 복사한다 — 시크가 안 되더라도 녹화물을 잃는 것보다 낫다.
         */
        private fun writeToMediaStore(
            tempFile: File,
            uri: android.net.Uri,
        ) {
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
                        android.util.Log.w(LOG_TAG, "remux 실패 — 원본 fMP4로 저장한다: ${tempFile.name}", remuxFailure)
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

        /** 빈 파일이거나 재생 가능한 비디오 트랙이 없으면 null. */
        private fun readMetadata(file: File): VideoMetadata? {
            if (file.length() == 0L) return null
            return MediaMetadataRetriever().use { retriever ->
                if (!retriever.tryReadVideoTrack(file)) {
                    null
                } else {
                    retriever.toVideoMetadata(file)
                }
            }
        }

        /** 데이터 소스를 열고 비디오 트랙이 있으면 true. 손상 파일은 저장할 내용 없음으로 본다. */
        @Suppress("TooGenericExceptionCaught") // setDataSource는 손상 파일에 다양한 RuntimeException을 던진다.
        private fun MediaMetadataRetriever.tryReadVideoTrack(file: File): Boolean =
            try {
                setDataSource(file.absolutePath)
                extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO) == "yes"
            } catch (unreadable: RuntimeException) {
                android.util.Log.w(LOG_TAG, "임시 파일을 읽을 수 없어 복구 대상에서 제외한다: ${file.name}", unreadable)
                false
            }

        private fun MediaMetadataRetriever.toVideoMetadata(file: File): VideoMetadata =
            VideoMetadata(
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
            const val LOG_TAG = "RecordingFileStore"

            /** MediaMuxer는 탐색 가능한 디스크립터가 필요하다 (쓰기 전용 "w"로는 안 된다). */
            const val FILE_MODE_READ_WRITE = "rw"

            /** remux 실패 후 재작성할 때 이전 내용을 남기지 않는다. */
            const val FILE_MODE_TRUNCATE = "wt"
        }
    }
