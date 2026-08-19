package io.rami.screenrecorder.domain.model

import kotlin.time.Duration

/** 저장된 녹화본의 식별자 (MediaStore ID에 대응). */
@JvmInline
value class RecordingId(
    val value: Long,
)

/** 저장된 녹화본 한 건 (기능명세서 7절 목록 표시 정보). */
data class Recording(
    val id: RecordingId,
    val displayName: String,
    val contentUri: String,
    val sizeBytes: Long,
    val duration: Duration,
    val resolution: Resolution,
    val frameRate: Int,
    val codec: VideoCodec,
    val createdAtEpochMillis: Long,
    /** 인코딩 비트레이트(bps). 메타데이터에서 읽을 수 없으면 null (기능명세서 7.2절 상세 정보). */
    val bitrateBps: Int?,
)

/** 녹화 목록 정렬 기준 (기능명세서 7.1절: 최신순 기본). */
enum class SortOrder {
    NEWEST_FIRST,
    OLDEST_FIRST,
    NAME,
    LARGEST_FIRST,
    ;

    /** [recordings]를 이 기준으로 정렬한 새 리스트를 반환한다. */
    fun sort(recordings: List<Recording>): List<Recording> =
        when (this) {
            NEWEST_FIRST -> recordings.sortedByDescending { it.createdAtEpochMillis }
            OLDEST_FIRST -> recordings.sortedBy { it.createdAtEpochMillis }
            NAME -> recordings.sortedBy { it.displayName.lowercase() }
            LARGEST_FIRST -> recordings.sortedByDescending { it.sizeBytes }
        }
}

/** 녹화 목록 검색 (기능명세서 7.1절: 파일명 부분 일치). */
object RecordingSearch {
    /** [query]가 파일명에 부분 일치(대소문자 무시)하는 항목만 반환한다. 빈 검색어는 전체 반환. */
    fun filter(
        recordings: List<Recording>,
        query: String,
    ): List<Recording> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return recordings
        return recordings.filter { it.displayName.contains(trimmed, ignoreCase = true) }
    }
}
