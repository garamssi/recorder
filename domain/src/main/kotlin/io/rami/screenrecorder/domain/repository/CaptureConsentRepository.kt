package io.rami.screenrecorder.domain.repository

/**
 * 화면 캡처 동의 보관 경계 (CLAUDE.md 7절).
 *
 * 동의는 세션 수명과 별개다 — 동의를 받고도 세션이 시작되지 않을 수 있다. 그 경우 소비된
 * 동의가 메모리에 남지 않게 여기서 버린다. 세션마다 새로 받아야 하므로 들고 있을 이유가 없다.
 */
interface CaptureConsentRepository {
    /** 쓰지 않기로 한 동의를 버린다. 없으면 아무 일도 하지 않는다. */
    fun discardPending()
}
