package io.rami.screenrecorder.data.storage

import android.content.Context
import android.provider.MediaStore
import dagger.hilt.android.qualifiers.ApplicationContext
import io.rami.screenrecorder.data.recorder.RecordingFileStore
import io.rami.screenrecorder.domain.model.Recording
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [RecordingFileStore] 의 MediaStore 구현 (기능명세서 6.1절).
 *
 * 녹화 중에는 앱 전용 캐시에 기록하고, 완료 시 Movies/ScreenRecorder 로 옮긴다.
 * 옮기는 순서와 실패 처리는 [RecordingPublisher] 가, 플랫폼 호출은
 * [MediaStorePublishTarget]·[MediaMetadataRecordingReader] 가 맡는다.
 */
@Singleton
class MediaStoreRecordingFileStore
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : RecordingFileStore {
        private val publisher =
            RecordingPublisher(
                target = MediaStorePublishTarget(context),
                readMetadata = MediaMetadataRecordingReader(),
            )

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
        ): Recording? = withContext(Dispatchers.IO) { publisher.publish(tempFile, fileName) }

        private companion object {
            const val TEMP_DIRECTORY = "recordings"
        }
    }
