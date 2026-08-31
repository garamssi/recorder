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

    /** 이 앱이 이 폴더에 만든 미완성(IS_PENDING) 레코드. */
    fun listPending(): List<PendingPublish>

    /**
     * [slot] 을 이 프로세스가 [create] 로 만들었는지.
     *
     * 정리는 시각으로 판정하는데 시계가 보정되면 기준선이 밀려 방금 만든 자리도 "프로세스보다
     * 먼저" 로 보인다. 그때 진행 중인 발행을 지우지 않으려면 이 사실이 필요하다.
     */
    fun wasCreatedByThisProcess(slot: PublishSlot): Boolean
}

/**
 * 자리를 만들고 [write] 로 채운 뒤 확정한다. 실패하면 미완성 자리를 지우고 원인을 전파한다.
 *
 * 녹화본과 압축 결과가 같은 규율을 쓰게 하는 지점이다 (기능명세서 6.1절 [결정]). 각자 발행
 * 코드를 갖고 있으면 한쪽만 고쳐진다 — 실제로 압축 워커는 스트림을 열지 못해도 확정해 버려
 * 0바이트 파일을 성공으로 발행하고 있었다.
 *
 * @return 확정된 자리.
 */
internal fun PublishTarget.publishing(
    fileName: String,
    write: (PublishSlot) -> Unit,
): PublishSlot {
    val slot = create(fileName)
    try {
        write(slot)
        finish(slot)
    } catch (
        @Suppress("TooGenericExceptionCaught") failure: Exception,
    ) {
        discard(slot)
        throw failure
    }
    return slot
}

/** 아직 확정되지 않은 발행 자리. 정리 대상 판정에 만들어진 시각이 필요하다. */
internal data class PendingPublish(
    val slot: PublishSlot,
    val createdAtEpochSeconds: Long,
)

/** 임시 파일에서 읽어 낸 녹화본 정보. 재생 가능한 트랙이 없으면 null 로 표현한다. */
internal data class RecordingMetadata(
    val sizeBytes: Long,
    val durationMs: Long,
    val resolution: Resolution,
    val frameRate: Int,
    val codec: VideoCodec,
    val bitrateBps: Int?,
)

/**
 * 임시 파일 판독 결과 (기능명세서 6.1절 [결정]).
 *
 * "읽지 못했다"와 "트랙이 없다"를 반드시 갈라야 한다. 판정에 쓰는 파서와 발행에 쓰는 파서는
 * 관용도가 다르므로, 판정이 실패한 파일도 remux 로는 살아날 수 있다. 하나로 접으면 그런
 * 녹화물을 지워 버린다.
 */
internal sealed interface RecordingMetadataResult {
    /** 정상적으로 읽었다. */
    data class Readable(
        val metadata: RecordingMetadata,
    ) : RecordingMetadataResult

    /** 재생 가능한 트랙이 없다고 확인했다 — 저장할 내용이 없다. */
    data object Empty : RecordingMetadataResult

    /** 읽지 못했다. 지우면 안 된다. */
    data class Unreadable(
        val cause: Throwable,
    ) : RecordingMetadataResult
}

/** 임시 파일의 메타데이터를 읽는 경계. */
internal fun interface RecordingMetadataReader {
    fun read(file: File): RecordingMetadataResult
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
        val metadata =
            when (val read = measure(PHASE_READ_METADATA) { metadataReader.read(tempFile) }) {
                is RecordingMetadataResult.Readable -> read.metadata
                // 프레임이 인코딩되기 전에 중지되면 빈 파일이 남는다. 저장할 내용이 없다.
                RecordingMetadataResult.Empty -> {
                    tempFile.delete()
                    return null
                }
                // 판독 실패는 발행 실패다 — 지우지 않는다 (기능명세서 6.1절 [결정]).
                is RecordingMetadataResult.Unreadable -> throw read.cause
            }
        // 실패하면 미완성 자리는 정리되고 원인이 올라온다. 임시 파일은 남는다 —
        // 저장 공간 부족이면 원본도 사본도 없어진다 (기능명세서 6.1절 [결정]).
        val slot = target.publishing(fileName) { measure(PHASE_WRITE) { target.write(it, tempFile) } }
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
