package io.rami.screenrecorder.domain.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.minutes

class RecordingQueryTest {
    private fun recording(
        name: String,
        createdAt: Long,
        sizeBytes: Long,
    ) = Recording(
        id = RecordingId(createdAt),
        displayName = name,
        contentUri = "content://media/$createdAt",
        sizeBytes = sizeBytes,
        duration = 1.minutes,
        resolution = Resolution.FHD,
        frameRate = 60,
        codec = VideoCodec.H264,
        createdAtEpochMillis = createdAt,
    )

    private val older = recording("B_earlier", createdAt = 1_000, sizeBytes = 300)
    private val newer = recording("a_recent", createdAt = 2_000, sizeBytes = 100)
    private val biggest = recording("C_big", createdAt = 1_500, sizeBytes = 900)
    private val all = listOf(older, newer, biggest)

    @Test
    fun `최신순 정렬이 기본이다`() {
        assertEquals(listOf(newer, biggest, older), SortOrder.NEWEST_FIRST.sort(all))
    }

    @Test
    fun `오래된순 이름순 크기순으로 정렬한다`() {
        assertEquals(listOf(older, biggest, newer), SortOrder.OLDEST_FIRST.sort(all))
        // 이름순은 대소문자 무시
        assertEquals(listOf(newer, older, biggest), SortOrder.NAME.sort(all))
        assertEquals(listOf(biggest, older, newer), SortOrder.LARGEST_FIRST.sort(all))
    }

    @Test
    fun `파일명 부분 일치 검색은 대소문자를 무시한다`() {
        assertEquals(listOf(older, newer), RecordingSearch.filter(all, query = "e"))
        assertEquals(listOf(newer), RecordingSearch.filter(all, query = "RECENT"))
    }

    @Test
    fun `빈 검색어는 전체를 반환한다`() {
        assertEquals(all, RecordingSearch.filter(all, query = ""))
        assertEquals(all, RecordingSearch.filter(all, query = "  "))
    }
}
