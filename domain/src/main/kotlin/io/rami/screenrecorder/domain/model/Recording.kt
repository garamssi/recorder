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
)

/** 녹화 목록 정렬 기준 (기능명세서 7.1절: 최신순 기본). */
enum class SortOrder {
    NEWEST_FIRST,
    OLDEST_FIRST,
    NAME,
    LARGEST_FIRST,
    ;

    /** [recordings]를 이 기준으로 정렬한 새 리스트를 반환한다. */
    fun sort(recordings: List<Recording>): List<Recording> = TODO()
}

/** 녹화 목록 검색 (기능명세서 7.1절: 파일명 부분 일치). */
object RecordingSearch {
    /** [query]가 파일명에 부분 일치(대소문자 무시)하는 항목만 반환한다. 빈 검색어는 전체 반환. */
    fun filter(recordings: List<Recording>, query: String): List<Recording> = TODO()
}
