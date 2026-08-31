package io.rami.screenrecorder.data.storage

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.io.File

/**
 * 버려진 미완성 레코드 정리 (기능명세서 6.1절 [결정]).
 *
 * 폴더와 is_pending 값만으로 판정하면 **진행 중인 압축 결과를 지운다** — 압축은 녹화본과 같은
 * 폴더에 같은 방식으로 발행하면서 WorkManager 잡이라 녹화 상태와 무관하게 돈다. 기준은
 * "이 프로세스가 시작되기 전에 만들어졌는가" 하나다.
 */
class AbandonedPublishCleanerTest {
    private val pending = mutableListOf<PendingPublish>()
    private val discarded = mutableListOf<String>()

    private val target =
        object : PublishTarget {
            override fun create(fileName: String) = error("쓰지 않는다")

            override fun write(
                slot: PublishSlot,
                tempFile: File,
            ) = error("쓰지 않는다")

            override fun finish(slot: PublishSlot) = error("쓰지 않는다")

            override fun discard(slot: PublishSlot) {
                discarded += slot.uri
            }

            override fun listPending(): List<PendingPublish> = pending
        }

    private fun cleaner() = AbandonedPublishCleaner(target) { PROCESS_STARTED_AT }

    private fun pendingEntry(
        uri: String,
        createdAt: Long,
    ) = PendingPublish(PublishSlot(id = 1L, uri = uri), createdAtEpochSeconds = createdAt)

    @Test
    fun `프로세스보다 먼저 만들어진 미완성 레코드를 지운다`() {
        pending += pendingEntry("죽은발행", PROCESS_STARTED_AT - 1)

        val count = cleaner().discardAbandoned()

        assertEquals(listOf("죽은발행"), discarded)
        assertEquals(1, count)
    }

    /** 이 프로세스가 만든 것은 발행 중이거나 이미 끝난 것이다. */
    @Test
    fun `이 프로세스가 만든 미완성 레코드는 지우지 않는다`() {
        pending += pendingEntry("진행중발행", PROCESS_STARTED_AT + 1)

        val count = cleaner().discardAbandoned()

        assertEquals(emptyList<String>(), discarded)
        assertEquals(0, count)
    }

    /** 압축은 WorkManager 잡이라 녹화 상태와 무관하게 도는데, 같은 규칙으로 보호된다. */
    @Test
    fun `프로세스 시작과 같은 시각이면 지우지 않는다`() {
        pending += pendingEntry("경계", PROCESS_STARTED_AT)

        cleaner().discardAbandoned()

        assertEquals(emptyList<String>(), discarded)
    }

    @Test
    fun `버려진 것만 골라 지운다`() {
        pending += pendingEntry("죽은발행", PROCESS_STARTED_AT - 10)
        pending += pendingEntry("진행중압축", PROCESS_STARTED_AT + 10)
        pending += pendingEntry("또다른죽은발행", PROCESS_STARTED_AT - 5)

        val count = cleaner().discardAbandoned()

        assertEquals(listOf("죽은발행", "또다른죽은발행"), discarded)
        assertEquals(2, count)
    }

    @Test
    fun `지울 것이 없으면 아무것도 하지 않는다`() {
        assertEquals(0, cleaner().discardAbandoned())
        assertEquals(emptyList<String>(), discarded)
    }

    private companion object {
        const val PROCESS_STARTED_AT = 1_788_155_808L
    }
}
