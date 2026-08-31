package io.rami.screenrecorder.domain.usecase

import io.rami.screenrecorder.domain.model.PendingRecovery
import io.rami.screenrecorder.domain.model.Recording
import io.rami.screenrecorder.domain.model.RecordingId
import io.rami.screenrecorder.domain.model.Resolution
import io.rami.screenrecorder.domain.model.VideoCodec
import io.rami.screenrecorder.domain.repository.RecordingRecoveryRepository
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.seconds

class RecoveryUseCasesTest {
    private class FakeRecoveryRepository : RecordingRecoveryRepository {
        var pending = listOf(PendingRecovery("a.mp4", "a.mp4", 100))
        val recovered = mutableListOf<String>()
        val discarded = mutableListOf<String>()
        var recoverResult: Recording? = SAMPLE

        override suspend fun pendingRecoveries(): List<PendingRecovery> = pending

        override suspend fun recover(id: String): Recording? {
            recovered += id
            return recoverResult
        }

        var cleanUpCount = 0

        override suspend fun cleanUpAbandonedPublishes() {
            cleanUpCount++
        }

        override suspend fun discard(id: String) {
            discarded += id
        }
    }

    private val repository = FakeRecoveryRepository()

    @Test
    fun `대기 중인 복구 목록을 반환한다`() =
        runTest {
            val result = GetPendingRecoveriesUseCase(repository)()

            assertEquals(repository.pending, result)
        }

    @Test
    fun `복구는 저장소에 위임하고 복구된 녹화본을 반환한다`() =
        runTest {
            val result = RecoverRecordingUseCase(repository)("a.mp4")

            assertEquals(listOf("a.mp4"), repository.recovered)
            assertEquals(SAMPLE, result)
        }

    @Test
    fun `재생 가능한 내용이 없으면 복구는 null을 반환한다`() =
        runTest {
            repository.recoverResult = null

            val result = RecoverRecordingUseCase(repository)("a.mp4")

            assertNull(result)
        }

    @Test
    fun `삭제는 저장소에 위임한다`() =
        runTest {
            DiscardRecoveryUseCase(repository)("a.mp4")

            assertTrue(repository.discarded.contains("a.mp4"))
        }

    private companion object {
        val SAMPLE =
            Recording(
                id = RecordingId(1),
                displayName = "a.mp4",
                contentUri = "content://media/1",
                sizeBytes = 100,
                duration = 1.seconds,
                resolution = Resolution.FHD,
                frameRate = 60,
                codec = VideoCodec.H264,
                createdAtEpochMillis = 0,
                bitrateBps = null,
            )
    }

    @Test
    fun `버려진 발행 정리를 저장소에 위임한다`() =
        runTest {
            CleanUpAbandonedPublishesUseCase(repository)()

            assertEquals(1, repository.cleanUpCount)
        }
}
