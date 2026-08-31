package io.rami.screenrecorder.data.storage

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * 이 프로세스가 만든 발행 자리의 기억 (기능명세서 6.1절 [결정]).
 *
 * 이 기억이 [MediaStorePublishTarget] 인스턴스 필드였을 때, DI 바인딩에 스코프가 없어
 * 인스턴스가 매번 새로 만들어졌다. 발행기·정리기·압축 워커가 각자 다른 기억을 들고 있었고
 * 정리기의 id 판정은 **항상 false** 였다 — 명세가 요구한 1차 판정이 통째로 죽어 있었다.
 * 기억을 따로 두면 그 판정이 바인딩 스코프에 기대지 않는다.
 */
class OwnPublishSlotsTest {
    private val slots = OwnPublishSlots()

    @Test
    fun `기억한 자리를 알아본다`() {
        slots.remember(42L)

        assertTrue(slots.contains(42L))
    }

    @Test
    fun `기억하지 않은 자리는 모른다`() {
        assertFalse(slots.contains(42L))
    }

    @Test
    fun `여러 자리를 함께 기억한다`() {
        slots.remember(1L)
        slots.remember(2L)

        assertTrue(slots.contains(1L))
        assertTrue(slots.contains(2L))
    }
}
