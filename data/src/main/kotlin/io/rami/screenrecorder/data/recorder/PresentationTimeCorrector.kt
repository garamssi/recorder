package io.rami.screenrecorder.data.recorder

/**
 * 일시정지 누적 오프셋 추적기 (기능명세서 11.2절).
 *
 * 비디오/오디오 트랙이 하나의 인스턴스를 공유하여 두 트랙의 보정량이 항상 동일하도록 한다
 * (재개 지점 A/V 동기 오차 1프레임 이내 요구의 전제).
 */
class PauseOffsetTracker {
    /** 현재 일시정지 여부. */
    var isPaused: Boolean = false
        private set

    /** 누적 일시정지 시간(us). */
    var totalPausedUs: Long = 0L
        private set

    private var pauseStartedAtUs: Long = 0L

    /** [atUs] 시점에 일시정지를 시작한다. */
    fun onPause(atUs: Long) {
        check(!isPaused) { "이미 일시정지 상태다" }
        isPaused = true
        pauseStartedAtUs = atUs
    }

    /** [atUs] 시점에 재개한다. */
    fun onResume(atUs: Long) {
        check(isPaused) { "일시정지 상태가 아니다" }
        totalPausedUs += atUs - pauseStartedAtUs
        isPaused = false
    }
}

/**
 * 인코더 출력 presentationTimeUs를 일시정지 구간만큼 당겨 보정한다 (기능명세서 11.2절).
 *
 * 트랙(비디오/오디오)마다 하나씩 만들고, [PauseOffsetTracker]를 공유해 보정량을 일치시킨다.
 */
class PresentationTimeCorrector(
    private val pauseOffset: PauseOffsetTracker = PauseOffsetTracker(),
) {
    /** [pauseOffset]으로 위임. 단일 트랙 사용 편의를 위한 프록시. */
    fun onPause(atUs: Long) = pauseOffset.onPause(atUs)

    /** [pauseOffset]으로 위임. */
    fun onResume(atUs: Long) = pauseOffset.onResume(atUs)

    private var lastEmittedUs: Long = Long.MIN_VALUE

    /**
     * [rawPtsUs]를 보정한 값을 반환한다.
     *
     * 일시정지 중이거나 보정 결과가 단조 증가를 깨면 null (해당 샘플은 먹싱에서 제외).
     */
    fun correct(rawPtsUs: Long): Long? {
        if (pauseOffset.isPaused) return null
        val corrected = rawPtsUs - pauseOffset.totalPausedUs
        if (corrected <= lastEmittedUs) return null
        lastEmittedUs = corrected
        return corrected
    }
}
