package io.rami.screenrecorder.domain.model

/** 이름 변경 유효성 검사 결과 (기능명세서 6.3절). */
enum class NameValidation {
    Valid,
    Empty,
    ForbiddenCharacter,
    TooLong,
}

/**
 * 녹화본 이름 변경 유효성 규칙 (기능명세서 6.3절):
 * 빈 이름 금지, 금지 문자(`/ \ : * ? " < > |`) 차단, 최대 100자.
 * 확장자 `.mp4`는 UI에서 고정 표시하므로 검사 대상에 포함하지 않는다.
 */
object RecordingNameValidator {
    /** [name]의 유효성을 검사한다. */
    fun validate(name: String): NameValidation = TODO()
}
