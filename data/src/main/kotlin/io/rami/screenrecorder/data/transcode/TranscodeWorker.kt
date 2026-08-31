package io.rami.screenrecorder.data.transcode

import android.content.ContentUris
import android.content.Context
import android.provider.MediaStore
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.effect.Presentation
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import androidx.media3.transformer.VideoEncoderSettings
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import io.rami.screenrecorder.data.storage.PublishTarget
import io.rami.screenrecorder.data.storage.publishing
import io.rami.screenrecorder.domain.model.CompressionPlan
import io.rami.screenrecorder.domain.model.CompressionPreset
import io.rami.screenrecorder.domain.model.CompressionSource
import io.rami.screenrecorder.domain.model.Resolution
import io.rami.screenrecorder.domain.model.VideoCodec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Media3 Transformer 트랜스코딩 워커 (기능명세서 8절).
 *
 * 원본은 보존하고 `원본이름_compressed.mp4`를 임시 파일로 만든 뒤 MediaStore에 등록한다.
 * 진행률은 setProgress로 보고하며, WorkManager 취소가 곧 작업 취소다.
 */
class TranscodeWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    private val probe = TranscodeMediaProbe(appContext)

    override suspend fun doWork(): Result {
        val recordingId = inputData.getLong(KEY_RECORDING_ID, -1L)
        val preset = CompressionPreset.valueOf(checkNotNull(inputData.getString(KEY_PRESET)))
        // 명세 8절: 백그라운드에서도 진행률 알림 + 취소 가능해야 한다 (검수 #3).
        setForeground(TranscodeNotifications.foregroundInfo(applicationContext, id, progressPercent = 0))
        val metadata = queryVideo(recordingId) ?: return Result.failure()
        val plan = preset.plan(metadata.source)
        val outputName = CompressionPreset.compressedFileName(metadata.displayName)
        val tempFile = File(applicationContext.cacheDir, "transcode_$outputName")
        return try {
            transform(metadata.uriString, plan, metadata.source.resolution, tempFile)
            publish(tempFile, outputName)
            Result.success(workDataOf(KEY_OUTPUT_NAME to outputName))
        } finally {
            tempFile.delete()
        }
    }

    /** Transformer는 메인 루퍼가 필요하다. 진행률은 폴링으로 setProgress에 반영한다. */
    private suspend fun transform(
        sourceUri: String,
        plan: CompressionPlan,
        sourceResolution: Resolution,
        outputFile: File,
    ) = withContext(Dispatchers.Main) {
        val transformer = buildTransformer(plan)
        coroutineScope {
            val progressJob =
                launch {
                    val holder = ProgressHolder()
                    while (true) {
                        if (transformer.getProgress(holder) == Transformer.PROGRESS_STATE_AVAILABLE) {
                            setProgress(workDataOf(KEY_PROGRESS to holder.progress))
                            setForeground(
                                TranscodeNotifications.foregroundInfo(applicationContext, id, holder.progress),
                            )
                        }
                        delay(PROGRESS_POLL_MS)
                    }
                }
            try {
                awaitCompletion(transformer, sourceUri, plan, sourceResolution, outputFile)
            } finally {
                progressJob.cancel()
            }
        }
    }

    private suspend fun awaitCompletion(
        transformer: Transformer,
        sourceUri: String,
        plan: CompressionPlan,
        sourceResolution: Resolution,
        outputFile: File,
    ) = suspendCancellableCoroutine { continuation ->
        transformer.addListener(
            object : Transformer.Listener {
                override fun onCompleted(
                    composition: Composition,
                    exportResult: ExportResult,
                ) {
                    continuation.resume(Unit)
                }

                override fun onError(
                    composition: Composition,
                    exportResult: ExportResult,
                    exportException: ExportException,
                ) {
                    continuation.resumeWithException(exportException)
                }
            },
        )
        transformer.start(editedItem(sourceUri, plan, sourceResolution), outputFile.absolutePath)
        continuation.invokeOnCancellation { transformer.cancel() }
    }

    private fun buildTransformer(plan: CompressionPlan): Transformer =
        Transformer
            .Builder(applicationContext)
            .setVideoMimeType(
                when (plan.targetCodec) {
                    VideoCodec.H264 -> MimeTypes.VIDEO_H264
                    VideoCodec.HEVC -> MimeTypes.VIDEO_H265
                },
            ).setEncoderFactory(
                androidx.media3.transformer
                    .DefaultEncoderFactory
                    .Builder(applicationContext)
                    .setRequestedVideoEncoderSettings(
                        VideoEncoderSettings
                            .Builder()
                            .setBitrate(plan.targetBitrateBps)
                            .build(),
                    ).build(),
            ).build()

    private fun editedItem(
        sourceUri: String,
        plan: CompressionPlan,
        sourceResolution: Resolution,
    ): EditedMediaItem {
        val builder = EditedMediaItem.Builder(MediaItem.fromUri(sourceUri))
        // 동일 해상도 프리셋에는 스케일 패스를 넣지 않는다 (검수 #2: 불필요한 리사이즈 방지)
        if (plan.targetResolution != sourceResolution) {
            builder.setEffects(
                androidx.media3.transformer.Effects(
                    emptyList(),
                    listOf(
                        Presentation.createForShortSide(
                            minOf(plan.targetResolution.width, plan.targetResolution.height),
                        ),
                    ),
                ),
            )
        }
        return builder.build()
    }

    /**
     * 압축 결과를 녹화본과 같은 발행 경로로 등록한다 (명세 8절: 원본 보존, 6.1절 [결정]).
     *
     * 자기 발행 코드를 갖고 있던 동안 두 결함이 있었다 — 스트림을 열지 못하면 복사를 건너뛰고도
     * IS_PENDING 을 해제해 0바이트 파일을 성공으로 발행했고, 복사가 실패하면 미완성 레코드를
     * 정리하지 않았다. 같은 경로를 쓰면 둘 다 구조적으로 사라지고, 이 결과도 "이 프로세스가
     * 만든 자리" 로 기억돼 고아 정리가 시각 폴백에 기대지 않는다.
     */
    private fun publish(
        tempFile: File,
        displayName: String,
    ) {
        val target = publishTarget()
        target.publishing(displayName) { slot ->
            val output =
                checkNotNull(applicationContext.contentResolver.openOutputStream(slot.uri.toUri())) {
                    "MediaStore 쓰기 스트림 열기 실패: $displayName"
                }
            output.use { tempFile.inputStream().use { input -> input.copyTo(it) } }
        }
    }

    /**
     * 발행 경계를 얻는다.
     *
     * 이 워커는 기본 WorkerFactory 가 만들어 생성자 주입을 받지 못한다. 자기 인스턴스를 새로
     * 만들면 "이 프로세스가 만든 자리" 기억이 갈라지므로 싱글턴을 그대로 꺼내 쓴다.
     */
    private fun publishTarget(): PublishTarget =
        EntryPointAccessors
            .fromApplication(applicationContext, TranscodePublishEntryPoint::class.java)
            .publishTarget()

    /** 기본 WorkerFactory 로 만들어지는 워커가 발행 경계에 닿기 위한 통로. */
    @EntryPoint
    @InstallIn(SingletonComponent::class)
    internal interface TranscodePublishEntryPoint {
        fun publishTarget(): PublishTarget
    }

    private class VideoMetadata(
        val uriString: String,
        val displayName: String,
        val source: CompressionSource,
    )

    private fun queryVideo(recordingId: Long): VideoMetadata? {
        val uri = ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, recordingId)
        val projection =
            arrayOf(
                MediaStore.Video.Media.DISPLAY_NAME,
                MediaStore.Video.Media.WIDTH,
                MediaStore.Video.Media.HEIGHT,
                MediaStore.Video.Media.SIZE,
                MediaStore.Video.Media.DURATION,
            )
        return applicationContext.contentResolver
            .query(uri, projection, null, null, null)
            ?.use { cursor -> if (cursor.moveToFirst()) readMetadata(cursor, uri.toString()) else null }
    }

    private fun readMetadata(
        cursor: android.database.Cursor,
        uriString: String,
    ): VideoMetadata {
        val name = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME))
        val width =
            cursor.getInt(cursor.getColumnIndexOrThrow(MediaStore.Video.Media.WIDTH)).coerceAtLeast(1)
        val height =
            cursor.getInt(cursor.getColumnIndexOrThrow(MediaStore.Video.Media.HEIGHT)).coerceAtLeast(1)
        val sizeBytes = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE))
        val durationMs = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION))
        return VideoMetadata(
            uriString = uriString,
            displayName = name,
            source =
                CompressionSource(
                    resolution = Resolution(width, height),
                    bitrateBps = probe.probeBitrate(uriString, sizeBytes, durationMs),
                    codec = probe.probeCodec(uriString),
                ),
        )
    }

    companion object {
        const val KEY_RECORDING_ID = "recording_id"
        const val KEY_PRESET = "preset"
        const val KEY_PROGRESS = "progress"
        const val KEY_OUTPUT_NAME = "output_name"
        private const val PROGRESS_POLL_MS = 500L
        private const val BITS_PER_BYTE = 8
        private const val MS_PER_SECOND = 1_000L

        /** 파일 크기/재생시간으로 전체 비트레이트를 추정한다 (컨테이너 오버헤드 포함, 근사값). */
        fun estimateBitrate(
            sizeBytes: Long,
            durationMs: Long,
        ): Int {
            if (durationMs <= 0) return DEFAULT_BITRATE_BPS
            return (sizeBytes * BITS_PER_BYTE * MS_PER_SECOND / durationMs).toInt()
        }

        private const val DEFAULT_BITRATE_BPS = 8_000_000
    }
}

private fun workDataOf(vararg pairs: Pair<String, Any>): androidx.work.Data =
    androidx.work.Data
        .Builder()
        .apply { pairs.forEach { (key, value) -> putValue(key, value) } }
        .build()

private fun androidx.work.Data.Builder.putValue(
    key: String,
    value: Any,
) {
    when (value) {
        is Int -> putInt(key, value)
        is Long -> putLong(key, value)
        is String -> putString(key, value)
        else -> error("지원하지 않는 Data 타입: ${value::class}")
    }
}
