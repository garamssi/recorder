package io.rami.screenrecorder.data.storage

import io.rami.screenrecorder.data.recorder.RecordingFileStore
import io.rami.screenrecorder.domain.model.PendingRecovery
import io.rami.screenrecorder.domain.model.Recording
import io.rami.screenrecorder.domain.repository.RecordingRecoveryRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [RecordingRecoveryRepository]의 파일 시스템 구현 (기능명세서 6.1절).
 *
 * 앱 전용 캐시의 임시 파일을 [RecordingFileStore]로 발행하거나 삭제한다.
 * 정상 종료 시 임시 파일은 이미 발행/삭제되므로, 남아 있는 파일은 크래시 잔여물이다.
 */
@Singleton
class FileStoreRecordingRecoveryRepository
    @Inject
    constructor(
        private val fileStore: RecordingFileStore,
    ) : RecordingRecoveryRepository {
        override suspend fun pendingRecoveries(): List<PendingRecovery> =
            withContext(Dispatchers.IO) {
                fileStore.listTempFiles().map { file ->
                    PendingRecovery(
                        id = file.name,
                        displayName = file.name,
                        sizeBytes = file.length(),
                    )
                }
            }

        override suspend fun recover(id: String): Recording? =
            withContext(Dispatchers.IO) {
                val tempFile = fileStore.listTempFiles().firstOrNull { it.name == id } ?: return@withContext null
                fileStore.publish(tempFile, id)
            }

        override suspend fun discard(id: String) {
            withContext(Dispatchers.IO) {
                fileStore.listTempFiles().firstOrNull { it.name == id }?.delete()
            }
        }
    }
