package io.rami.screenrecorder.data.recorder

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class PresentationTimeCorrectorTest {
    private val pauseOffset = PauseOffsetTracker()
    private val corrector = PresentationTimeCorrector(pauseOffset)

    @Test
    fun `일시정지가 없으면 원본 타임스탬프를 그대로 반환한다`() {
        assertEquals(0L, corrector.correct(rawPtsUs = 0L))
        assertEquals(16_666L, corrector.correct(rawPtsUs = 16_666L))
    }

    @Test
    fun `일시정지 중 캡처된 샘플은 버린다`() {
        corrector.correct(rawPtsUs = 100_000L)
        pauseOffset.onPause(atUs = 150_000L)

        assertNull(corrector.correct(rawPtsUs = 200_000L))
    }

    @Test
    fun `재개 후 타임스탬프는 일시정지 누적분만큼 당겨진다`() {
        corrector.correct(rawPtsUs = 100_000L)
        pauseOffset.onPause(atUs = 150_000L)
        pauseOffset.onResume(atUs = 1_150_000L) // 1초 일시정지

        // 1,200,000 - 1,000,000(누적 일시정지) = 200,000
        assertEquals(200_000L, corrector.correct(rawPtsUs = 1_200_000L))
    }

    @Test
    fun `여러 번의 일시정지가 누적 보정된다`() {
        pauseOffset.onPause(atUs = 100_000L)
        pauseOffset.onResume(atUs = 200_000L) // +100ms
        pauseOffset.onPause(atUs = 300_000L)
        pauseOffset.onResume(atUs = 600_000L) // +300ms, 누적 400ms

        assertEquals(300_000L, corrector.correct(rawPtsUs = 700_000L))
    }

    @Test
    fun `보정 결과가 직전 값보다 작거나 같으면 버린다 (단조 증가 보장)`() {
        assertEquals(100_000L, corrector.correct(rawPtsUs = 100_000L))

        assertNull(corrector.correct(rawPtsUs = 100_000L))
        assertNull(corrector.correct(rawPtsUs = 99_000L))
        assertEquals(116_666L, corrector.correct(rawPtsUs = 116_666L))
    }

    @Test
    fun `비디오와 오디오에 동일한 보정량이 적용된다`() {
        // 하나의 오프셋 추적기를 공유하고, 트랙별 단조성은 corrector 인스턴스로 관리한다.
        val shared = PauseOffsetTracker()
        shared.onPause(atUs = 100_000L)
        shared.onResume(atUs = 300_000L)

        val video = PresentationTimeCorrector(shared)
        val audio = PresentationTimeCorrector(shared)

        assertEquals(200_000L, video.correct(rawPtsUs = 400_000L))
        assertEquals(210_000L, audio.correct(rawPtsUs = 410_000L))
    }
}
