package io.rami.screenrecorder.data.recorder

import android.media.MediaFormat
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLDisplay
import android.opengl.EGLExt
import android.opengl.GLES20
import android.view.Surface
import io.rami.screenrecorder.domain.model.FrameRate
import io.rami.screenrecorder.domain.model.Resolution
import io.rami.screenrecorder.domain.model.VideoCodec
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * 인코더 처리량 계측 (prompt.md 4절 [결정]).
 *
 * GL 렌더 루프가 1080p 프레임을 60fps 페이스로 인코더 입력 서피스에 공급하고,
 * 인코더 출력 샘플의 presentationTimeUs 간격으로 평균 fps를 산출한다.
 * 평균 58fps 미만이면 실패다 (몰래 다운그레이드 금지 — 원인 분석 대상).
 */
class EncoderThroughputTest {
    @Test
    fun encoderSustains58fpsAt1080p() {
        val encoder = MediaCodecVideoEncoder()
        val samplePtsUs = CopyOnWriteArrayList<Long>()
        val formatReady = CountDownLatch(1)
        val listener =
            object : VideoEncoder.Listener {
                override fun onOutputFormatReady(format: MediaFormat) {
                    formatReady.countDown()
                }

                override fun onSample(sample: EncodedSample) {
                    samplePtsUs += sample.presentationTimeUs
                }

                override fun onError(error: Throwable): Unit = throw AssertionError("인코더 오류", error)
            }
        val surface =
            encoder.prepare(
                VideoEncoderConfig(
                    resolution = Resolution(1920, 1080),
                    frameRateFps = FrameRate.FPS_60.fps,
                    bitrateBps = TEST_BITRATE_BPS,
                    codec = VideoCodec.H264,
                ),
                listener,
            )
        encoder.start()
        try {
            renderFramesAt60Fps(surface, TOTAL_FRAMES)
            assertTrue("인코더 출력 포맷 미확정", formatReady.await(5, TimeUnit.SECONDS))
            waitForSamples(samplePtsUs, minimumCount = TOTAL_FRAMES - FRAME_TOLERANCE)
        } finally {
            encoder.stopAndRelease()
        }

        val pts = samplePtsUs.sorted()
        val spanSeconds = (pts.last() - pts.first()) / MICROS_PER_SECOND
        val averageFps = (pts.size - 1) / spanSeconds
        assertTrue(
            "평균 fps가 게이트 미달: ${"%.1f".format(averageFps)}fps (${pts.size}프레임/${spanSeconds}s)",
            averageFps >= MIN_AVERAGE_FPS,
        )
    }

    /** 프레임마다 색을 바꿔 클리어해 정적 화면 최적화(빈 프레임 스킵)를 배제한다. */
    private fun renderFramesAt60Fps(
        surface: Surface,
        frameCount: Int,
    ) {
        val egl = TestEgl(surface)
        try {
            val startNanos = System.nanoTime()
            for (frameIndex in 0 until frameCount) {
                val dueNanos = startNanos + frameIndex * FRAME_INTERVAL_NANOS
                while (System.nanoTime() < dueNanos) {
                    // 60fps 페이스 유지 (바쁜 대기 — 계측 테스트 전용)
                }
                val hue = (frameIndex % COLOR_CYCLE) / COLOR_CYCLE.toFloat()
                GLES20.glClearColor(hue, 1f - hue, 0.5f, 1f)
                GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
                EGLExt.eglPresentationTimeANDROID(egl.display, egl.eglSurface, System.nanoTime())
                EGL14.eglSwapBuffers(egl.display, egl.eglSurface)
            }
        } finally {
            egl.release()
        }
    }

    private fun waitForSamples(
        samples: List<Long>,
        minimumCount: Int,
    ) {
        val deadline = System.currentTimeMillis() + DRAIN_TIMEOUT_MS
        while (samples.size < minimumCount && System.currentTimeMillis() < deadline) {
            Thread.sleep(DRAIN_POLL_MS)
        }
    }

    /** 테스트 전용 최소 EGL 컨텍스트 (색상 클리어만 하므로 셰이더 불필요). */
    private class TestEgl(
        surface: Surface,
    ) {
        val display: EGLDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        private val context: android.opengl.EGLContext
        val eglSurface: android.opengl.EGLSurface

        init {
            val version = IntArray(2)
            check(EGL14.eglInitialize(display, version, 0, version, 1)) { "EGL 초기화 실패" }
            val configs = arrayOfNulls<EGLConfig>(1)
            val count = IntArray(1)
            check(
                EGL14.eglChooseConfig(
                    display,
                    intArrayOf(
                        EGL14.EGL_RED_SIZE,
                        8,
                        EGL14.EGL_GREEN_SIZE,
                        8,
                        EGL14.EGL_BLUE_SIZE,
                        8,
                        EGL14.EGL_RENDERABLE_TYPE,
                        EGL14.EGL_OPENGL_ES2_BIT,
                        EGL_RECORDABLE_ANDROID,
                        1,
                        EGL14.EGL_NONE,
                    ),
                    0,
                    configs,
                    0,
                    1,
                    count,
                    0,
                ) &&
                    count[0] > 0,
            ) { "EGL 설정 없음" }
            context =
                EGL14.eglCreateContext(
                    display,
                    configs[0],
                    EGL14.EGL_NO_CONTEXT,
                    intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE),
                    0,
                )
            eglSurface =
                EGL14.eglCreateWindowSurface(display, configs[0], surface, intArrayOf(EGL14.EGL_NONE), 0)
            check(EGL14.eglMakeCurrent(display, eglSurface, eglSurface, context)) { "eglMakeCurrent 실패" }
        }

        fun release() {
            EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
            EGL14.eglDestroySurface(display, eglSurface)
            EGL14.eglDestroyContext(display, context)
            EGL14.eglTerminate(display)
        }

        private companion object {
            const val EGL_RECORDABLE_ANDROID = 0x3142
        }
    }

    private companion object {
        const val TOTAL_FRAMES = 300
        const val FRAME_TOLERANCE = 10
        const val FRAME_INTERVAL_NANOS = 16_666_667L
        const val MIN_AVERAGE_FPS = 58.0
        const val TEST_BITRATE_BPS = 15_000_000
        const val MICROS_PER_SECOND = 1_000_000.0
        const val COLOR_CYCLE = 60
        const val DRAIN_TIMEOUT_MS = 10_000L
        const val DRAIN_POLL_MS = 50L
    }
}
