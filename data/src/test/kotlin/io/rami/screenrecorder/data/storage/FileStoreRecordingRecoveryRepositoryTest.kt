package io.rami.screenrecorder.data.storage

import io.rami.screenrecorder.data.recorder.RecordingFileStore
import io.rami.screenrecorder.domain.model.Recording
import io.rami.screenrecorder.domain.model.RecordingId
import io.rami.screenrecorder.domain.model.Resolution
import io.rami.screenrecorder.domain.model.VideoCodec
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
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

        /** 설정하면 발행이 여기서 멈춘다 — 그 사이 호출자를 취소해 볼 수 있다. */
        var publishGate: CompletableDeferred<Unit>? = null

        override fun createTempFile(fileName: String): File = File(tempDir, fileName)

        override fun listTempFiles(): List<File> =
            tempDir.listFiles()?.filter { it.isFile }?.sortedBy { it.name } ?: emptyList()

        override suspend fun existingFileNames(): Set<String> = emptySet()

        override suspend fun discardAbandonedPublishes(): Int = 0

        override suspend fun publish(
            tempFile: File,
            fileName: String,
        ): Recording? {
            publishGate?.await()
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

    /**
     * 복구 발행은 화면을 벗어나도 끝까지 간다 (기능명세서 6.1절 [결정]).
     *
     * 발행은 2~4분 걸린다. 화면의 viewModelScope 에서 돌면 사용자가 홈을 벗어나는 순간
     * remux 도중에 취소되고, 아무 안내 없이 실패한다.
     */
    @Test
    fun `호출자가 취소돼도 복구 발행은 끝난다`() =
        runTest {
            val fileStore = FakeFileStore()
            File(tempDir, "t.mp4").writeText("녹화")
            val gate = CompletableDeferred<Unit>()
            fileStore.publishGate = gate
            val repository = FileStoreRecordingRecoveryRepository(fileStore)

            val caller = async { repository.recover("t.mp4") }
            testScheduler.advanceUntilIdle()
            caller.cancel()
            gate.complete(Unit)
            testScheduler.advanceUntilIdle()

            assertEquals(listOf("t.mp4"), fileStore.published)
        }

    @Test
    fun `호출자가 취소돼도 삭제는 끝난다`() =
        runTest {
            val fileStore = FakeFileStore()
            val file = File(tempDir, "t.mp4").apply { writeText("녹화") }
            val repository = FileStoreRecordingRecoveryRepository(fileStore)

            val caller = async { repository.discard("t.mp4") }
            caller.cancel()
            testScheduler.advanceUntilIdle()

            assertFalse(file.exists())
        }
}
