package io.rami.screenrecorder.data.transcode

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.provider.MediaStore
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
    override suspend fun doWork(): Result {
        val recordingId = inputData.getLong(KEY_RECORDING_ID, -1L)
        val preset = CompressionPreset.valueOf(checkNotNull(inputData.getString(KEY_PRESET)))
        val metadata = queryVideo(recordingId) ?: return Result.failure()
        val plan = preset.plan(metadata.source)
        val outputName = CompressionPreset.compressedFileName(metadata.displayName)
        val tempFile = File(applicationContext.cacheDir, "transcode_$outputName")
        return try {
            transform(metadata.uriString, plan, tempFile)
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
                        }
                        delay(PROGRESS_POLL_MS)
                    }
                }
            try {
                awaitCompletion(transformer, sourceUri, plan, outputFile)
            } finally {
                progressJob.cancel()
            }
        }
    }

    private suspend fun awaitCompletion(
        transformer: Transformer,
        sourceUri: String,
        plan: CompressionPlan,
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
        transformer.start(editedItem(sourceUri, plan), outputFile.absolutePath)
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
    ): EditedMediaItem {
        val builder = EditedMediaItem.Builder(MediaItem.fromUri(sourceUri))
        // 짧은 변 기준 다운스케일 (최대 압축 프리셋)
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
        return builder.build()
    }

    /** 압축 결과를 원본과 같은 컬렉션(Movies/ScreenRecorder)에 등록한다 (명세 8절: 원본 보존). */
    private fun publish(
        tempFile: File,
        displayName: String,
    ) {
        val resolver = applicationContext.contentResolver
        val values =
            ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, displayName)
                put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                put(MediaStore.Video.Media.RELATIVE_PATH, RELATIVE_PATH)
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }
        val uri =
            checkNotNull(resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)) {
                "MediaStore insert 실패"
            }
        resolver.openOutputStream(uri)?.use { output ->
            tempFile.inputStream().use { input -> input.copyTo(output) }
        }
        resolver.update(
            uri,
            ContentValues().apply { put(MediaStore.Video.Media.IS_PENDING, 0) },
            null,
            null,
        )
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
                    bitrateBps = probeBitrate(uriString, sizeBytes, durationMs),
                    codec = VideoCodec.H264,
                ),
        )
    }

    /**
     * 실제 비트레이트를 컨테이너에서 읽는다. fMP4는 MediaStore duration이 0이므로
     * (ADR-0001) retriever가 단일 진실 공급원이고, 크기/시간 추정은 최후 폴백이다.
     */
    private fun probeBitrate(
        uriString: String,
        sizeBytes: Long,
        mediaStoreDurationMs: Long,
    ): Int {
        val retriever = android.media.MediaMetadataRetriever()
        try {
            retriever.setDataSource(applicationContext, android.net.Uri.parse(uriString))
            retriever
                .extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_BITRATE)
                ?.toIntOrNull()
                ?.takeIf { it > 0 }
                ?.let { return it }
            val durationMs =
                retriever
                    .extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)
                    ?.toLongOrNull()
                    ?.takeIf { it > 0 } ?: mediaStoreDurationMs
            return estimateBitrate(sizeBytes, durationMs)
        } finally {
            retriever.release()
        }
    }

    companion object {
        const val KEY_RECORDING_ID = "recording_id"
        const val KEY_PRESET = "preset"
        const val KEY_PROGRESS = "progress"
        const val KEY_OUTPUT_NAME = "output_name"
        private const val PROGRESS_POLL_MS = 500L
        private const val RELATIVE_PATH = "Movies/ScreenRecorder"
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
