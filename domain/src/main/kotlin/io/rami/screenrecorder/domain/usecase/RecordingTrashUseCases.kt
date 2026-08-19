package io.rami.screenrecorder.domain.usecase

import io.rami.screenrecorder.domain.model.RecordingId
import io.rami.screenrecorder.domain.repository.MediaLibraryRepository
import javax.inject.Inject

/** 대상이 비어 있는 휴지통 작업 요청. */
class EmptySelectionException : Exception("선택된 항목이 없다")

private suspend fun requireSelection(
    ids: List<RecordingId>,
    action: suspend (List<RecordingId>) -> Unit,
): Result<Unit> {
    if (ids.isEmpty()) return Result.failure(EmptySelectionException())
    return runCatching { action(ids) }
}

/** 선택 항목을 휴지통으로 이동한다 (기능명세서 7.3절: 확인 다이얼로그는 UI 책임). */
class MoveToTrashUseCase
    @Inject
    constructor(
        private val libraryRepository: MediaLibraryRepository,
    ) {
        /** [ids]를 휴지통으로 이동한다. 빈 목록은 거부한다. */
        suspend operator fun invoke(ids: List<RecordingId>): Result<Unit> =
            requireSelection(ids) { libraryRepository.moveToTrash(it) }
    }

/** 휴지통 항목을 복원한다 (기능명세서 9절). */
class RestoreRecordingUseCase
    @Inject
    constructor(
        private val libraryRepository: MediaLibraryRepository,
    ) {
        /** [ids]를 원래 위치로 복원한다. 빈 목록은 거부한다. */
        suspend operator fun invoke(ids: List<RecordingId>): Result<Unit> =
            requireSelection(ids) { libraryRepository.restore(it) }
    }

/** 휴지통 항목을 영구 삭제한다 (기능명세서 9절: 확인 다이얼로그는 UI 책임). */
class PermanentlyDeleteUseCase
    @Inject
    constructor(
        private val libraryRepository: MediaLibraryRepository,
    ) {
        /** [ids]를 영구 삭제한다. 빈 목록은 거부한다. */
        suspend operator fun invoke(ids: List<RecordingId>): Result<Unit> =
            requireSelection(ids) { libraryRepository.permanentlyDelete(it) }
    }
