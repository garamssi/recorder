package io.rami.screenrecorder.data.recorder

import android.graphics.SurfaceTexture
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLExt
import android.opengl.EGLSurface
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.os.Handler
import android.os.HandlerThread
import android.view.Surface
import io.rami.screenrecorder.domain.model.CropGeometry
import io.rami.screenrecorder.domain.model.Resolution
import java.util.concurrent.CountDownLatch

/**
 * EGL + GLES 2.0 기반 [FrameProcessor] 구현 (기능명세서 2.2절 부분 영역).
 *
 * 캡처 프레임을 외부(OES) 텍스처로 받아 [CropGeometry.sourceRect]만 샘플링해
 * [CropGeometry.destViewport]에 그린다. 프레임 타임스탬프는 SurfaceTexture의
 * 타임스탬프(CLOCK_MONOTONIC)를 인코더 서피스에 그대로 전달해 A/V 동기를 유지한다.
 */
class GlFrameProcessor : FrameProcessor {
    private var thread: HandlerThread? = null
    private var handler: Handler? = null
    private var egl: EglBundle? = null
    private var inputTexture: SurfaceTexture? = null
    private var inputSurface: Surface? = null

    override fun start(
        outputSurface: Surface,
        sourceSize: Resolution,
        geometry: CropGeometry,
    ): Surface {
        val renderThread = HandlerThread(THREAD_NAME).also { it.start() }
        val renderHandler = Handler(renderThread.looper)
        thread = renderThread
        handler = renderHandler

        // GL 자원은 렌더 스레드에서만 만지고, 입력 Surface 반환은 동기로 기다린다.
        val ready = CountDownLatch(1)
        var failure: Throwable? = null
        renderHandler.post {
            try {
                val bundle = EglBundle.create(outputSurface)
                egl = bundle
                val texture =
                    SurfaceTexture(bundle.oesTextureId).apply {
                        setDefaultBufferSize(sourceSize.width, sourceSize.height)
                    }
                inputTexture = texture
                inputSurface = Surface(texture)
                texture.setOnFrameAvailableListener({ frameTexture ->
                    drawFrame(frameTexture, bundle, geometry)
                }, renderHandler)
            } catch (
                // 초기화 실패는 start() 호출자에게 그대로 전파한다 (은폐 금지).
                @Suppress("TooGenericExceptionCaught") error: Throwable,
            ) {
                failure = error
            } finally {
                ready.countDown()
            }
        }
        ready.await()
        failure?.let { initError ->
            // 초기화 실패 시 렌더 스레드를 남기지 않는다 (검수 #1: HandlerThread 누수)
            renderThread.quitSafely()
            thread = null
            handler = null
            throw initError
        }
        return checkNotNull(inputSurface)
    }

    private fun drawFrame(
        texture: SurfaceTexture,
        bundle: EglBundle,
        geometry: CropGeometry,
    ) {
        texture.updateTexImage()
        GLES20.glClearColor(0f, 0f, 0f, 1f)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        bundle.draw(geometry)
        // 캡처 타임스탬프(CLOCK_MONOTONIC ns)를 인코더 프레임 PTS로 그대로 사용한다.
        EGLExt.eglPresentationTimeANDROID(bundle.display, bundle.surface, texture.timestamp)
        EGL14.eglSwapBuffers(bundle.display, bundle.surface)
    }

    override fun stop() {
        val renderHandler = handler ?: return
        val done = CountDownLatch(1)
        renderHandler.post {
            try {
                inputTexture?.setOnFrameAvailableListener(null)
                inputSurface?.release()
                inputTexture?.release()
                egl?.release()
            } finally {
                done.countDown()
            }
        }
        done.await()
        thread?.quitSafely()
        thread = null
        handler = null
        egl = null
        inputTexture = null
        inputSurface = null
    }

    /** EGL 컨텍스트 + OES 셰이더 파이프라인 묶음. 렌더 스레드 전용. */
    private class EglBundle(
        val display: EGLDisplay,
        val context: EGLContext,
        val surface: EGLSurface,
        val program: Int,
        val oesTextureId: Int,
    ) {
        private val positionHandle = GLES20.glGetAttribLocation(program, "aPosition")
        private val texCoordHandle = GLES20.glGetAttribLocation(program, "aTexCoord")

        fun draw(geometry: CropGeometry) {
            GLES20.glUseProgram(program)
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, oesTextureId)

            val viewport = geometry.destViewport
            // 레터박스 바깥은 위 glClear의 검은색이 남는다 (명세 5절).
            GLES20.glViewport(viewport.x, viewport.y, viewport.width, viewport.height)

            val positions = fullQuadPositions()
            val texCoords = cropTexCoords(geometry)
            GLES20.glEnableVertexAttribArray(positionHandle)
            GLES20.glVertexAttribPointer(positionHandle, 2, GLES20.GL_FLOAT, false, 0, positions)
            GLES20.glEnableVertexAttribArray(texCoordHandle)
            GLES20.glVertexAttribPointer(texCoordHandle, 2, GLES20.GL_FLOAT, false, 0, texCoords)
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, VERTEX_COUNT)
            GLES20.glDisableVertexAttribArray(positionHandle)
            GLES20.glDisableVertexAttribArray(texCoordHandle)
        }

        /** 뷰포트 전체를 덮는 클립 좌표 사각형. */
        private fun fullQuadPositions() =
            floatBuffer(
                floatArrayOf(
                    -1f,
                    -1f,
                    1f,
                    -1f,
                    -1f,
                    1f,
                    1f,
                    1f,
                ),
            )

        /**
         * 크롭 영역의 텍스처 좌표. [CropGeometry]는 좌상단 원점이고
         * GL 텍스처는 좌하단 원점이므로 V축을 뒤집는다.
         */
        private fun cropTexCoords(geometry: CropGeometry): java.nio.FloatBuffer {
            val rect = geometry.sourceRect
            return floatBuffer(
                floatArrayOf(
                    rect.left,
                    rect.bottom,
                    rect.right,
                    rect.bottom,
                    rect.left,
                    rect.top,
                    rect.right,
                    rect.top,
                ),
            )
        }

        fun release() {
            GLES20.glDeleteProgram(program)
            GLES20.glDeleteTextures(1, intArrayOf(oesTextureId), 0)
            EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
            EGL14.eglDestroySurface(display, surface)
            EGL14.eglDestroyContext(display, context)
            EGL14.eglTerminate(display)
        }

        companion object {
            fun create(outputSurface: Surface): EglBundle {
                val display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
                check(display != EGL14.EGL_NO_DISPLAY) { "EGL 디스플레이를 얻지 못했다" }
                val version = IntArray(2)
                check(EGL14.eglInitialize(display, version, 0, version, 1)) { "EGL 초기화 실패" }

                val config = chooseConfig(display)
                val context =
                    EGL14.eglCreateContext(
                        display,
                        config,
                        EGL14.EGL_NO_CONTEXT,
                        intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE),
                        0,
                    )
                check(context != EGL14.EGL_NO_CONTEXT) { "EGL 컨텍스트 생성 실패" }

                val eglSurface =
                    EGL14.eglCreateWindowSurface(
                        display,
                        config,
                        outputSurface,
                        intArrayOf(EGL14.EGL_NONE),
                        0,
                    )
                check(eglSurface != EGL14.EGL_NO_SURFACE) { "EGL 윈도 서피스 생성 실패" }
                check(EGL14.eglMakeCurrent(display, eglSurface, eglSurface, context)) { "eglMakeCurrent 실패" }

                val program = buildProgram()
                return EglBundle(
                    display = display,
                    context = context,
                    surface = eglSurface,
                    program = program,
                    oesTextureId = createOesTexture(),
                )
            }

            private fun chooseConfig(display: EGLDisplay): EGLConfig {
                val attributes =
                    intArrayOf(
                        EGL14.EGL_RED_SIZE,
                        COLOR_BITS,
                        EGL14.EGL_GREEN_SIZE,
                        COLOR_BITS,
                        EGL14.EGL_BLUE_SIZE,
                        COLOR_BITS,
                        EGL14.EGL_RENDERABLE_TYPE,
                        EGL14.EGL_OPENGL_ES2_BIT,
                        EGL_RECORDABLE_ANDROID,
                        1,
                        EGL14.EGL_NONE,
                    )
                val configs = arrayOfNulls<EGLConfig>(1)
                val count = IntArray(1)
                check(EGL14.eglChooseConfig(display, attributes, 0, configs, 0, 1, count, 0) && count[0] > 0) {
                    "녹화 가능한 EGL 설정이 없다"
                }
                return checkNotNull(configs[0])
            }

            private fun buildProgram(): Int {
                val vertex = compileShader(GLES20.GL_VERTEX_SHADER, VERTEX_SHADER)
                val fragment = compileShader(GLES20.GL_FRAGMENT_SHADER, FRAGMENT_SHADER)
                val program = GLES20.glCreateProgram()
                GLES20.glAttachShader(program, vertex)
                GLES20.glAttachShader(program, fragment)
                GLES20.glLinkProgram(program)
                val linked = IntArray(1)
                GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linked, 0)
                check(linked[0] == GLES20.GL_TRUE) { "GL 프로그램 링크 실패: ${GLES20.glGetProgramInfoLog(program)}" }
                GLES20.glDeleteShader(vertex)
                GLES20.glDeleteShader(fragment)
                return program
            }

            private fun compileShader(
                type: Int,
                source: String,
            ): Int {
                val shader = GLES20.glCreateShader(type)
                GLES20.glShaderSource(shader, source)
                GLES20.glCompileShader(shader)
                val compiled = IntArray(1)
                GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compiled, 0)
                check(compiled[0] == GLES20.GL_TRUE) { "셰이더 컴파일 실패: ${GLES20.glGetShaderInfoLog(shader)}" }
                return shader
            }

            private fun createOesTexture(): Int {
                val ids = IntArray(1)
                GLES20.glGenTextures(1, ids, 0)
                GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, ids[0])
                GLES20.glTexParameteri(
                    GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
                    GLES20.GL_TEXTURE_MIN_FILTER,
                    GLES20.GL_LINEAR,
                )
                GLES20.glTexParameteri(
                    GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
                    GLES20.GL_TEXTURE_MAG_FILTER,
                    GLES20.GL_LINEAR,
                )
                return ids[0]
            }

            /** EGL_ANDROID_recordable 확장 (MediaCodec 입력 서피스 호환 필수). */
            private const val EGL_RECORDABLE_ANDROID = 0x3142
            private const val COLOR_BITS = 8

            private val VERTEX_SHADER =
                """
                attribute vec2 aPosition;
                attribute vec2 aTexCoord;
                varying vec2 vTexCoord;
                void main() {
                    gl_Position = vec4(aPosition, 0.0, 1.0);
                    vTexCoord = aTexCoord;
                }
                """.trimIndent()

            private val FRAGMENT_SHADER =
                """
                #extension GL_OES_EGL_image_external : require
                precision mediump float;
                varying vec2 vTexCoord;
                uniform samplerExternalOES uTexture;
                void main() {
                    gl_FragColor = texture2D(uTexture, vTexCoord);
                }
                """.trimIndent()
        }
    }

    private companion object {
        const val THREAD_NAME = "GlFrameProcessor"
        const val VERTEX_COUNT = 4

        fun floatBuffer(values: FloatArray): java.nio.FloatBuffer =
            java.nio.ByteBuffer
                .allocateDirect(values.size * Float.SIZE_BYTES)
                .order(java.nio.ByteOrder.nativeOrder())
                .asFloatBuffer()
                .put(values)
                .apply { position(0) }
    }
}
