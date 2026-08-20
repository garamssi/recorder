package io.rami.screenrecorder.data.transcode

import android.content.Context
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import io.rami.screenrecorder.domain.model.CompressionPreset
import io.rami.screenrecorder.domain.model.RecordingId
import io.rami.screenrecorder.domain.model.TranscodeJob
import io.rami.screenrecorder.domain.model.TranscodeStatus
import io.rami.screenrecorder.domain.repository.TranscodeRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [TranscodeRepository]의 WorkManager 구현 (기능명세서 8절).
 *
 * 인코더 자원 경합을 피하려고 고유 작업 이름으로 동시에 하나만 실행한다.
 */
@Singleton
class WorkManagerTranscodeRepository
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : TranscodeRepository {
        private val workManager: WorkManager get() = WorkManager.getInstance(context)

        override fun observeJob(): Flow<TranscodeJob?> =
            workManager
                .getWorkInfosForUniqueWorkFlow(UNIQUE_WORK_NAME)
                .map { infos -> infos.firstOrNull()?.toJob() }

        override suspend fun enqueue(
            recordingId: RecordingId,
            preset: CompressionPreset,
        ) {
            val request =
                OneTimeWorkRequestBuilder<TranscodeWorker>()
                    .setInputData(
                        Data
                            .Builder()
                            .putLong(TranscodeWorker.KEY_RECORDING_ID, recordingId.value)
                            .putString(TranscodeWorker.KEY_PRESET, preset.name)
                            .build(),
                    ).addTag(PRESET_TAG_PREFIX + preset.name)
                    .addTag(RECORDING_TAG_PREFIX + recordingId.value)
                    .build()
            workManager.enqueueUniqueWork(UNIQUE_WORK_NAME, ExistingWorkPolicy.KEEP, request)
        }

        override suspend fun cancel() {
            workManager.cancelUniqueWork(UNIQUE_WORK_NAME)
        }

        private fun WorkInfo.toJob(): TranscodeJob? {
            val recordingId =
                RecordingId(
                    tags
                        .firstOrNull { it.startsWith(RECORDING_TAG_PREFIX) }
                        ?.removePrefix(RECORDING_TAG_PREFIX)
                        ?.toLongOrNull() ?: return null,
                )
            val presetName = tags.firstOrNull { it.startsWith(PRESET_TAG_PREFIX) }
            val status =
                when (state) {
                    WorkInfo.State.ENQUEUED, WorkInfo.State.RUNNING, WorkInfo.State.BLOCKED ->
                        TranscodeStatus.RUNNING

                    WorkInfo.State.SUCCEEDED -> TranscodeStatus.SUCCEEDED
                    WorkInfo.State.FAILED -> TranscodeStatus.FAILED
                    WorkInfo.State.CANCELLED -> TranscodeStatus.CANCELLED
                }
            return TranscodeJob(
                recordingId = recordingId,
                preset =
                    presetName
                        ?.removePrefix(PRESET_TAG_PREFIX)
                        ?.let(CompressionPreset::valueOf)
                        ?: CompressionPreset.STANDARD,
                progressPercent = progress.getInt(TranscodeWorker.KEY_PROGRESS, 0),
                status = status,
            )
        }

        private companion object {
            const val UNIQUE_WORK_NAME = "transcode"
            const val PRESET_TAG_PREFIX = "preset:"
            const val RECORDING_TAG_PREFIX = "recording:"
        }
    }
