package io.rami.screenrecorder.data.storage

import io.rami.screenrecorder.data.recorder.RecordingFileStore
import io.rami.screenrecorder.domain.model.PendingRecovery
import io.rami.screenrecorder.domain.model.Recording
import io.rami.screenrecorder.domain.repository.RecordingRecoveryRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/**
 * [RecordingRecoveryRepository]의 파일 시스템 구현 (기능명세서 6.1절).
 *
 * 앱 전용 캐시의 임시 파일을 [RecordingFileStore]로 발행하거나 삭제한다.
 * 정상 종료 시 임시 파일은 이미 발행/삭제되므로, 남아 있는 파일은 크래시 잔여물이다.
 *
 * 발행과 삭제는 [NonCancellable]로 감싼다 (기능명세서 6.1절 [결정]). 호출자가 화면의
 * `viewModelScope`라 사용자가 홈을 벗어나면 취소되는데, 발행은 2~4분 걸리므로 그 사이
 * 화면을 떠나는 것이 드물지 않다. 정상 발행이 코디네이터에서 쓰는 것과 같은 수단이다.
 */
class FileStoreRecordingRecoveryRepository(
    private val fileStore: RecordingFileStore,
    // 기본값을 두어 테스트가 시계를 쥘 수 있게 한다 (RecordingCoordinator 와 같은 관례).
    private val blockingDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : RecordingRecoveryRepository {
    override suspend fun pendingRecoveries(): List<PendingRecovery> =
        withContext(blockingDispatcher) {
            fileStore.listTempFiles().map { file ->
                PendingRecovery(
                    id = file.name,
                    displayName = file.name,
                    sizeBytes = file.length(),
                )
            }
        }

    override suspend fun recover(id: String): Recording? =
        withContext(blockingDispatcher + NonCancellable) {
            val tempFile = fileStore.listTempFiles().firstOrNull { it.name == id } ?: return@withContext null
            fileStore.publish(tempFile, id)
        }

    override suspend fun cleanUpAbandonedPublishes() {
        fileStore.discardAbandonedPublishes()
    }

    override suspend fun discard(id: String) {
        withContext(blockingDispatcher + NonCancellable) {
            val tempFile = fileStore.listTempFiles().firstOrNull { it.name == id } ?: return@withContext
            // 반환값을 버리면 삭제 실패가 성공으로 보고돼 사용자가 지웠다고 믿는다.
            check(tempFile.delete()) { "임시 파일을 지우지 못했다: $id" }
        }
    }
}
