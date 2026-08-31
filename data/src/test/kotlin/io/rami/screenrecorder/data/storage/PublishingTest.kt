package io.rami.screenrecorder.data.storage

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.io.File
import java.io.IOException

/**
 * 발행 자리를 다루는 순서와 실패 처리 (기능명세서 6.1절 [결정]).
 *
 * 녹화본과 압축 결과가 같은 규율을 쓴다. 압축 워커가 자기 코드를 따로 갖고 있던 동안
 * 0바이트 파일을 성공으로 발행하고 실패한 자리를 정리하지 않았다.
 */
class PublishingTest {
    private val calls = mutableListOf<String>()

    private val target =
        object : PublishTarget {
            override fun create(fileName: String): PublishSlot {
                calls += "create"
                return PublishSlot(id = 1L, uri = "자리")
            }

            override fun write(
                slot: PublishSlot,
                tempFile: File,
            ) = error("이 테스트는 쓰기 전략을 직접 넘긴다")

            override fun finish(slot: PublishSlot) {
                calls += "finish"
            }

            override fun discard(slot: PublishSlot) {
                calls += "discard"
            }

            override fun sizeOf(slot: PublishSlot): Long = 0L

            override fun listPending(): List<PendingPublish> = emptyList()

            override fun wasCreatedByThisProcess(slot: PublishSlot): Boolean = true
        }

    @Test
    fun `자리를 만들고 쓰고 확정한다`() {
        val slot = target.publishing("a.mp4") { calls += "write" }

        assertEquals(listOf("create", "write", "finish"), calls)
        assertEquals(1L, slot.id)
    }

    @Test
    fun `쓰기에 실패하면 미완성 자리를 지우고 원인을 전파한다`() {
        assertThrows<IOException> {
            target.publishing("a.mp4") { throw IOException("스트림을 열지 못했다") }
        }

        assertEquals(listOf("create", "discard"), calls)
    }

    @Test
    fun `확정에 실패해도 미완성 자리를 지운다`() {
        val failing =
            object : PublishTarget by target {
                override fun finish(slot: PublishSlot) = throw IOException("확정 실패")
            }

        assertThrows<IOException> { failing.publishing("a.mp4") { } }

        assertEquals(listOf("create", "discard"), calls)
    }
}
