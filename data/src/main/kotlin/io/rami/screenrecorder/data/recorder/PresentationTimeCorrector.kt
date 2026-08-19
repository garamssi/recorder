package io.rami.screenrecorder.data.recorder

/**
 * 일시정지 누적 오프셋 추적기 (기능명세서 11.2절).
 *
 * 비디오/오디오 트랙이 하나의 인스턴스를 공유하여 두 트랙의 보정량이 항상 동일하도록 한다
 * (재개 지점 A/V 동기 오차 1프레임 이내 요구의 전제).
 *
 * 코루틴(pause/resume)과 인코더 콜백 스레드(correct)가 동시에 접근하므로
 * 모든 상태 접근을 동기화한다 (JMM 가시성 보장).
 */
class PauseOffsetTracker {
    private var paused = false
    private var totalUs = 0L
    private var pauseStartedAtUs = 0L

    /** 현재 일시정지 여부. */
    val isPaused: Boolean
        @Synchronized get() = paused

    /** 누적 일시정지 시간(us). */
    val totalPausedUs: Long
        @Synchronized get() = totalUs

    /** [atUs] 시점에 일시정지를 시작한다. */
    @Synchronized
    fun onPause(atUs: Long) {
        check(!paused) { "이미 일시정지 상태다" }
        paused = true
        pauseStartedAtUs = atUs
    }

    /** [atUs] 시점에 재개한다. */
    @Synchronized
    fun onResume(atUs: Long) {
        check(paused) { "일시정지 상태가 아니다" }
        totalUs += atUs - pauseStartedAtUs
        paused = false
    }

    /** 일시정지 중이면 null, 아니면 현재 누적 오프셋(us)의 원자적 스냅샷. */
    @Synchronized
    fun currentOffsetUsOrNull(): Long? = if (paused) null else totalUs
}

/**
 * 인코더 출력 presentationTimeUs를 일시정지 구간만큼 당겨 보정한다 (기능명세서 11.2절).
 *
 * 트랙(비디오/오디오)마다 하나씩 만들고, [PauseOffsetTracker]를 공유해 보정량을 일치시킨다.
 * [correct]는 해당 트랙의 인코더 콜백 스레드 한 곳에서만 호출한다.
 */
class PresentationTimeCorrector(
    private val pauseOffset: PauseOffsetTracker,
) {
    private var lastEmittedUs: Long = Long.MIN_VALUE

    /**
     * [rawPtsUs]를 보정한 값을 반환한다.
     *
     * 일시정지 중이거나 보정 결과가 단조 증가를 깨면 null (해당 샘플은 먹싱에서 제외).
     */
    fun correct(rawPtsUs: Long): Long? {
        val offsetUs = pauseOffset.currentOffsetUsOrNull() ?: return null
        val corrected = rawPtsUs - offsetUs
        if (corrected <= lastEmittedUs) return null
        lastEmittedUs = corrected
        return corrected
    }
}
