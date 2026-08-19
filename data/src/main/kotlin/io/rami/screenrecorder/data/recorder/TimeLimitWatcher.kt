package io.rami.screenrecorder.data.recorder

import io.rami.screenrecorder.domain.model.TimeLimit
import io.rami.screenrecorder.domain.model.TimeLimitWarningPolicy
import kotlin.time.Duration

/**
 * 타이머 녹화 진행 감시 (기능명세서 11.4절).
 *
 * 경과 시간(일시정지 제외)을 받아 1분 전/10초 전 예고와 종료 시점을 판정한다.
 * 예고의 remaining은 알림 문구용 명목값(정책 상수)이다.
 */
internal class TimeLimitWatcher(
    private val timeLimit: TimeLimit,
) {
    private var firstWarningSent = false
    private var finalWarningSent = false

    /** 틱 판정 결과. */
    sealed interface Verdict {
        /** 예고 알림 필요. */
        data class Warn(
            val remaining: Duration,
        ) : Verdict

        /** 시간 도달 — 자동 안전 중지. */
        data object Stop : Verdict

        /** 조치 없음. */
        data object Continue : Verdict
    }

    /** [elapsed] 기준으로 판정한다. */
    fun onTick(elapsed: Duration): Verdict {
        val limit = (timeLimit as? TimeLimit.Limited)?.duration ?: return Verdict.Continue
        val remaining = limit - elapsed
        return when {
            remaining <= Duration.ZERO -> Verdict.Stop

            shouldSendFirstWarning(limit, remaining) -> {
                firstWarningSent = true
                Verdict.Warn(TimeLimitWarningPolicy.FIRST_WARNING_BEFORE)
            }

            shouldSendFinalWarning(remaining) -> {
                finalWarningSent = true
                Verdict.Warn(TimeLimitWarningPolicy.FINAL_WARNING_BEFORE)
            }

            else -> Verdict.Continue
        }
    }

    private fun shouldSendFirstWarning(
        limit: Duration,
        remaining: Duration,
    ): Boolean =
        !firstWarningSent &&
            limit > TimeLimitWarningPolicy.FIRST_WARNING_BEFORE &&
            remaining <= TimeLimitWarningPolicy.FIRST_WARNING_BEFORE &&
            remaining > TimeLimitWarningPolicy.FINAL_WARNING_BEFORE

    private fun shouldSendFinalWarning(remaining: Duration): Boolean =
        !finalWarningSent && remaining <= TimeLimitWarningPolicy.FINAL_WARNING_BEFORE
}
