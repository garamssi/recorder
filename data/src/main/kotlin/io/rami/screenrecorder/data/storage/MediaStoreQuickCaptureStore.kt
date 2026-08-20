package io.rami.screenrecorder.data.storage

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.MediaStore
import dagger.hilt.android.qualifiers.ApplicationContext
import io.rami.screenrecorder.domain.model.CapturedImage
import io.rami.screenrecorder.domain.model.VoiceMemo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.milliseconds

/**
 * 화면 캡처 이미지와 음성 녹음을 MediaStore에 발행한다 (기능명세서 12, 13절).
 *
 * 녹화 영상과 동일하게 IS_PENDING insert → 스트림 쓰기 → IS_PENDING 해제 순서를 지켜
 * 쓰다 만 파일이 갤러리/음악 앱에 노출되지 않게 한다.
 */
@Singleton
class MediaStoreQuickCaptureStore
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        /** 이미 쓰이고 있는 이미지 파일명 (순번 충돌 방지용). */
        suspend fun existingImageNames(): Set<String> =
            queryNames(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, IMAGE_RELATIVE_PATH)

        /** 이미 쓰이고 있는 오디오 파일명. */
        suspend fun existingAudioNames(): Set<String> =
            queryNames(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, AUDIO_RELATIVE_PATH)

        /** [bitmap]을 PNG로 저장하고 저장된 이미지 정보를 반환한다. */
        suspend fun publishImage(
            bitmap: Bitmap,
            fileName: String,
        ): CapturedImage =
            withContext(Dispatchers.IO) {
                val values =
                    contentValues(
                        displayName = fileName,
                        mimeType = IMAGE_MIME_TYPE,
                        relativePath = IMAGE_RELATIVE_PATH,
                    )
                val uri = insertPending(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values, fileName)
                val sizeBytes =
                    writeThenPublish(uri) { output ->
                        bitmap.compress(Bitmap.CompressFormat.PNG, PNG_QUALITY, output)
                    }
                CapturedImage(
                    displayName = fileName,
                    contentUri = uri.toString(),
                    sizeBytes = sizeBytes,
                    widthPx = bitmap.width,
                    heightPx = bitmap.height,
                    createdAtEpochMillis = System.currentTimeMillis(),
                )
            }

        /**
         * [tempFile] m4a를 Music/ScreenRecorder로 옮기고 저장된 녹음 정보를 반환한다.
         *
         * 재생 가능한 오디오 트랙이 없으면(시작 직후 중지 등) 임시 파일만 정리하고 null을 반환한다.
         */
        suspend fun publishVoiceMemo(
            tempFile: File,
            fileName: String,
        ): VoiceMemo? =
            withContext(Dispatchers.IO) {
                val durationMs = readAudioDurationMs(tempFile)
                if (durationMs == null) {
                    tempFile.delete()
                    return@withContext null
                }
                val values =
                    contentValues(
                        displayName = fileName,
                        mimeType = AUDIO_MIME_TYPE,
                        relativePath = AUDIO_RELATIVE_PATH,
                    )
                val uri = insertPending(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values, fileName)
                val sizeBytes =
                    try {
                        writeThenPublish(uri) { output -> tempFile.inputStream().use { it.copyTo(output) } }
                    } finally {
                        tempFile.delete()
                    }
                VoiceMemo(
                    displayName = fileName,
                    contentUri = uri.toString(),
                    sizeBytes = sizeBytes,
                    duration = durationMs.milliseconds,
                    createdAtEpochMillis = System.currentTimeMillis(),
                )
            }

        private fun contentValues(
            displayName: String,
            mimeType: String,
            relativePath: String,
        ) = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }

        private fun insertPending(
            collection: Uri,
            values: ContentValues,
            fileName: String,
        ): Uri =
            checkNotNull(context.contentResolver.insert(collection, values)) {
                "MediaStore insert 실패: $fileName"
            }

        /** 스트림에 쓰고 IS_PENDING을 해제한다. 실패하면 고아 레코드를 지우고 원인을 전파한다. */
        private fun writeThenPublish(
            uri: Uri,
            write: (java.io.OutputStream) -> Unit,
        ): Long {
            val resolver = context.contentResolver
            try {
                val output =
                    checkNotNull(resolver.openOutputStream(uri)) { "MediaStore 쓰기 스트림 열기 실패: $uri" }
                output.use(write)
                resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
            } catch (
                @Suppress("TooGenericExceptionCaught") publishFailure: Exception,
            ) {
                resolver.delete(uri, null, null)
                throw publishFailure
            }
            return readSizeBytes(uri)
        }

        private fun readSizeBytes(uri: Uri): Long =
            context.contentResolver
                .query(uri, arrayOf(MediaStore.MediaColumns.SIZE), null, null, null)
                ?.use { cursor -> if (cursor.moveToFirst()) cursor.getLong(0) else 0L }
                ?: 0L

        private suspend fun queryNames(
            collection: Uri,
            relativePath: String,
        ): Set<String> =
            withContext(Dispatchers.IO) {
                val names = mutableSetOf<String>()
                context.contentResolver
                    .query(
                        collection,
                        arrayOf(MediaStore.MediaColumns.DISPLAY_NAME),
                        "${MediaStore.MediaColumns.RELATIVE_PATH} = ?",
                        arrayOf("$relativePath/"),
                        null,
                    )?.use { cursor ->
                        while (cursor.moveToNext()) names += cursor.getString(0)
                    }
                names
            }

        /** 재생 가능한 오디오 트랙이 없으면 null (빈 파일·손상 파일). */
        @Suppress("TooGenericExceptionCaught") // setDataSource는 손상 파일에 다양한 RuntimeException을 던진다.
        private fun readAudioDurationMs(file: File): Long? {
            if (file.length() == 0L) return null
            return MediaMetadataRetriever().use { retriever ->
                try {
                    retriever.setDataSource(file.absolutePath)
                    if (retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO) != "yes") {
                        null
                    } else {
                        retriever
                            .extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                            ?.toLongOrNull()
                            ?.takeIf { it > 0 }
                    }
                } catch (unreadable: RuntimeException) {
                    android.util.Log.w(LOG_TAG, "음성 임시 파일을 읽을 수 없다: ${file.name}", unreadable)
                    null
                }
            }
        }

        private companion object {
            const val IMAGE_RELATIVE_PATH = "Pictures/ScreenRecorder"
            const val AUDIO_RELATIVE_PATH = "Music/ScreenRecorder"
            const val IMAGE_MIME_TYPE = "image/png"
            const val AUDIO_MIME_TYPE = "audio/mp4"
            const val PNG_QUALITY = 100
            const val LOG_TAG = "QuickCaptureStore"
        }
    }
