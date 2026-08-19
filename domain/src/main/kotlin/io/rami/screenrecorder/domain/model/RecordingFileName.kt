package io.rami.screenrecorder.domain.model

import java.time.LocalDateTime

/**
 * 파일명 접두어 (기능명세서 4.3절: 영문, 숫자, 언더스코어만, 기본 "Rec").
 */
@JvmInline
value class FileNamePrefix(
    val value: String,
) {
    init {
        require(value.matches(ALLOWED_PATTERN)) {
            "접두어는 영문, 숫자, 언더스코어만 허용한다: '$value'"
        }
    }

    companion object {
        private val ALLOWED_PATTERN = Regex("[A-Za-z0-9_]+")

        /** 기본 접두어 (기능명세서 4.3절). */
        val DEFAULT = FileNamePrefix("Rec")
    }
}

/**
 * 기본 파일명 생성 규칙 (기능명세서 6.2절): `{접두어}_{YYYYMMDD}_{HHmmss}.mp4`,
 * 동일 초 충돌 시 `_1`, `_2` 순번.
 */
object RecordingFileNameFactory {
    private const val EXTENSION = ".mp4"
    private val TIMESTAMP_FORMAT =
        java.time.format.DateTimeFormatter
            .ofPattern("yyyyMMdd_HHmmss")

    /** [existingNames]와 충돌하지 않는 파일명을 생성한다. */
    fun create(
        prefix: FileNamePrefix,
        timestamp: LocalDateTime,
        existingNames: Set<String>,
    ): String {
        val base = "${prefix.value}_${TIMESTAMP_FORMAT.format(timestamp)}"
        val candidate = base + EXTENSION
        if (candidate !in existingNames) return candidate
        return generateSequence(1) { it + 1 }
            .map { sequenceNumber -> "${base}_$sequenceNumber$EXTENSION" }
            .first { it !in existingNames }
    }
}
