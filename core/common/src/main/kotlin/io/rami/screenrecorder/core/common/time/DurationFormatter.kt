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
        TODO("[RED] 단계: 아직 구현되지 않음")
    }
}
