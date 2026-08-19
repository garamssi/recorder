package io.rami.screenrecorder.domain.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

class RecordingFileNameTest {
    private val timestamp = LocalDateTime.of(2026, 8, 19, 14, 30, 25)

    @Test
    fun `기본 파일명은 접두어_날짜_시각 형식이다`() {
        val name =
            RecordingFileNameFactory.create(
                prefix = FileNamePrefix("Rec"),
                timestamp = timestamp,
                existingNames = emptySet(),
            )
        assertEquals("Rec_20260819_143025.mp4", name)
    }

    @Test
    fun `동일 초에 충돌하면 순번을 붙인다`() {
        val existing = setOf("Rec_20260819_143025.mp4")
        val name =
            RecordingFileNameFactory.create(
                prefix = FileNamePrefix("Rec"),
                timestamp = timestamp,
                existingNames = existing,
            )
        assertEquals("Rec_20260819_143025_1.mp4", name)
    }

    @Test
    fun `순번도 충돌하면 다음 순번을 쓴다`() {
        val existing =
            setOf(
                "Rec_20260819_143025.mp4",
                "Rec_20260819_143025_1.mp4",
                "Rec_20260819_143025_2.mp4",
            )
        val name =
            RecordingFileNameFactory.create(
                prefix = FileNamePrefix("Rec"),
                timestamp = timestamp,
                existingNames = existing,
            )
        assertEquals("Rec_20260819_143025_3.mp4", name)
    }

    @Test
    fun `접두어는 영문 숫자 언더스코어만 허용한다`() {
        org.junit.jupiter.api
            .assertThrows<IllegalArgumentException> { FileNamePrefix("녹화") }
        org.junit.jupiter.api
            .assertThrows<IllegalArgumentException> { FileNamePrefix("Rec ") }
        org.junit.jupiter.api
            .assertThrows<IllegalArgumentException> { FileNamePrefix("") }
        org.junit.jupiter.api
            .assertDoesNotThrow { FileNamePrefix("My_Rec2") }
    }
}
