package io.rami.screenrecorder.data.recorder

import io.rami.screenrecorder.domain.model.Recording
import io.rami.screenrecorder.domain.repository.CompletedRecordingAnnouncer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * 저장 완료 공지를 홈이 볼 때까지 들고 있는다 ([CompletedRecordingAnnouncer] 구현).
 *
 * 세션 오케스트레이션과 섞지 않으려고 따로 둔다 — 이 상태의 수명은 세션이 아니라
 * "홈이 아직 못 봤는가"가 정한다.
 */
class CompletedRecordingAnnouncement : CompletedRecordingAnnouncer {
    private val pending = MutableStateFlow<Recording?>(null)

    override val pendingCompletedRecording: StateFlow<Recording?> = pending

    override fun consumeCompletedRecording() = discard()

    /** 발행이 확정된 [recording] 을 공지로 올린다. */
    fun announce(recording: Recording) {
        pending.value = recording
    }

    /** 보여 주지 못한 공지를 버린다 (새 세션 시작 등). */
    fun discard() {
        pending.value = null
    }
}
