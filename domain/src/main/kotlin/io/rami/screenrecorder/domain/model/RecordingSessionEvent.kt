package io.rami.screenrecorder.domain.model

import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/** 자동 안전 중지 사유 (기능명세서 11절). */
enum class AutoStopReason {
    /** 타이머 녹화 시간 도달 (11.4절). */
    TIME_LIMIT_REACHED,

    /** 저장 공간 부족 (11.1절: 200MB 이하). */
    STORAGE_LOW,

    /** 일시정지 30분 초과 (11.2절). */
    PAUSE_TIMEOUT,
}

/** 세션 진행 중 알림이 필요한 이벤트 (기능명세서 11절). */
sealed interface RecordingSessionEvent {
    /** 시간 제한 종료 예고 (11.4절: 1분 전, 10초 전). */
    data class TimeLimitWarning(
        val remaining: Duration,
    ) : RecordingSessionEvent

    /** 일시정지 자동 중지 예고 (11.2절: 중지 5분 전). */
    data class PauseTimeoutWarning(
        val remaining: Duration,
    ) : RecordingSessionEvent

    /** 자동 안전 중지 발생. 저장은 완료 이벤트로 별도 전달된다. */
    data class AutoStopped(
        val reason: AutoStopReason,
    ) : RecordingSessionEvent

    /**
     * 발행이 실패해 녹화본이 저장되지 않았다 (기능명세서 2.1절 [결정]).
     *
     * 중지 처리는 성공·실패·빈 세션 모두 같은 방식으로 끝나므로 상태만 보고는 구분할 수 없다.
     * 실패를 알리지 않으면 진행 게이지가 도중에 사라지고 사용자는 저장된 줄 안다.
     * 임시 파일은 남아 다음 실행에서 복구를 제안한다 (6.1절).
     */
    data object SaveFailed : RecordingSessionEvent

    /**
     * 선택한 마이크 입력 장치를 쓸 수 없어 시스템 기본 마이크로 녹음 중 (기능명세서 4.2절 [결정]).
     *
     * 블루투스 헤드셋 미연결, SCO/LE 링크 실패 등이 원인이다. 조용히 다른 마이크로 녹음되면
     * 사용자가 나중에야 알게 되므로 시작 직후 알린다.
     */
    data class MicrophoneFellBack(
        val requested: MicrophoneDevice,
    ) : RecordingSessionEvent

    /**
     * 부분 영역 녹화 중 회전 감지로 자동 일시정지됨 (기능명세서 5절 [결정]).
     *
     * UI는 "영역을 다시 지정하거나 중지하세요" 알림을 표시한다.
     */
    data object RegionInvalidatedByRotation : RecordingSessionEvent
}

/** 일시정지 자동 중지 정책 (기능명세서 11.2절 [결정]). */
object PauseTimeoutPolicy {
    /** 일시정지 상태 최대 유지 시간. */
    val AUTO_STOP_AFTER: Duration = 30.minutes

    /** 자동 중지 예고 시점 (중지 몇 분 전). */
    val WARNING_BEFORE: Duration = 5.minutes
}

/** 타이머 녹화 예고 정책 (기능명세서 11.4절 [결정]). */
object TimeLimitWarningPolicy {
    /** 1차 예고 시점 (종료 1분 전). 제한이 1분 미만이면 생략한다. */
    val FIRST_WARNING_BEFORE: Duration = 60.seconds

    /** 최종 예고 시점 (종료 10초 전). */
    val FINAL_WARNING_BEFORE: Duration = 10.seconds
}
