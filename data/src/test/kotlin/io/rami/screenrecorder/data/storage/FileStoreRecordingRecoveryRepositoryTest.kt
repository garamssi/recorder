package io.rami.screenrecorder.data.storage

import io.rami.screenrecorder.data.recorder.RecordingFileStore
import io.rami.screenrecorder.domain.model.Recording
import io.rami.screenrecorder.domain.model.RecordingId
import io.rami.screenrecorder.domain.model.Resolution
import io.rami.screenrecorder.domain.model.VideoCodec
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.time.Duration.Companion.seconds

/**
 * 복구 저장소는 [RecordingFileStore] 인터페이스에만 의존하므로,
 * 실제 임시 파일 디렉터리를 쓰는 페이크로 JVM에서 검증한다.
 */
class FileStoreRecordingRecoveryRepositoryTest {
    @TempDir
    lateinit var tempDir: File

    /** 실제 파일을 다루는 페이크 파일 스토어. */
    private inner class FakeFileStore : RecordingFileStore {
        var publishReturnsNull = false
        val published = mutableListOf<String>()

        override fun createTempFile(fileName: String): File = File(tempDir, fileName)

        override fun listTempFiles(): List<File> =
            tempDir.listFiles()?.filter { it.isFile }?.sortedBy { it.name } ?: emptyList()

        override suspend fun existingFileNames(): Set<String> = emptySet()

        override suspend fun discardAbandonedPublishes(): Int = 0

        override suspend fun publish(
            tempFile: File,
            fileName: String,
        ): Recording? {
            if (publishReturnsNull) return null
            published += fileName
            return Recording(
                id = RecordingId(1),
                displayName = fileName,
                contentUri = "content://media/1",
                sizeBytes = tempFile.length(),
                duration = 1.seconds,
                resolution = Resolution.FHD,
                frameRate = 60,
                codec = VideoCodec.H264,
                createdAtEpochMillis = 0,
                bitrateBps = null,
            )
        }
    }

    private val fileStore = FakeFileStore()
    private val repository = FileStoreRecordingRecoveryRepository(fileStore)

    private fun writeTempFile(
        name: String,
        bytes: Int,
    ) = File(tempDir, name).apply { writeBytes(ByteArray(bytes)) }

    @Test
    fun `임시 파일이 없으면 빈 목록을 반환한다`() =
        runTest {
            assertEquals(emptyList<Any>(), repository.pendingRecoveries())
        }

    @Test
    fun `남은 임시 파일을 이름 순으로 복구 대기 목록에 담는다`() =
        runTest {
            writeTempFile("b.mp4", 200)
            writeTempFile("a.mp4", 100)

            val pending = repository.pendingRecoveries()

            assertEquals(listOf("a.mp4", "b.mp4"), pending.map { it.id })
            assertEquals(100L, pending.first().sizeBytes)
        }

    @Test
    fun `복구는 해당 임시 파일을 파일 스토어로 발행한다`() =
        runTest {
            writeTempFile("a.mp4", 100)

            val recording = repository.recover("a.mp4")

            assertEquals("a.mp4", recording?.displayName)
            assertEquals(listOf("a.mp4"), fileStore.published)
        }

    @Test
    fun `재생 가능한 내용이 없으면 복구는 null을 반환한다`() =
        runTest {
            writeTempFile("empty.mp4", 0)
            fileStore.publishReturnsNull = true

            assertNull(repository.recover("empty.mp4"))
        }

    @Test
    fun `없는 id 복구는 null을 반환한다`() =
        runTest {
            assertNull(repository.recover("missing.mp4"))
        }

    @Test
    fun `삭제는 임시 파일을 지운다`() =
        runTest {
            val file = writeTempFile("a.mp4", 100)
            assertTrue(file.exists())

            repository.discard("a.mp4")

            assertFalse(file.exists())
        }

    @Test
    fun `이미 없는 id 삭제는 아무 일도 하지 않는다`() =
        runTest {
            repository.discard("missing.mp4") // 예외 없이 no-op
        }
}
