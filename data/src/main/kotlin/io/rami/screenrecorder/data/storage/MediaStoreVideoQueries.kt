package io.rami.screenrecorder.data.storage

import android.content.ContentResolver
import android.content.Context
import android.os.Bundle
import android.provider.MediaStore
import io.rami.screenrecorder.domain.model.Recording
import io.rami.screenrecorder.domain.model.RecordingId
import io.rami.screenrecorder.domain.model.Resolution
import io.rami.screenrecorder.domain.model.TrashItem
import io.rami.screenrecorder.domain.model.VideoCodec
import kotlin.time.Duration.Companion.milliseconds

/** MediaStore 비디오 쿼리 모음 (목록/휴지통, 기능명세서 7, 9절). */
internal class MediaStoreVideoQueries(
    private val context: Context,
) {
    fun queryRecordings(): List<Recording> {
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
                "${MediaStore.Video.Media.RELATIVE_PATH} = ? AND ${MediaStore.Video.Media.IS_PENDING} = 0",
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

    /** 휴지통 항목 조회 (기능명세서 9절: 남은 보관일 표시). */
    fun queryTrash(): List<TrashItem> {
        val trashItems = mutableListOf<TrashItem>()
        val queryArgs =
            Bundle().apply {
                putInt(MediaStore.QUERY_ARG_MATCH_TRASHED, MediaStore.MATCH_ONLY)
                putString(
                    ContentResolver.QUERY_ARG_SQL_SELECTION,
                    "${MediaStore.Video.Media.RELATIVE_PATH} = ?",
                )
                putStringArray(
                    ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS,
                    arrayOf("$RELATIVE_PATH/"),
                )
            }
        context.contentResolver
            .query(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                arrayOf(
                    MediaStore.Video.Media._ID,
                    MediaStore.Video.Media.DISPLAY_NAME,
                    MediaStore.Video.Media.SIZE,
                    MediaStore.Video.Media.DURATION,
                    MediaStore.MediaColumns.DATE_EXPIRES,
                ),
                queryArgs,
                null,
            )?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
                val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
                val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
                val expiresColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_EXPIRES)
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idColumn)
                    val expiresEpochSeconds = cursor.getLong(expiresColumn)
                    trashItems +=
                        TrashItem(
                            recording =
                                Recording(
                                    id = RecordingId(id),
                                    displayName = cursor.getString(nameColumn),
                                    contentUri = "${MediaStore.Video.Media.EXTERNAL_CONTENT_URI}/$id",
                                    sizeBytes = cursor.getLong(sizeColumn),
                                    duration = cursor.getLong(durationColumn).milliseconds,
                                    resolution = Resolution(1, 1),
                                    frameRate = 0,
                                    codec = VideoCodec.H264,
                                    createdAtEpochMillis = 0,
                                    bitrateBps = null,
                                ),
                            daysUntilDeletion = daysUntil(expiresEpochSeconds),
                        )
                }
            }
        return trashItems
    }

    private fun daysUntil(expiresEpochSeconds: Long): Int {
        if (expiresEpochSeconds <= 0) return 0
        val remainingMillis =
            expiresEpochSeconds * MILLIS_PER_SECOND - System.currentTimeMillis()
        return (remainingMillis / MILLIS_PER_DAY).toInt().coerceAtLeast(0)
    }

    private companion object {
        const val RELATIVE_PATH = "Movies/ScreenRecorder"
        const val MILLIS_PER_SECOND = 1_000L
        const val MILLIS_PER_DAY = 86_400_000L
    }
}
