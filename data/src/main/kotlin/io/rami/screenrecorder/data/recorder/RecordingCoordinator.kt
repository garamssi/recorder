package io.rami.screenrecorder.data.recorder

import io.rami.screenrecorder.domain.model.Recording
import io.rami.screenrecorder.domain.model.RecordingConfig
import io.rami.screenrecorder.domain.model.RecordingState
import io.rami.screenrecorder.domain.repository.RecordingSessionRepository
import io.rami.screenrecorder.domain.session.MonotonicClock
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * 녹화 세션 오케스트레이터 — [RecordingSessionRepository]의 구현.
 *
 * 캡처/인코더/먹서 어댑터를 조율하고 상태 전이, 카운트다운, 일시정지 PTS 보정,
 * 안전 마무리(시스템 중단 포함)를 담당한다 (기능명세서 3, 11절).
 */
class RecordingCoordinator(
    private val sessionFactory: RecorderSessionFactory,
    private val fileStore: RecordingFileStore,
    private val fileNameProvider: FileNameProvider,
    private val displayInfo: DisplayInfoProvider,
    private val clock: MonotonicClock,
    private val dispatcher: CoroutineDispatcher,
) : RecordingSessionRepository {
    private val mutableState = MutableStateFlow<RecordingState>(RecordingState.Idle)
    private val mutableCompleted = MutableSharedFlow<Recording>(extraBufferCapacity = 1)

    override val state: StateFlow<RecordingState> = mutableState

    override val completedRecordings: Flow<Recording> = mutableCompleted

    override suspend fun start(config: RecordingConfig) {
        TODO()
    }

    override fun skipCountdown() {
        TODO()
    }

    override suspend fun stop() {
        TODO()
    }

    override suspend fun pause() {
        TODO()
    }

    override suspend fun resume() {
        TODO()
    }
}
