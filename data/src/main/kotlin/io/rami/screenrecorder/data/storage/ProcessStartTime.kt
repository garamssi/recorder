package io.rami.screenrecorder.data.storage

import android.os.Process
import android.os.SystemClock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 이 프로세스가 뜬 벽시계 시각(초). 버려진 발행 판정의 폴백 기준이다 (기능명세서 6.1절 [결정]).
 *
 * `Application.onCreate` 에서 만들어 값을 고정한다. 늦게 재면 그 사이 NTP 보정으로 벽시계가
 * 앞으로 점프했을 때 기준선이 같이 밀려, 이 프로세스가 방금 만든 자리도 "프로세스보다 먼저"로
 * 보인다. 프로세스가 어떤 레코드를 만들기도 전에 재야 그 창이 닫힌다.
 */
@Singleton
class ProcessStartTime
    @Inject
    constructor() {
        val epochSeconds: Long =
            processStartEpochSeconds(
                nowEpochMillis = System.currentTimeMillis(),
                elapsedRealtimeMillis = SystemClock.elapsedRealtime(),
                processStartElapsedRealtimeMillis = Process.getStartElapsedRealtime(),
            )
    }
