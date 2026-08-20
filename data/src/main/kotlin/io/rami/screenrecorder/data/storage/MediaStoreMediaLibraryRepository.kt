package io.rami.screenrecorder.data.storage

import android.content.ContentValues
import android.content.Context
import android.database.ContentObserver
import android.provider.MediaStore
import dagger.hilt.android.qualifiers.ApplicationContext
import io.rami.screenrecorder.domain.model.Recording
import io.rami.screenrecorder.domain.model.RecordingId
import io.rami.screenrecorder.domain.model.TrashItem
import io.rami.screenrecorder.domain.repository.MediaLibraryRepository
import io.rami.screenrecorder.domain.usecase.DuplicateRecordingNameException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [MediaLibraryRepository]의 MediaStore 구현.
 *
 * 목록/휴지통 관찰과 이름 변경·휴지통 이동·복원·영구 삭제를 담당한다 (기능명세서 6.3, 7, 9절).
 */
@Singleton
class MediaStoreMediaLibraryRepository
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : MediaLibraryRepository {
        private val queries = MediaStoreVideoQueries(context)

        override fun observeRecordings(): Flow<List<Recording>> =
            callbackFlow {
                val resolver = context.contentResolver
                val observer =
                    object : ContentObserver(null) {
                        override fun onChange(selfChange: Boolean) {
                            launch { trySend(queries.queryRecordings()) }
                        }
                    }
                resolver.registerContentObserver(
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                    true,
                    observer,
                )
                trySend(queries.queryRecordings())
                awaitClose { resolver.unregisterContentObserver(observer) }
            }.flowOn(Dispatchers.IO)

        override suspend fun rename(
            id: RecordingId,
            newName: String,
        ) {
            withContext(Dispatchers.IO) {
                // 확장자는 고정이다 (기능명세서 6.3절: .mp4 수정 불가).
                val displayName = if (newName.endsWith(EXTENSION)) newName else newName + EXTENSION
                // 중복 이름이면 순번을 붙인 제안과 함께 거절한다 (기능명세서 6.3절).
                val existingNames =
                    queries
                        .queryRecordings()
                        .filterNot { it.id == id }
                        .map { it.displayName }
                        .toSet()
                if (displayName in existingNames) {
                    throw DuplicateRecordingNameException(
                        suggestNumberedName(displayName, existingNames),
                    )
                }
                val values =
                    ContentValues().apply {
                        put(MediaStore.Video.Media.DISPLAY_NAME, displayName)
                    }
                context.contentResolver.update(uriOf(id), values, null, null)
            }
        }

        override suspend fun moveToTrash(ids: List<RecordingId>) {
            setTrashed(ids, trashed = true)
        }

        override fun observeTrash(): Flow<List<TrashItem>> =
            callbackFlow {
                val resolver = context.contentResolver
                val observer =
                    object : ContentObserver(null) {
                        override fun onChange(selfChange: Boolean) {
                            launch { trySend(queries.queryTrash()) }
                        }
                    }
                resolver.registerContentObserver(
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                    true,
                    observer,
                )
                trySend(queries.queryTrash())
                awaitClose { resolver.unregisterContentObserver(observer) }
            }.flowOn(Dispatchers.IO)

        override suspend fun restore(ids: List<RecordingId>) {
            setTrashed(ids, trashed = false)
        }

        override suspend fun permanentlyDelete(ids: List<RecordingId>) {
            withContext(Dispatchers.IO) {
                ids.forEach { id -> context.contentResolver.delete(uriOf(id), null, null) }
            }
        }

        /** 자기 앱이 만든 파일은 승인 다이얼로그 없이 IS_TRASHED를 직접 갱신할 수 있다 (기능명세서 9절). */
        private suspend fun setTrashed(
            ids: List<RecordingId>,
            trashed: Boolean,
        ) {
            withContext(Dispatchers.IO) {
                val values =
                    ContentValues().apply {
                        put(MediaStore.MediaColumns.IS_TRASHED, if (trashed) 1 else 0)
                    }
                ids.forEach { id -> context.contentResolver.update(uriOf(id), values, null, null) }
            }
        }

        private fun uriOf(id: RecordingId) =
            android.content.ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id.value)

        /** "이름_1.mp4" 형식으로 비어 있는 첫 순번 이름을 제안한다. */
        private fun suggestNumberedName(
            displayName: String,
            existingNames: Set<String>,
        ): String {
            val base = displayName.removeSuffix(EXTENSION)
            return generateSequence(1) { it + 1 }
                .map { sequenceNumber -> "${base}_$sequenceNumber$EXTENSION" }
                .first { it !in existingNames }
                .removeSuffix(EXTENSION)
        }

        private companion object {
            const val EXTENSION = ".mp4"
        }
    }
