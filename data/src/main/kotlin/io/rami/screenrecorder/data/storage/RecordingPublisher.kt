package io.rami.screenrecorder.data.storage

import io.rami.screenrecorder.domain.model.Recording
import io.rami.screenrecorder.domain.model.RecordingId
import io.rami.screenrecorder.domain.model.Resolution
import io.rami.screenrecorder.domain.model.VideoCodec
import java.io.File
import kotlin.time.Duration.Companion.milliseconds

/**
 * 발행 자리 하나 — MediaStore 가 내준 미완성(IS_PENDING) 레코드.
 *
 * 플랫폼 타입(`android.net.Uri`)을 담지 않아 [RecordingPublisher] 가 순수 JVM 으로 남는다.
 *
 * [id] 는 [uri] 에서 뽑아낼 수 있지만 그 파생이 `ContentUris.parseId` (Android API)라
 * 일부러 따로 담는다. 중복으로 보고 지우면 정책 쪽이 다시 플랫폼에 묶인다.
 */
internal data class PublishSlot(
    val id: Long,
    val uri: String,
)

/**
 * 녹화본을 최종 위치로 옮기는 플랫폼 경계 (기능명세서 6.1절).
 *
 * MediaStore·MediaMuxer·MediaExtractor 호출을 전부 이 뒤로 격리한다 (CLAUDE.md 3절).
 */
internal interface PublishTarget {
    /** [fileName] 자리를 IS_PENDING 으로 만든다. */
    fun create(fileName: String): PublishSlot

    fun write(
        slot: PublishSlot,
        tempFile: File,
    )

    /** IS_PENDING 을 해제해 사용자에게 보이게 한다. */
    fun finish(slot: PublishSlot)

    /** 미완성 자리를 지운다. */
    fun discard(slot: PublishSlot)
}

/** 임시 파일에서 읽어 낸 녹화본 정보. 재생 가능한 트랙이 없으면 null 로 표현한다. */
internal data class RecordingMetadata(
    val sizeBytes: Long,
    val durationMs: Long,
    val resolution: Resolution,
    val frameRate: Int,
    val codec: VideoCodec,
    val bitrateBps: Int?,
)

/** 임시 파일의 메타데이터를 읽는 경계. 읽을 수 없거나 비디오 트랙이 없으면 null. */
internal fun interface RecordingMetadataReader {
    fun read(file: File): RecordingMetadata?
}

/**
 * 발행 순서와 실패 처리 정책 (기능명세서 6.1절).
 *
 * "무엇을 어떤 순서로 하고, 실패하면 무엇을 남기고 무엇을 지우는가"만 담는다. 플랫폼 호출은
 * [PublishTarget]·[RecordingMetadataReader] 뒤에 있으므로 이 클래스는 순수 JVM 테스트 대상이다.
 */
internal class RecordingPublisher(
    private val target: PublishTarget,
    private val metadataReader: RecordingMetadataReader,
    private val nowEpochMillis: () -> Long = System::currentTimeMillis,
    private val onPhaseMeasured: (phase: String, millis: Long) -> Unit = { _, _ -> },
) {
    /**
     * [tempFile] 을 [fileName] 으로 발행한다.
     *
     * @return 저장된 녹화본. 저장할 내용이 없으면 null (오류가 아니다).
     */
    fun publish(
        tempFile: File,
        fileName: String,
    ): Recording? {
        // 프레임이 인코딩되기 전에 중지되면 빈/재생 불가 파일이 남는다.
        // 저장할 내용이 없으므로 임시 파일만 정리하고 null 을 반환한다 (오류 아님).
        val metadata =
            measure(PHASE_READ_METADATA) { metadataReader.read(tempFile) } ?: run {
                tempFile.delete()
                return null
            }
        val slot = target.create(fileName)
        try {
            measure(PHASE_WRITE) { target.write(slot, tempFile) }
            target.finish(slot)
        } catch (
            // 복사 실패 시 IS_PENDING 고아 레코드가 남지 않도록 정리 후 원인을 그대로 전파한다.
            // 임시 파일은 남긴다 — 저장 공간 부족이면 원본도 사본도 없어진다 (명세 6.1절 [결정]).
            @Suppress("TooGenericExceptionCaught") publishFailure: Exception,
        ) {
            target.discard(slot)
            throw publishFailure
        }
        tempFile.delete()
        return metadata.toRecording(slot, fileName)
    }

    /**
     * [block] 을 재고 결과를 그대로 돌려준다.
     *
     * 발행이 왜 2~4분씩 걸리는지는 단계를 갈라 재야 알 수 있다 (CLAUDE.md 8절: 실기기 측정).
     * 실패해도 잰 값을 흘려보낸다 — 실패까지 걸린 시간도 노출 구간이다.
     */
    private inline fun <T> measure(
        phase: String,
        block: () -> T,
    ): T {
        val startedAt = System.nanoTime()
        try {
            return block()
        } finally {
            onPhaseMeasured(phase, (System.nanoTime() - startedAt) / NANOS_PER_MILLI)
        }
    }

    private fun RecordingMetadata.toRecording(
        slot: PublishSlot,
        fileName: String,
    ): Recording =
        Recording(
            id = RecordingId(slot.id),
            displayName = fileName,
            contentUri = slot.uri,
            sizeBytes = sizeBytes,
            duration = durationMs.milliseconds,
            resolution = resolution,
            frameRate = frameRate,
            codec = codec,
            createdAtEpochMillis = nowEpochMillis(),
            bitrateBps = bitrateBps,
        )

    companion object {
        /** 임시 파일 전체 스캔으로 의심되는 첫 패스. */
        const val PHASE_READ_METADATA = "readMetadata"

        /** remux(+실패 시 원본 전량 복사) 두 번째 패스. */
        const val PHASE_WRITE = "write"

        private const val NANOS_PER_MILLI = 1_000_000L
    }
}
