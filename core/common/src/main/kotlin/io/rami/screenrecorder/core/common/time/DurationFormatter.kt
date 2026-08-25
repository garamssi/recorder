package io.rami.screenrecorder.core.common.time

import kotlin.time.Duration

/**
 * 녹화 경과/제한 시간을 화면·알림에 표시하기 위한 포매터 (기능명세서 11절).
 *
 * 1시간 미만은 "MM:SS", 1시간 이상은 "HH:MM:SS" 형식이다.
 */
object DurationFormatter {
    /**
     * [duration]을 경과 시간 문자열로 변환한다. 밀리초 이하는 버림.
     *
     * @throws IllegalArgumentException 음수 시간이 주어진 경우
     */
    fun formatElapsed(duration: Duration): String {
        require(!duration.isNegative()) { "경과 시간은 음수일 수 없다: $duration" }
        return duration.toComponents { hours, minutes, seconds, _ ->
            if (hours > 0) {
                "%02d:%02d:%02d".format(hours, minutes, seconds)
            } else {
                "%02d:%02d".format(minutes, seconds)
            }
        }
    }

    /**
     * 경과 시간에 제한을 병기한다 (기능명세서 11.4절: 예 "03:24 / 10:00").
     *
     * 알림과 플로팅 버블이 같은 표기를 쓰도록 구분 기호를 한곳에 둔다.
     *
     * @param limit 시간 제한. null이면 제한이 없어 경과 시간만 남긴다.
     */
    fun formatElapsedWithLimit(
        elapsed: Duration,
        limit: Duration?,
    ): String {
        val elapsedText = formatElapsed(elapsed)
        return if (limit == null) elapsedText else "$elapsedText$SEPARATOR${formatElapsed(limit)}"
    }

    private const val SEPARATOR = " / "
}
