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
 * 기본 파일명 생성 규칙 (기능명세서 6.2절): `{접두어}_{YYYYMMDD}_{HHmmss}.{확장자}`,
 * 동일 초 충돌 시 `_1`, `_2` 순번.
 *
 * 화면 캡처(.png)와 음성 녹음(.m4a)도 같은 규칙을 따른다 (기능명세서 12, 13절).
 */
object RecordingFileNameFactory {
    /** 화면 녹화 파일 확장자 (기본값). */
    const val VIDEO_EXTENSION = ".mp4"

    /** 화면 캡처 이미지 확장자 (기능명세서 12절). */
    const val IMAGE_EXTENSION = ".png"

    /** 음성 전용 녹음 확장자 (기능명세서 13절). */
    const val AUDIO_EXTENSION = ".m4a"

    private val TIMESTAMP_FORMAT =
        java.time.format.DateTimeFormatter
            .ofPattern("yyyyMMdd_HHmmss")

    /**
     * [existingNames]와 충돌하지 않는 파일명을 생성한다.
     *
     * @param extension 점을 포함한 확장자 (예: ".mp4"). 순번은 같은 확장자 안에서만 매긴다.
     */
    fun create(
        prefix: FileNamePrefix,
        timestamp: LocalDateTime,
        existingNames: Set<String>,
        extension: String = VIDEO_EXTENSION,
    ): String {
        val base = "${prefix.value}_${TIMESTAMP_FORMAT.format(timestamp)}"
        val candidate = base + extension
        if (candidate !in existingNames) return candidate
        return generateSequence(1) { it + 1 }
            .map { sequenceNumber -> "${base}_$sequenceNumber$extension" }
            .first { it !in existingNames }
    }
}
