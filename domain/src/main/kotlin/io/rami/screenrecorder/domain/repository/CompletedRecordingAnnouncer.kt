package io.rami.screenrecorder.domain.repository

import io.rami.screenrecorder.domain.model.Recording
import kotlinx.coroutines.flow.StateFlow

/**
 * 홈이 아직 보여 주지 못한 저장 완료 공지 (기능명세서 2.1절 [결정]).
 *
 * [RecordingSessionRepository.completedRecordings] 가 지나가는 이벤트인 것과 달리, 이쪽은
 * 남아 있는 상태다. 완료 순간 홈이 화면에 없어도 — 플로팅 버블로 녹화를 시작하면 늘 그렇다 —
 * 사라지지 않고, 홈이 한 번 보여 준 뒤 [consumeCompletedRecording] 로 소모될 때까지 유지된다.
 *
 * 세션 제어와 분리한 이유: 이 공지는 "누가 아직 못 봤는가"에 대한 것이라 세션의 수명이 아니라
 * 화면의 가시성에 매인다.
 */
interface CompletedRecordingAnnouncer {
    /** 아직 보여 주지 못한 완료 녹화본. 없으면 null. */
    val pendingCompletedRecording: StateFlow<Recording?>

    /** 완료 표시를 소모한다. 홈이 실제로 보여 준 뒤에만 부른다. */
    fun consumeCompletedRecording()
}
