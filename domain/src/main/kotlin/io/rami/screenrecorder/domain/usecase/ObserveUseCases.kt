package io.rami.screenrecorder.domain.usecase

import io.rami.screenrecorder.domain.model.Recording
import io.rami.screenrecorder.domain.model.RecordingState
import io.rami.screenrecorder.domain.model.TrashItem
import io.rami.screenrecorder.domain.repository.MediaLibraryRepository
import io.rami.screenrecorder.domain.repository.RecordingSessionRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/** 녹화 세션 상태를 관찰한다. */
class ObserveRecordingStateUseCase
    @Inject
    constructor(
        private val sessionRepository: RecordingSessionRepository,
    ) {
        /** 현재 세션 상태 스트림. */
        operator fun invoke(): Flow<RecordingState> = sessionRepository.state
    }

/** 저장 완료 이벤트를 관찰한다 (기능명세서 6.2절 저장 직후 스낵바/이름 변경). */
class ObserveCompletedRecordingUseCase
    @Inject
    constructor(
        private val sessionRepository: RecordingSessionRepository,
    ) {
        /** 저장 완료된 녹화본 이벤트 스트림. */
        operator fun invoke(): Flow<Recording> = sessionRepository.completedRecordings
    }

/** 휴지통 목록을 관찰한다 (기능명세서 9절). */
class ObserveTrashUseCase
    @Inject
    constructor(
        private val libraryRepository: MediaLibraryRepository,
    ) {
        /** 휴지통 항목 스트림 (남은 보관일 포함). */
        operator fun invoke(): Flow<List<TrashItem>> = libraryRepository.observeTrash()
    }
