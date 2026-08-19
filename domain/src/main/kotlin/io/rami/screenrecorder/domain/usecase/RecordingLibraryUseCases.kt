package io.rami.screenrecorder.domain.usecase

import io.rami.screenrecorder.domain.model.NameValidation
import io.rami.screenrecorder.domain.model.Recording
import io.rami.screenrecorder.domain.model.RecordingId
import io.rami.screenrecorder.domain.model.RecordingNameValidator
import io.rami.screenrecorder.domain.model.RecordingSearch
import io.rami.screenrecorder.domain.model.SortOrder
import io.rami.screenrecorder.domain.repository.MediaLibraryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/** 검색·정렬이 적용된 녹화 목록을 관찰한다 (기능명세서 7.1절). */
class GetRecordingsUseCase
    @Inject
    constructor(
        private val libraryRepository: MediaLibraryRepository,
    ) {
        /** [query] 부분 일치 검색과 [sortOrder] 정렬을 적용한 목록 스트림을 반환한다. */
        operator fun invoke(
            query: String,
            sortOrder: SortOrder,
        ): Flow<List<Recording>> =
            libraryRepository
                .observeRecordings()
                .map { recordings -> sortOrder.sort(RecordingSearch.filter(recordings, query)) }
    }

/** 녹화본 이름을 변경한다 (기능명세서 6.3절). */
class RenameRecordingUseCase
    @Inject
    constructor(
        private val libraryRepository: MediaLibraryRepository,
    ) {
        /** 유효성 검사 후 [newName]으로 변경한다. */
        suspend operator fun invoke(
            id: RecordingId,
            newName: String,
        ): Result<Unit> {
            val validation = RecordingNameValidator.validate(newName)
            if (validation != NameValidation.Valid) {
                return Result.failure(InvalidRecordingNameException(validation))
            }
            return runCatching { libraryRepository.rename(id, newName) }
        }
    }
