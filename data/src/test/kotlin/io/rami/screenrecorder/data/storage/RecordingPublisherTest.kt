package io.rami.screenrecorder.data.storage

import io.rami.screenrecorder.domain.model.Resolution
import io.rami.screenrecorder.domain.model.VideoCodec
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.io.IOException
import kotlin.time.Duration.Companion.milliseconds

/**
 * 녹화본 발행의 순서와 실패 처리 (기능명세서 6.1절).
 *
 * 이 경로에는 오랫동안 테스트가 없었고, 그 사이 "발행이 실패해도 임시 파일을 지운다" 같은
 * 결함이 살아남았다 (docs/postmortem/2026-08-31-publish-sigkill.md). 플랫폼 호출을
 * [PublishTarget]·[RecordingMetadataReader] 뒤로 뺀 덕에 순수 JVM 으로 고정할 수 있다.
 */
class RecordingPublisherTest {
    @TempDir
    lateinit var tempDirectory: File

    private val target = CallLoggingPublishTarget()

    private var readResult: RecordingMetadataResult = RecordingMetadataResult.Readable(SAMPLE_METADATA)

    private fun publisher() =
        RecordingPublisher(
            target = target,
            metadataReader = { readResult },
            nowEpochMillis = { FIXED_NOW },
        )

    private fun tempFile(content: String = "녹화"): File = File(tempDirectory, "Rec.mp4").apply { writeText(content) }

    /** 호출 순서를 그대로 기록하고, 원하는 단계에서 실패시킬 수 있는 발행 대상. */
    private class CallLoggingPublishTarget : PublishTarget {
        val calls = mutableListOf<String>()
        var createdFor: String? = null
        var createFailure: Exception? = null
        var writeFailure: Exception? = null
        var finishFailure: Exception? = null
        var discardFailure: Exception? = null

        override fun create(fileName: String): PublishSlot {
            calls += "create"
            createdFor = fileName
            createFailure?.let { throw it }
            return PublishSlot(id = SLOT_ID, uri = SLOT_URI)
        }

        /** write 단계에서 흘려보낼 진행률. 되돌아가는 값을 넣어 걸러지는지도 본다. */
        var writeProgress: List<Float> = emptyList()

        override fun write(
            slot: PublishSlot,
            tempFile: File,
            onProgress: (Float) -> Unit,
        ) {
            calls += "write"
            writeProgress.forEach(onProgress)
            writeFailure?.let { throw it }
        }

        override fun finish(slot: PublishSlot) {
            calls += "finish"
            finishFailure?.let { throw it }
        }

        override fun discard(slot: PublishSlot) {
            calls += "discard"
            discardFailure?.let { throw it }
        }

        var publishedSize = 0L

        override fun sizeOf(slot: PublishSlot): Long = publishedSize

        override fun listPending(): List<PendingPublish> = emptyList()

        override fun wasCreatedByThisProcess(slot: PublishSlot): Boolean = slot.id == SLOT_ID
    }

    @Test
    fun `자리를 만들고 쓰고 확정하는 순서로 발행한다`() {
        publisher().publish(tempFile(), FILE_NAME)

        assertEquals(listOf("create", "write", "finish"), target.calls)
    }

    @Test
    fun `발행에 성공하면 임시 파일을 지운다`() {
        val file = tempFile()

        publisher().publish(file, FILE_NAME)

        assertFalse(file.exists())
    }

    @Test
    fun `발행 결과에 메타데이터와 발행된 자리를 담는다`() {
        val recording = requireNotNull(publisher().publish(tempFile(), FILE_NAME))

        assertEquals(SLOT_ID, recording.id.value)
        assertEquals(SLOT_URI, recording.contentUri)
        assertEquals(FILE_NAME, recording.displayName)
        assertEquals(SAMPLE_METADATA.durationMs.milliseconds, recording.duration)
        assertEquals(SAMPLE_METADATA.resolution, recording.resolution)
        assertEquals(FIXED_NOW, recording.createdAtEpochMillis)
        // 크기는 임시 파일이 아니라 발행된 파일에서 온다 (기능명세서 6.1절 [결정]).
        assertEquals(0L, recording.sizeBytes)
        assertEquals(SAMPLE_METADATA.frameRate, recording.frameRate)
        assertEquals(SAMPLE_METADATA.codec, recording.codec)
        assertEquals(SAMPLE_METADATA.bitrateBps, recording.bitrateBps)
    }

    @Test
    fun `재생 가능한 내용이 없으면 자리를 만들지 않고 임시 파일만 지운다`() {
        readResult = RecordingMetadataResult.Empty
        val file = tempFile()

        val recording = publisher().publish(file, FILE_NAME)

        assertNull(recording)
        assertFalse(file.exists())
        assertEquals(emptyList<String>(), target.calls)
    }

    @Test
    fun `쓰기에 실패하면 미완성 자리를 지우고 원인을 그대로 전파한다`() {
        target.writeFailure = IOException("저장 공간 부족")

        assertThrows<IOException> { publisher().publish(tempFile(), FILE_NAME) }

        assertEquals(listOf("create", "write", "discard"), target.calls)
    }

    /**
     * 발행에 실패하면 임시 파일을 남긴다 (기능명세서 6.1절 [결정]).
     *
     * 저장 공간이 부족하면 remux 도 원본 복사도 실패한다. 그때 임시 파일까지 지우면
     * 원본도 사본도 남지 않는다 — 1시간짜리 녹화가 통째로 사라진다.
     */
    @Test
    fun `발행에 실패하면 임시 파일을 남긴다`() {
        target.writeFailure = IOException("저장 공간 부족")
        val file = tempFile()

        assertThrows<IOException> { publisher().publish(file, FILE_NAME) }

        assertTrue(file.exists(), "다음 실행에서 복구할 수 있어야 한다")
    }

    @Test
    fun `확정에 실패해도 임시 파일을 남긴다`() {
        target.finishFailure = IOException("MediaStore update 실패")
        val file = tempFile()

        assertThrows<IOException> { publisher().publish(file, FILE_NAME) }

        assertTrue(file.exists())
    }

    /**
     * 확정(IS_PENDING 해제)은 쓰기와 같은 try 안에 있어야 한다.
     *
     * 이 범위가 이 경로에서 가장 깨지기 쉬운 부분이다. finish 를 try 밖으로 빼면 확정에
     * 실패했을 때 미완성 레코드가 그대로 남는데, 아무 테스트도 빨개지지 않는다.
     */
    @Test
    fun `확정에 실패해도 미완성 자리를 지우고 원인을 전파한다`() {
        target.finishFailure = IOException("MediaStore update 실패")

        assertThrows<IOException> { publisher().publish(tempFile(), FILE_NAME) }

        assertEquals(listOf("create", "write", "finish", "discard"), target.calls)
    }

    /** 자리를 못 만들면 아직 아무것도 안 썼으므로 임시 파일을 남긴다 — 유일하게 옳게 도는 실패 분기다. */
    @Test
    fun `자리를 만들지 못하면 임시 파일을 남긴다`() {
        target.createFailure = IllegalStateException("MediaStore insert 실패")
        val file = tempFile()

        assertThrows<IllegalStateException> { publisher().publish(file, FILE_NAME) }

        assertTrue(file.exists())
        assertEquals(listOf("create"), target.calls)
    }

    @Test
    fun `발행할 이름을 그대로 자리에 넘긴다`() {
        publisher().publish(tempFile(), FILE_NAME)

        assertEquals(FILE_NAME, target.createdFor)
    }

    /**
     * 현재 동작을 고정한다 — 정리에 실패하면 원래 원인이 통째로 가려진다.
     *
     * `addSuppressed` 가 없어 "왜 발행이 실패했는가"를 잃는다. 회귀는 아니지만(원본도 같았다)
     * 고칠 때 이 테스트가 뒤집혀야 한다.
     */
    @Test
    fun `현재는 정리에 실패하면 원래 원인이 가려진다`() {
        target.writeFailure = IOException("저장 공간 부족")
        target.discardFailure = IllegalStateException("정리 실패")

        val thrown = assertThrows<IllegalStateException> { publisher().publish(tempFile(), FILE_NAME) }

        assertEquals("정리 실패", thrown.message)
    }

    /**
     * 판독 실패를 "저장할 내용 없음" 으로 접지 않는다 (기능명세서 6.1절 [결정]).
     *
     * 판정에 쓰는 파서와 발행에 쓰는 파서는 관용도가 다르다. 꼬리가 잘린 fMP4 는 판정이
     * 실패해도 remux 로는 살아날 수 있는데, 접어 버리면 그 녹화물을 지워 버린다.
     */
    @Test
    fun `읽지 못한 파일은 지우지 않고 발행 실패로 다룬다`() {
        readResult = RecordingMetadataResult.Unreadable(IllegalStateException("파싱 실패"))
        val file = tempFile()

        assertThrows<IllegalStateException> { publisher().publish(file, FILE_NAME) }

        assertTrue(file.exists(), "remux 로 살릴 수 있을지 모른다")
        assertEquals(emptyList<String>(), target.calls)
    }

    @Test
    fun `메타데이터를 읽다 예외가 나도 임시 파일을 남긴다`() {
        val file = tempFile()
        val publisher =
            RecordingPublisher(
                target = target,
                metadataReader = { throw OutOfMemoryError("판독 중 메모리 부족") },
            )

        assertThrows<OutOfMemoryError> { publisher.publish(file, FILE_NAME) }

        assertTrue(file.exists())
    }

    @Test
    fun `정리까지 실패해도 임시 파일을 남긴다`() {
        target.writeFailure = IOException("저장 공간 부족")
        target.discardFailure = IllegalStateException("정리 실패")
        val file = tempFile()

        assertThrows<IllegalStateException> { publisher().publish(file, FILE_NAME) }

        assertTrue(file.exists())
    }

    /**
     * 크기는 발행된 파일에서 읽는다 (기능명세서 6.1절 [결정]).
     *
     * remux 는 컨테이너를 바꾸므로 임시 파일과 발행된 파일의 크기가 다르다. 임시 파일 크기를
     * 담으면 사용자가 보는 값이 틀린다.
     */
    @Test
    fun `발행 결과의 크기는 발행된 파일에서 읽는다`() {
        target.publishedSize = 999L

        val recording = requireNotNull(publisher().publish(tempFile(), FILE_NAME))

        assertEquals(999L, recording.sizeBytes)
    }

    /**
     * 저장 진행률 (기능명세서 2.1절 [결정]).
     *
     * 발행은 분 단위로 걸린다. 화면이 진척을 보여 주려면 이 경로가 진행률을 흘려보내야 하고,
     * 되돌아가거나 100% 에 못 미친 채 끝나서는 안 된다.
     */
    @Test
    fun `쓰기 진행률을 흘려보내고 발행이 끝나면 1f 로 닫는다`() {
        target.writeProgress = listOf(0.2f, 0.8f)
        val reported = mutableListOf<Float>()

        publisher().publish(tempFile(), FILE_NAME) { reported += it }

        assertEquals(listOf(0.2f, 0.8f, 1f), reported)
    }

    @Test
    fun `뒤로 가는 진행률은 걸러 낸다`() {
        // remux 가 중간에 실패하면 원본 전량 복사로 되돌아가 0 바이트부터 다시 센다.
        target.writeProgress = listOf(0.6f, 0.1f, 0.3f, 0.7f)
        val reported = mutableListOf<Float>()

        publisher().publish(tempFile(), FILE_NAME) { reported += it }

        assertEquals(listOf(0.6f, 0.7f, 1f), reported)
    }

    @Test
    fun `범위를 벗어난 진행률은 0f_1f 로 자른다`() {
        target.writeProgress = listOf(-0.5f, 1.9f)
        val reported = mutableListOf<Float>()

        publisher().publish(tempFile(), FILE_NAME) { reported += it }

        assertEquals(listOf(1f), reported)
    }

    @Test
    fun `저장할 내용이 없으면 진행률을 보고하지 않는다`() {
        readResult = RecordingMetadataResult.Empty
        val reported = mutableListOf<Float>()

        assertNull(publisher().publish(tempFile(), FILE_NAME) { reported += it })

        assertTrue(reported.isEmpty())
    }

    @Test
    fun `발행이 실패하면 1f 를 보고하지 않는다`() {
        target.writeProgress = listOf(0.4f)
        target.writeFailure = IOException("스트림을 열지 못했다")
        val reported = mutableListOf<Float>()

        assertThrows<IOException> { publisher().publish(tempFile(), FILE_NAME) { reported += it } }

        assertEquals(listOf(0.4f), reported)
    }

    private companion object {
        const val FILE_NAME = "Rec_20260831_135941.mp4"
        const val SLOT_ID = 42L
        const val SLOT_URI = "content://media/external/video/media/42"
        const val FIXED_NOW = 1_788_155_923_000L

        val SAMPLE_METADATA =
            RecordingMetadata(
                sizeBytes = 433_362_223L,
                durationMs = 3_428_134L,
                resolution = Resolution(1920, 1080),
                frameRate = 60,
                codec = VideoCodec.H264,
                bitrateBps = 12_000_000,
            )
    }
}
