package io.rami.screenrecorder.data.storage

import android.content.Context
import android.os.StatFs
import dagger.hilt.android.qualifiers.ApplicationContext
import io.rami.screenrecorder.domain.repository.StorageRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.seconds

/** [StorageRepository]의 StatFs 구현. 내부 저장소(미디어 볼륨과 동일)의 여유 공간을 주기 조회한다. */
@Singleton
class DeviceStorageRepository
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : StorageRepository {
        override fun observeAvailableBytes(): Flow<Long> =
            flow {
                while (true) {
                    emit(StatFs(context.cacheDir.absolutePath).availableBytes)
                    kotlinx.coroutines.delay(POLL_INTERVAL)
                }
            }.flowOn(Dispatchers.IO)

        private companion object {
            val POLL_INTERVAL = 5.seconds
        }
    }
