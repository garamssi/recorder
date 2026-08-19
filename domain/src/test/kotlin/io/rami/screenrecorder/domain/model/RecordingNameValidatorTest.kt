package io.rami.screenrecorder.domain.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class RecordingNameValidatorTest {
    @Test
    fun `유효한 이름은 통과한다`() {
        assertEquals(NameValidation.Valid, RecordingNameValidator.validate("내 녹화 영상 (1)"))
    }

    @Test
    fun `빈 이름과 공백만인 이름은 거부한다`() {
        assertEquals(NameValidation.Empty, RecordingNameValidator.validate(""))
        assertEquals(NameValidation.Empty, RecordingNameValidator.validate("   "))
    }

    @Test
    fun `파일명 금지 문자를 거부한다`() {
        listOf("/", "\\", ":", "*", "?", "\"", "<", ">", "|").forEach { forbidden ->
            assertEquals(
                NameValidation.ForbiddenCharacter,
                RecordingNameValidator.validate("이름$forbidden"),
                "금지 문자 '$forbidden' 가 거부되지 않음",
            )
        }
    }

    @Test
    fun `100자를 초과하면 거부한다`() {
        val hundredChars = "가".repeat(100)
        assertEquals(NameValidation.Valid, RecordingNameValidator.validate(hundredChars))
        assertEquals(NameValidation.TooLong, RecordingNameValidator.validate(hundredChars + "가"))
    }
}
