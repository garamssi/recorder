package io.rami.screenrecorder.data.recorder

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.withTimeoutOrNull

/**
 * 녹화 시작 카운트다운 실행기 (기능명세서 3절).
 *
 * 탭 스킵([skip])과 중지 요청([abort])을 처리하며, 인코딩은 카운트다운 종료 후에만
 * 시작되므로 카운트다운 숫자는 영상에 포함되지 않는다.
 */
internal class CountdownRunner {
    private var control: Control? = null

    /** [seconds]초 카운트다운을 진행하며 남은 초를 [onTick]으로 알린다. 중단 요청이면 true. */
    suspend fun run(
        seconds: Int,
        onTick: (Int) -> Unit,
    ): Boolean {
        if (seconds == 0) return false
        val activeControl = Control()
        control = activeControl
        var signal: Signal? = null
        try {
            for (remaining in seconds downTo 1) {
                onTick(remaining)
                signal = awaitTick(activeControl)
                if (signal != null) break
            }
        } finally {
            control = null
        }
        return signal == Signal.ABORT
    }

    /** 카운트다운을 건너뛰고 즉시 시작한다. */
    fun skip() {
        control?.skip?.complete(Unit)
    }

    /** 카운트다운을 중단한다 (녹화 시작 안 함). */
    fun abort() {
        control?.abort?.complete(Unit)
    }

    private suspend fun awaitTick(control: Control): Signal? =
        withTimeoutOrNull(TICK_MS) {
            select {
                control.skip.onAwait { Signal.SKIP }
                control.abort.onAwait { Signal.ABORT }
            }
        }

    private class Control {
        val skip = CompletableDeferred<Unit>()
        val abort = CompletableDeferred<Unit>()
    }

    private enum class Signal { SKIP, ABORT }

    private companion object {
        const val TICK_MS = 1_000L
    }
}
