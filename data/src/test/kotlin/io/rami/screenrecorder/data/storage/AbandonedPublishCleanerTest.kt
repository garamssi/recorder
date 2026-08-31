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
            val created = mutableListOf<Long>()
            var discardFailsFor: String? = null

            override fun create(fileName: String): PublishSlot {
                val slot = PublishSlot(id = created.size + 100L, uri = "새자리${created.size}")
                created += slot.id
                return slot
            }

            override fun write(
                slot: PublishSlot,
                tempFile: File,
            ) = error("쓰지 않는다")

            override fun finish(slot: PublishSlot) = error("쓰지 않는다")

            override fun discard(slot: PublishSlot) {
                if (slot.uri == discardFailsFor) error("삭제 실패: ${slot.uri}")
                discarded += slot.uri
            }

            override fun sizeOf(slot: PublishSlot): Long = 0L

            override fun listPending(): List<PendingPublish> = pending

            override fun wasCreatedByThisProcess(slot: PublishSlot): Boolean = slot.id in created
        }

    private fun cleaner() = AbandonedPublishCleaner(target, PROCESS_STARTED_AT)

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

    /**
     * 이 프로세스가 만든 자리는 시각과 무관하게 지키다 (기능명세서 6.1절 [결정]).
     *
     * 시각 비교만으로는 부족하다 — 시계가 앞으로 보정되면 기준선이 밀려 방금 만든 자리도
     * "프로세스보다 먼저"로 보인다. 그러면 진행 중인 발행을 지운다.
     */
    @Test
    fun `이 프로세스가 만든 자리는 시각이 어긋나도 지키지 않는다는 판정에서 제외한다`() {
        val slot = target.create("진행중.mp4")
        // 시계가 앞으로 밀려 방금 만든 자리가 "프로세스보다 먼저" 로 보이는 상황.
        pending += PendingPublish(slot, createdAtEpochSeconds = PROCESS_STARTED_AT - 100)

        val count = cleaner().discardAbandoned()

        assertEquals(emptyList<String>(), discarded)
        assertEquals(0, count)
    }

    /** 조기 회수일 뿐이라 한 건 때문에 나머지를 포기할 이유가 없다. */
    @Test
    fun `한 건을 못 지워도 나머지는 계속 지운다`() {
        pending += pendingEntry("첫번째", PROCESS_STARTED_AT - 10)
        pending += pendingEntry("실패하는것", PROCESS_STARTED_AT - 9)
        pending += pendingEntry("세번째", PROCESS_STARTED_AT - 8)
        target.discardFailsFor = "실패하는것"

        val count = cleaner().discardAbandoned()

        assertEquals(listOf("첫번째", "세번째"), discarded)
        assertEquals(2, count)
    }

    @Test
    fun `회수 건수는 실패한 것을 빼고 센다`() {
        pending += pendingEntry("실패하는것", PROCESS_STARTED_AT - 10)
        target.discardFailsFor = "실패하는것"

        assertEquals(0, cleaner().discardAbandoned())
    }

    private companion object {
        const val PROCESS_STARTED_AT = 1_788_155_808L
    }
}
