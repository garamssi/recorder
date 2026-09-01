package io.rami.screenrecorder.domain.model

import kotlin.time.Duration

/** 녹화 세션의 상태 (기능명세서 2.2, 3, 11절). */
sealed interface RecordingState {
    /** 세션 없음. */
    data object Idle : RecordingState

    /**
     * 파이프라인 준비 중 — 인코더·먹서·캡처를 세운다 (기능명세서 6.1절 [결정]).
     *
     * 카운트다운이 끝난 뒤부터 첫 프레임을 받기 전까지의 구간이다. 세션이 아직 없어
     * 중지·일시정지가 아무 일도 하지 않으므로, 이 구간을 [Idle] 로 두면 버블과 홈이
     * "녹화 시작" 을 그대로 내놓는다. 카운트다운을 "없음" 으로 두면 [CountingDown] 이
     * 한 번도 방출되지 않아 이 구간이 그대로 드러난다.
     */
    data object Preparing : RecordingState

    /** 카운트다운 진행 중 (기능명세서 3절). 인코딩은 아직 시작되지 않았다. */
    data class CountingDown(
        val remainingSeconds: Int,
    ) : RecordingState

    /**
     * 녹화 진행 중. [elapsed]는 일시정지 구간을 제외한 실제 녹화 시간이다.
     *
     * @param timeLimit 이 세션이 시작할 때 정해진 시간 제한 (기능명세서 11.4절).
     *   알림과 플로팅 버블이 남은 시간을 병기하는 데 쓴다. 녹화 중 설정이 바뀌어도
     *   세션을 멈출 시각은 그대로이므로 설정이 아니라 상태가 들고 있어야 한다.
     */
    data class Recording(
        val elapsed: Duration,
        val timeLimit: TimeLimit = TimeLimit.None,
    ) : RecordingState

    /** 일시정지됨 (기능명세서 11.2절). 세션과 캡처 자원은 유지된다. */
    data class Paused(
        val elapsed: Duration,
        val timeLimit: TimeLimit = TimeLimit.None,
    ) : RecordingState

    /**
     * 중지 처리 중 — 파일을 마무리한다 (기능명세서 2.1절 [결정]).
     *
     * 1시간짜리 녹화는 이 구간이 분 단위로 걸린다. 화면이 얼마나 남았는지 보여주려면
     * 상태가 진행률을 들고 있어야 한다.
     *
     * @param elapsed 방금 녹화한 길이. 저장 중 화면이 결과물의 길이를 함께 보여준다.
     * @param fileName 저장 중인 파일 이름. 무엇이 저장되는지 화면에 못 박는다.
     * @param progress 저장 진행률 (0f..1f). 아직 알 수 없으면 null —
     *   발행은 메타데이터 판독으로 시작하는데 그 구간에는 진행률 신호가 없다.
     *
     * [elapsed] 와 [fileName] 에 기본값을 두지 않는다. 기본값이 있으면 "값을 실어 보냈는지"
     * 검증하는 테스트가 0 과 0 을 비교하는 공허한 단정이 된다.
     */
    data class Stopping(
        val elapsed: Duration,
        val fileName: String,
        val progress: Float? = null,
    ) : RecordingState
}
