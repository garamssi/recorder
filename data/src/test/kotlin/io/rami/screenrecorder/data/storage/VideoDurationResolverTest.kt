package io.rami.screenrecorder.data.storage

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.milliseconds

/**
 * fMP4는 moov에 duration이 없어 MediaStore DURATION 컬럼이 0으로 기록된다 (ADR-0001).
 * 이때만 파일 메타데이터를 직접 읽어 보완한다.
 */
class VideoDurationResolverTest {
    @Test
    fun `MediaStore 값이 있으면 그대로 쓴다`() {
        var probeCalls = 0
        val resolver =
            VideoDurationResolver {
                probeCalls++
                9_999L
            }

        val duration = resolver.resolve(contentUri = "content://video/1", mediaStoreDurationMs = 15_401L)

        assertEquals(15_401.milliseconds, duration)
        assertEquals(0, probeCalls) { "값이 있으면 파일을 열지 않는다" }
    }

    @Test
    fun `MediaStore 값이 0이면 파일에서 읽어 보완한다`() {
        val resolver = VideoDurationResolver { 15_401L }

        val duration = resolver.resolve(contentUri = "content://video/1", mediaStoreDurationMs = 0L)

        assertEquals(15_401.milliseconds, duration)
    }

    @Test
    fun `파일에서도 읽지 못하면 0으로 둔다`() {
        val resolver = VideoDurationResolver { null }

        val duration = resolver.resolve(contentUri = "content://video/1", mediaStoreDurationMs = 0L)

        assertEquals(0.milliseconds, duration)
    }

    @Test
    fun `같은 항목을 다시 물어보면 파일을 다시 열지 않는다`() {
        var probeCalls = 0
        val resolver =
            VideoDurationResolver {
                probeCalls++
                15_401L
            }

        repeat(3) { resolver.resolve(contentUri = "content://video/1", mediaStoreDurationMs = 0L) }

        assertEquals(1, probeCalls) { "목록이 갱신될 때마다 파일을 여는 것은 비싸다" }
    }
}
