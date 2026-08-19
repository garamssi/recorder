package io.rami.screenrecorder.domain.session

/**
 * 단조 증가 시계 추상화.
 *
 * 시스템 시계 변경에 영향받지 않아야 하는 타이머 계산에 쓴다 (기능명세서 11.4절,
 * Android 구현은 SystemClock.elapsedRealtime을 data 계층에서 주입).
 */
fun interface MonotonicClock {
    /** 부팅 이후 경과 밀리초. */
    fun elapsedRealtimeMillis(): Long
}
