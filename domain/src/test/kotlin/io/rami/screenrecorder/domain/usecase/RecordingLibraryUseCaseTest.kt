package io.rami.screenrecorder.domain.usecase

import app.cash.turbine.test
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.rami.screenrecorder.domain.model.NameValidation
import io.rami.screenrecorder.domain.model.Recording
import io.rami.screenrecorder.domain.model.RecordingId
import io.rami.screenrecorder.domain.model.Resolution
import io.rami.screenrecorder.domain.model.SortOrder
import io.rami.screenrecorder.domain.model.VideoCodec
import io.rami.screenrecorder.domain.repository.MediaLibraryRepository
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.minutes

class RecordingLibraryUseCaseTest {
    private val libraryRepository = mockk<MediaLibraryRepository>(relaxed = true)

    private fun recording(
        name: String,
        createdAt: Long,
    ) = Recording(
        id = RecordingId(createdAt),
        displayName = name,
        contentUri = "content://media/$createdAt",
        sizeBytes = 100,
        duration = 1.minutes,
        resolution = Resolution.FHD,
        frameRate = 60,
        codec = VideoCodec.H264,
        createdAtEpochMillis = createdAt,
    )

    @Test
    fun `검색어와 정렬 기준을 적용해 목록을 내보낸다`() =
        runTest {
            val gameplay = recording("Game_play", createdAt = 1_000)
            val meeting = recording("Meeting", createdAt = 2_000)
            val gameIntro = recording("game_intro", createdAt = 3_000)
            every { libraryRepository.observeRecordings() } returns
                flowOf(listOf(gameplay, meeting, gameIntro))

            val useCase = GetRecordingsUseCase(libraryRepository)

            useCase(query = "game", sortOrder = SortOrder.NEWEST_FIRST).test {
                assertEquals(listOf(gameIntro, gameplay), awaitItem())
                awaitComplete()
            }
        }

    @Test
    fun `유효한 이름으로 변경을 요청한다`() =
        runTest {
            val useCase = RenameRecordingUseCase(libraryRepository)

            val result = useCase(RecordingId(1), newName = "새 이름")

            assertTrue(result.isSuccess)
            coVerify(exactly = 1) { libraryRepository.rename(RecordingId(1), "새 이름") }
        }

    @Test
    fun `유효하지 않은 이름은 저장소 호출 없이 거부한다`() =
        runTest {
            val useCase = RenameRecordingUseCase(libraryRepository)

            val result = useCase(RecordingId(1), newName = "이름/불가")

            val exception = result.exceptionOrNull() as InvalidRecordingNameException
            assertEquals(NameValidation.ForbiddenCharacter, exception.reason)
            coVerify(exactly = 0) { libraryRepository.rename(any(), any()) }
        }
}
