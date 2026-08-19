package io.rami.screenrecorder.domain.usecase

import io.mockk.coVerify
import io.mockk.mockk
import io.rami.screenrecorder.domain.model.RecordingId
import io.rami.screenrecorder.domain.repository.MediaLibraryRepository
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RecordingTrashUseCaseTest {
    private val libraryRepository = mockk<MediaLibraryRepository>(relaxed = true)
    private val ids = listOf(RecordingId(1), RecordingId(2))

    @Test
    fun `선택 항목을 휴지통으로 이동한다`() =
        runTest {
            val result = MoveToTrashUseCase(libraryRepository)(ids)

            assertTrue(result.isSuccess)
            coVerify(exactly = 1) { libraryRepository.moveToTrash(ids) }
        }

    @Test
    fun `휴지통 항목을 복원한다`() =
        runTest {
            val result = RestoreRecordingUseCase(libraryRepository)(ids)

            assertTrue(result.isSuccess)
            coVerify(exactly = 1) { libraryRepository.restore(ids) }
        }

    @Test
    fun `휴지통 항목을 영구 삭제한다`() =
        runTest {
            val result = PermanentlyDeleteUseCase(libraryRepository)(ids)

            assertTrue(result.isSuccess)
            coVerify(exactly = 1) { libraryRepository.permanentlyDelete(ids) }
        }

    @Test
    fun `빈 선택 목록은 저장소 호출 없이 거부한다`() =
        runTest {
            val result = MoveToTrashUseCase(libraryRepository)(emptyList())

            assertTrue(result.isFailure)
            coVerify(exactly = 0) { libraryRepository.moveToTrash(any()) }
        }
}
