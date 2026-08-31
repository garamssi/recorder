package io.rami.screenrecorder.data.storage

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * 발행 진행률 스로틀 (기능명세서 2.1절 [결정]).
 *
 * 1시간 FHD 는 샘플이 수십만 개다. 샘플마다 알리면 상태 방출이 그 수만큼 일어나 알림과
 * 플로팅 버블까지 따라 돈다. 화면에서 구분되는 것은 0.5% 단위뿐이다.
 */
class ByteProgressTest {
    private val reported = mutableListOf<Float>()

    private fun report(
        copied: Long,
        total: Long,
        last: Float,
    ): Float = reportByteProgress(copied, total, last) { reported += it }

    @Test
    fun `증분이 임계 미만이면 알리지 않는다`() {
        val next = report(copied = 1L, total = 1_000L, last = 0f)

        assertTrue(reported.isEmpty())
        assertEquals(0f, next)
    }

    @Test
    fun `임계에 닿으면 알리고 기준을 갱신한다`() {
        val next = report(copied = 5L, total = 1_000L, last = 0f)

        assertEquals(listOf(0.005f), reported)
        assertEquals(0.005f, next)
    }

    /**
     * 처음부터 끝까지 감으면 단조 증가하며 1f 로 끝나고, 보고 횟수는 임계가 정하는 상한 안에 있다.
     *
     * 정확한 횟수를 단정하지 않는 이유: 0.005 는 이진수로 정확히 표현되지 않아 증분이 정확히
     * 임계인 구간에서는 비교가 한 번씩 걸러진다. 화면에는 영향이 없고, 상한만 지키면 된다.
     */
    @Test
    fun `끝까지 감으면 단조 증가하며 1f 로 닫힌다`() {
        var last = 0f
        repeat(TOTAL_STEPS) { step ->
            last = report(copied = (step + 1).toLong(), total = TOTAL_STEPS.toLong(), last = last)
        }

        assertEquals(1f, reported.last())
        assertEquals(reported.sorted(), reported)
        assertTrue(reported.size <= MAX_REPORTS) { "보고가 상한을 넘었다: ${reported.size}" }
        assertTrue(reported.size >= MIN_REPORTS) { "보고가 너무 적다: ${reported.size}" }
    }

    @Test
    fun `크기를 모르면 아무것도 알리지 않는다`() {
        val next = report(copied = 500L, total = 0L, last = 0.2f)

        assertTrue(reported.isEmpty())
        assertEquals(0.2f, next)
    }

    @Test
    fun `음수 크기에도 알리지 않는다`() {
        report(copied = 500L, total = -1L, last = 0f)

        assertTrue(reported.isEmpty())
    }

    /** 컨테이너 부담 때문에 샘플 바이트 합이 파일 크기를 넘을 수 있다. */
    @Test
    fun `파일 크기를 넘어도 1f 를 넘기지 않는다`() {
        report(copied = 2_000L, total = 1_000L, last = 0f)

        assertEquals(listOf(1f), reported)
    }

    /**
     * 폴백 리매핑 (기능명세서 2.1절 [결정]).
     *
     * remux 가 도중에 실패하면 원본 전량 복사가 0 바이트부터 다시 센다. 남은 구간으로
     * 접어 넣지 않으면 게이지가 되감기거나, 되감기를 걸러 내는 동안 얼어붙는다.
     */
    @Test
    fun `폴백 진행률을 남은 구간으로 접어 넣는다`() {
        assertEquals(0.6f, remainingBandProgress(alreadyDone = 0.6f, fraction = 0f))
        assertEquals(0.8f, remainingBandProgress(alreadyDone = 0.6f, fraction = 0.5f))
        assertEquals(1f, remainingBandProgress(alreadyDone = 0.6f, fraction = 1f))
    }

    @Test
    fun `폴백 진행률은 뒤로 가지 않는다`() {
        val alreadyDone = 0.95f

        // 복사 초반의 아주 작은 값도 이미 진행한 지점 아래로 내려가지 않아야 한다.
        assertTrue(remainingBandProgress(alreadyDone, fraction = 0.001f) >= alreadyDone)
    }

    @Test
    fun `범위를 벗어난 입력을 잘라 낸다`() {
        assertEquals(0f, remainingBandProgress(alreadyDone = -1f, fraction = 0f))
        assertEquals(1f, remainingBandProgress(alreadyDone = 2f, fraction = 0f))
        assertEquals(0.5f, remainingBandProgress(alreadyDone = 0.5f, fraction = -1f))
        assertEquals(1f, remainingBandProgress(alreadyDone = 0.5f, fraction = 9f))
    }

    private companion object {
        const val TOTAL_STEPS = 200

        // 0.5% 임계에서 실측 110회다. 임계를 절반(0.0025 → 200회)이나 두 배(0.01 → 100회)로
        // 바꾸면 이 구간을 벗어나므로, 경계가 임계를 실제로 고정한다.
        const val MAX_REPORTS = 130
        const val MIN_REPORTS = 105
    }
}
