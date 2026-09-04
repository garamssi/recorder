package io.rami.screenrecorder.service

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * 설정을 아직 읽지 못한 동안에는 버블 서비스를 건드리지 않는다 (기능명세서 11.1절 [결정]).
 *
 * 앱 화면은 DataStore 를 읽기 전에 이미 한 번 그려지고, 그때 손에 든 것은 저장된 값이 아니라
 * 플레이스홀더 기본값(버블 끔)이다. 그 값으로 HIDE 를 보내면 이미 떠 있는 서비스가 스스로
 * 멈춘다 — 앱을 열 때마다 버블 서비스가 죽고 다시 뜨면서 드래그해 둔 자리와 그리고 있던
 * 상태를 잃는다.
 */
class FloatingBubbleCommandTest {
    @Test
    fun `설정을 아직 읽지 못했으면 아무것도 보내지 않는다`() {
        assertNull(
            floatingBubbleCommand(showFloatingBubble = null, canDrawOverlays = true),
            "플레이스홀더 기본값으로 HIDE 를 보내면 떠 있던 버블 서비스가 죽는다",
        )
    }

    @Test
    fun `설정을 모르면 권한이 없어도 아무것도 보내지 않는다`() {
        assertNull(floatingBubbleCommand(showFloatingBubble = null, canDrawOverlays = false))
    }

    @Test
    fun `설정이 켜져 있고 오버레이 권한이 있으면 띄운다`() {
        assertEquals(
            FloatingBubbleCommand.SHOW,
            floatingBubbleCommand(showFloatingBubble = true, canDrawOverlays = true),
        )
    }

    @Test
    fun `설정이 켜져 있어도 오버레이 권한이 없으면 내린다`() {
        assertEquals(
            FloatingBubbleCommand.HIDE,
            floatingBubbleCommand(showFloatingBubble = true, canDrawOverlays = false),
            "권한 없이 띄우면 서비스가 즉시 스스로 멈춰 알림만 깜빡인다",
        )
    }

    @Test
    fun `설정이 꺼져 있으면 내린다`() {
        assertEquals(
            FloatingBubbleCommand.HIDE,
            floatingBubbleCommand(showFloatingBubble = false, canDrawOverlays = true),
        )
    }
}
