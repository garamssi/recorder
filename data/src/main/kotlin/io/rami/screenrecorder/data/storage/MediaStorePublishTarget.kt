package io.rami.screenrecorder.data.storage

import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import io.rami.screenrecorder.domain.model.Resolution
import java.io.File
import javax.inject.Inject

// 녹화본 발행의 플랫폼 절반 (기능명세서 6.1절).
// 순서와 실패 처리 정책은 RecordingPublisher.kt 가 갖는다.

/** [PublishTarget] 의 MediaStore 구현. */
internal class MediaStorePublishTarget
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val ownSlots: OwnPublishSlots,
    ) : PublishTarget {
        override fun create(fileName: String): PublishSlot {
            val values =
                ContentValues().apply {
                    put(MediaStore.Video.Media.DISPLAY_NAME, fileName)
                    put(MediaStore.Video.Media.MIME_TYPE, MIME_TYPE)
                    put(MediaStore.Video.Media.RELATIVE_PATH, RECORDINGS_RELATIVE_PATH)
                    put(MediaStore.Video.Media.IS_PENDING, 1)
                }
            val uri =
                checkNotNull(
                    context.contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values),
                ) { "MediaStore insert 실패: $fileName" }
            return PublishSlot(id = ContentUris.parseId(uri), uri = uri.toString()).also {
                ownSlots.remember(it.id)
            }
        }

        /**
         * 임시 fMP4 를 표준 MP4 로 remux 해 MediaStore 에 쓴다 (ADR-0001 개정).
         *
         * remux 가 실패하면 원본을 그대로 복사한다 — 시크가 안 되더라도 녹화물을 잃는 것보다 낫다.
         */
        override fun write(
            slot: PublishSlot,
            tempFile: File,
        ) {
            val uri = slot.toUri()
            val resolver = context.contentResolver
            val descriptor =
                checkNotNull(resolver.openFileDescriptor(uri, FILE_MODE_READ_WRITE)) {
                    "MediaStore 파일 디스크립터 열기 실패: $uri"
                }
            val remuxed =
                descriptor.use {
                    @Suppress("TooGenericExceptionCaught") // 어떤 실패든 원본 복사로 되돌린다.
                    try {
                        Mp4Remuxer.remux(tempFile, it.fileDescriptor)
                        true
                    } catch (remuxFailure: Exception) {
                        Log.w(LOG_TAG, "remux 실패 — 원본 fMP4로 저장한다: ${tempFile.name}", remuxFailure)
                        false
                    }
                }
            if (remuxed) return
            val output =
                checkNotNull(resolver.openOutputStream(uri, FILE_MODE_TRUNCATE)) {
                    "MediaStore 쓰기 스트림 열기 실패: $uri"
                }
            output.use { tempFile.inputStream().use { input -> input.copyTo(it) } }
        }

        override fun finish(slot: PublishSlot) {
            val values = ContentValues().apply { put(MediaStore.Video.Media.IS_PENDING, 0) }
            context.contentResolver.update(slot.toUri(), values, null, null)
        }

        override fun discard(slot: PublishSlot) {
            context.contentResolver.delete(slot.toUri(), null, null)
        }

        /**
         * 우리 폴더의 미완성 레코드를 모은다.
         *
         * 미완성 항목은 만든 앱에만 보이므로 소유권을 따로 거를 필요가 없다. 그래도
         * `includePending` 을 명시한다 — 기본 동작에 기대면 플랫폼 버전에 따라 조용히 비게 된다.
         */
        override fun sizeOf(slot: PublishSlot): Long =
            context.contentResolver.openFileDescriptor(slot.toUri(), "r")?.use { it.statSize } ?: 0L

        override fun listPending(): List<PendingPublish> {
            val queryArgs =
                Bundle().apply {
                    putInt(MediaStore.QUERY_ARG_MATCH_PENDING, MediaStore.MATCH_INCLUDE)
                    putString(
                        ContentResolver.QUERY_ARG_SQL_SELECTION,
                        "${MediaStore.Video.Media.RELATIVE_PATH} = ? AND ${MediaStore.Video.Media.IS_PENDING} = 1",
                    )
                    putStringArray(
                        ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS,
                        arrayOf("$RECORDINGS_RELATIVE_PATH/"),
                    )
                }
            val pending = mutableListOf<PendingPublish>()
            context.contentResolver
                .query(
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                    arrayOf(MediaStore.Video.Media._ID, MediaStore.Video.Media.DATE_ADDED),
                    queryArgs,
                    null,
                )?.use { cursor ->
                    while (cursor.moveToNext()) {
                        val id = cursor.getLong(0)
                        val entryUri = ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id)
                        pending +=
                            PendingPublish(
                                slot = PublishSlot(id = id, uri = entryUri.toString()),
                                createdAtEpochSeconds = cursor.getLong(1),
                            )
                    }
                }
            return pending
        }

        override fun wasCreatedByThisProcess(slot: PublishSlot): Boolean = ownSlots.contains(slot.id)

        private fun PublishSlot.toUri(): Uri = Uri.parse(uri)
    }

/** [RecordingMetadataReader] 의 MediaMetadataRetriever 구현. */
internal class MediaMetadataRecordingReader
    @Inject
    constructor() : RecordingMetadataReader {
        override fun read(file: File): RecordingMetadataResult {
            if (file.length() == 0L) return RecordingMetadataResult.Empty
            return MediaMetadataRetriever().use { retriever ->
                val failure = retriever.tryOpen(file)
                when {
                    failure != null -> RecordingMetadataResult.Unreadable(failure)
                    !retriever.hasVideoTrack() -> RecordingMetadataResult.Empty
                    else -> RecordingMetadataResult.Readable(retriever.toMetadata(file))
                }
            }
        }

        /**
         * 데이터 소스를 열어 본다. 성공하면 null, 실패하면 그 원인.
         *
         * 실패를 "트랙 없음"으로 접지 않는다 — 접으면 발행 경로가 그 파일을 지운다.
         * remux 는 이 파서보다 관용적이라 살릴 수 있는 경우가 있다 (기능명세서 6.1절 [결정]).
         */
        @Suppress("TooGenericExceptionCaught") // setDataSource 는 손상 파일에 다양한 RuntimeException 을 던진다.
        private fun MediaMetadataRetriever.tryOpen(file: File): Throwable? =
            try {
                setDataSource(file.absolutePath)
                null
            } catch (unreadable: RuntimeException) {
                Log.w(LOG_TAG, "임시 파일을 읽지 못했다 — 발행 실패로 다룬다: ${file.name}", unreadable)
                unreadable
            }

        /** 재생 가능한 비디오 트랙이 있는지. 열기에 성공한 뒤에만 묻는다. */
        private fun MediaMetadataRetriever.hasVideoTrack(): Boolean =
            extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO) == "yes"

        private fun MediaMetadataRetriever.toMetadata(file: File): RecordingMetadata {
            val trackFormat = videoTrackFormatOf(file)
            return RecordingMetadata(
                sizeBytes = file.length(),
                durationMs = longMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION),
                resolution =
                    Resolution(
                        width =
                            longMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                                .toInt()
                                .coerceAtLeast(1),
                        height =
                            longMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                                .toInt()
                                .coerceAtLeast(1),
                    ),
                frameRate =
                    frameRateOf(
                        reportedFrameRate = trackFormat?.reportedFrameRate(),
                        // fMP4 트랙 포맷에 프레임레이트가 없을 수 있어 프레임 수로 되짚는다.
                        frameCount = longMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_FRAME_COUNT).toInt(),
                        durationMs = longMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION),
                    ),
                // 컨테이너 MIME(video/mp4)이 아니라 비디오 트랙의 코덱 MIME 이어야 한다.
                codec = videoCodecOf(trackFormat?.getString(MediaFormat.KEY_MIME)),
                bitrateBps =
                    longMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE).toInt().takeIf { it > 0 },
            )
        }

        private fun MediaMetadataRetriever.longMetadata(key: Int): Long = extractMetadata(key)?.toLongOrNull() ?: 0L

        /**
         * 비디오 트랙 포맷.
         *
         * MediaMetadataRetriever 의 MIME 은 컨테이너(video/mp4)라 코덱을 가릴 수 없고,
         * 프레임레이트 키는 카메라 전용이라 값이 없다. 트랙 포맷을 직접 봐야 둘 다 얻는다.
         *
         * 비용은 아직 재지 않았다. 발행 임계 경로에 있으므로 기기가 생기면 1시간짜리 fMP4 로
         * 측정해야 한다 (CLAUDE.md 8절).
         */
        @Suppress("TooGenericExceptionCaught") // 손상 파일에 다양한 RuntimeException 이 나온다.
        private fun videoTrackFormatOf(file: File): MediaFormat? {
            val extractor = MediaExtractor()
            return try {
                extractor.setDataSource(file.absolutePath)
                (0 until extractor.trackCount)
                    .asSequence()
                    .map(extractor::getTrackFormat)
                    .firstOrNull { it.getString(MediaFormat.KEY_MIME)?.startsWith("video/") == true }
            } catch (unreadable: RuntimeException) {
                Log.w(LOG_TAG, "트랙 포맷을 읽지 못했다: ${file.name}", unreadable)
                null
            } finally {
                extractor.release()
            }
        }

        /** 포맷이 알려 주는 프레임레이트. 없으면 null 이라 프레임 수로 되짚는다. */
        private fun MediaFormat.reportedFrameRate(): Int? =
            if (containsKey(MediaFormat.KEY_FRAME_RATE)) getInteger(MediaFormat.KEY_FRAME_RATE) else null
    }

private const val MIME_TYPE = "video/mp4"
private const val LOG_TAG = "RecordingFileStore"

/** MediaMuxer 는 탐색 가능한 디스크립터가 필요하다 (쓰기 전용 "w"로는 안 된다). */
private const val FILE_MODE_READ_WRITE = "rw"

/** remux 실패 후 재작성할 때 이전 내용을 남기지 않는다. */
private const val FILE_MODE_TRUNCATE = "wt"
