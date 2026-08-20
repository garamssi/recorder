package io.rami.screenrecorder.domain.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.minutes

class TrashItemTest {
    @Test
    fun `휴지통 항목은 원본 녹화와 삭제 예정일을 담는다`() {
        val recording =
            Recording(
                id = RecordingId(1),
                displayName = "녹화_1.mp4",
                contentUri = "content://media/1",
                sizeBytes = 100,
                duration = 1.minutes,
                resolution = Resolution.FHD,
                frameRate = 60,
                codec = VideoCodec.H264,
                bitrateBps = null,
                createdAtEpochMillis = 1_000,
            )

        val item = TrashItem(recording = recording, daysUntilDeletion = 29)

        assertEquals(recording, item.recording)
        assertEquals(29, item.daysUntilDeletion)
        assertEquals(item, item.copy())
    }
}
