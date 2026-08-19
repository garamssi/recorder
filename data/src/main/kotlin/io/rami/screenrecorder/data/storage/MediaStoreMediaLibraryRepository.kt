package io.rami.screenrecorder.data.storage

import android.content.Context
import android.database.ContentObserver
import android.provider.MediaStore
import dagger.hilt.android.qualifiers.ApplicationContext
import io.rami.screenrecorder.domain.model.Recording
import io.rami.screenrecorder.domain.model.RecordingId
import io.rami.screenrecorder.domain.model.Resolution
import io.rami.screenrecorder.domain.model.TrashItem
import io.rami.screenrecorder.domain.model.VideoCodec
import io.rami.screenrecorder.domain.repository.MediaLibraryRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.milliseconds

/**
 * [MediaLibraryRepository]의 MediaStore 구현.
 *
 * Stage 6 범위: 목록 관찰(홈 최근 녹화). 이름 변경/휴지통/복원/영구 삭제는 Stage 7에서 구현한다.
 */
@Singleton
class MediaStoreMediaLibraryRepository
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : MediaLibraryRepository {
        override fun observeRecordings(): Flow<List<Recording>> =
            callbackFlow {
                val resolver = context.contentResolver
                val observer =
                    object : ContentObserver(null) {
                        override fun onChange(selfChange: Boolean) {
                            launch { trySend(queryRecordings()) }
                        }
                    }
                resolver.registerContentObserver(
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                    true,
                    observer,
                )
                trySend(queryRecordings())
                awaitClose { resolver.unregisterContentObserver(observer) }
            }.flowOn(Dispatchers.IO)

        override suspend fun rename(
            id: RecordingId,
            newName: String,
        ): Unit = TODO("Stage 7에서 구현한다 (기능명세서 6.3절)")

        override suspend fun moveToTrash(ids: List<RecordingId>): Unit = TODO("Stage 7에서 구현한다 (기능명세서 7.3절)")

        override fun observeTrash(): Flow<List<TrashItem>> = TODO("Stage 7에서 구현한다 (기능명세서 9절)")

        override suspend fun restore(ids: List<RecordingId>): Unit = TODO("Stage 7에서 구현한다 (기능명세서 9절)")

        override suspend fun permanentlyDelete(ids: List<RecordingId>): Unit = TODO("Stage 7에서 구현한다 (기능명세서 9절)")

        private fun queryRecordings(): List<Recording> {
            val recordings = mutableListOf<Recording>()
            context.contentResolver
                .query(
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                    arrayOf(
                        MediaStore.Video.Media._ID,
                        MediaStore.Video.Media.DISPLAY_NAME,
                        MediaStore.Video.Media.SIZE,
                        MediaStore.Video.Media.DURATION,
                        MediaStore.Video.Media.WIDTH,
                        MediaStore.Video.Media.HEIGHT,
                        MediaStore.Video.Media.DATE_ADDED,
                    ),
                    "${MediaStore.Video.Media.RELATIVE_PATH} = ?",
                    arrayOf("$RELATIVE_PATH/"),
                    "${MediaStore.Video.Media.DATE_ADDED} DESC",
                )?.use { cursor ->
                    val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                    val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
                    val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
                    val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
                    val widthColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.WIDTH)
                    val heightColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.HEIGHT)
                    val dateColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED)
                    while (cursor.moveToNext()) {
                        val id = cursor.getLong(idColumn)
                        recordings +=
                            Recording(
                                id = RecordingId(id),
                                displayName = cursor.getString(nameColumn),
                                contentUri = "${MediaStore.Video.Media.EXTERNAL_CONTENT_URI}/$id",
                                sizeBytes = cursor.getLong(sizeColumn),
                                duration = cursor.getLong(durationColumn).milliseconds,
                                resolution =
                                    Resolution(
                                        width = cursor.getInt(widthColumn).coerceAtLeast(1),
                                        height = cursor.getInt(heightColumn).coerceAtLeast(1),
                                    ),
                                frameRate = 0,
                                codec = VideoCodec.H264,
                                createdAtEpochMillis = cursor.getLong(dateColumn) * MILLIS_PER_SECOND,
                                bitrateBps = null,
                            )
                    }
                }
            return recordings
        }

        private companion object {
            const val RELATIVE_PATH = "Movies/ScreenRecorder"
            const val MILLIS_PER_SECOND = 1_000L
        }
    }
