package io.rami.screenrecorder.data.recorder

import android.content.Context
import android.graphics.Bitmap
import android.hardware.display.DisplayManager
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Handler
import android.os.HandlerThread
import dagger.hilt.android.qualifiers.ApplicationContext
import io.rami.screenrecorder.data.storage.MediaStoreQuickCaptureStore
import io.rami.screenrecorder.domain.model.CapturedImage
import io.rami.screenrecorder.domain.model.RecordingFileNameFactory
import io.rami.screenrecorder.domain.repository.ScreenshotRepository
import io.rami.screenrecorder.domain.repository.SettingsRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/**
 * MediaProjection + ImageReader로 화면 한 장을 캡처한다 (기능명세서 12절).
 *
 * Android 14+는 startForeground(mediaProjection) 이후에만 프로젝션을 열 수 있으므로
 * 이 저장소는 반드시 포그라운드 서비스 안에서 호출돼야 한다.
 */
@Singleton
class MediaProjectionScreenshotRepository
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val tokenHolder: MediaProjectionTokenHolder,
        private val displayInfoProvider: DisplayInfoProvider,
        private val settingsRepository: SettingsRepository,
        private val quickCaptureStore: MediaStoreQuickCaptureStore,
    ) : ScreenshotRepository {
        override suspend fun capture(): Result<CapturedImage> =
            runCatching {
                val projection = openProjection()
                val bitmap =
                    try {
                        captureFrame(projection)
                    } finally {
                        projection.stop()
                    }
                try {
                    quickCaptureStore.publishImage(bitmap, nextFileName())
                } finally {
                    bitmap.recycle()
                }
            }

        private fun openProjection(): MediaProjection {
            val (resultCode, data) = tokenHolder.consume()
            val manager = context.getSystemService(MediaProjectionManager::class.java)
            return checkNotNull(manager.getMediaProjection(resultCode, data)) {
                "MediaProjection을 열 수 없다"
            }
        }

        /** VirtualDisplay에서 첫 프레임을 받아 Bitmap으로 바꾼다. */
        private suspend fun captureFrame(projection: MediaProjection): Bitmap {
            // 동의 직후 첫 프레임에는 사라지는 중인 시스템 동의 다이얼로그가 찍힌다.
            // 사용자가 원한 것은 그 아래 화면이므로 다이얼로그가 완전히 걷힐 시간을 준다
            // (레이스 회피가 아니라 "무엇을 캡처할지"에 대한 명시적 정책 — 기능명세서 12절 [결정]).
            delay(DIALOG_DISMISS_SETTLE_MILLIS)
            val resolution = displayInfoProvider.currentResolution()
            val imageReader =
                ImageReader.newInstance(
                    resolution.width,
                    resolution.height,
                    android.graphics.PixelFormat.RGBA_8888,
                    MAX_IMAGES,
                )
            val callbackThread = HandlerThread(CAPTURE_THREAD_NAME).apply { start() }
            val handler = Handler(callbackThread.looper)
            // Android 14+는 VirtualDisplay 생성 전에 콜백을 등록해야 한다.
            projection.registerCallback(object : MediaProjection.Callback() {}, handler)
            val virtualDisplay =
                projection.createVirtualDisplay(
                    VIRTUAL_DISPLAY_NAME,
                    resolution.width,
                    resolution.height,
                    context.resources.displayMetrics.densityDpi,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                    imageReader.surface,
                    null,
                    handler,
                )
            try {
                val bitmap =
                    withTimeoutOrNull(FRAME_TIMEOUT_MILLIS) { awaitFirstFrame(imageReader, handler) }
                return checkNotNull(bitmap) { "화면 프레임을 받지 못했다 (${FRAME_TIMEOUT_MILLIS}ms 초과)" }
            } finally {
                virtualDisplay?.release()
                imageReader.close()
                callbackThread.quitSafely()
            }
        }

        private suspend fun awaitFirstFrame(
            imageReader: ImageReader,
            handler: Handler,
        ): Bitmap =
            suspendCoroutine { continuation ->
                imageReader.setOnImageAvailableListener({ reader ->
                    val image = reader.acquireLatestImage() ?: return@setOnImageAvailableListener
                    // 첫 프레임만 쓰므로 리스너를 즉시 해제해 중복 resume을 막는다.
                    reader.setOnImageAvailableListener(null, null)
                    val bitmap = image.use { it.toBitmap() }
                    continuation.resume(bitmap)
                }, handler)
            }

        private suspend fun nextFileName(): String =
            RecordingFileNameFactory.create(
                prefix = settingsRepository.settings.first().fileNamePrefix,
                timestamp = LocalDateTime.now(),
                existingNames = quickCaptureStore.existingImageNames(),
                extension = RecordingFileNameFactory.IMAGE_EXTENSION,
            )

        private companion object {
            const val VIRTUAL_DISPLAY_NAME = "ScreenRecorderScreenshot"
            const val CAPTURE_THREAD_NAME = "ScreenshotCapture"
            const val MAX_IMAGES = 2

            /** 시스템 동의 다이얼로그가 사라지기를 기다리는 시간 (기능명세서 12절 [결정]). */
            const val DIALOG_DISMISS_SETTLE_MILLIS = 500L
            const val FRAME_TIMEOUT_MILLIS = 3_000L
        }
    }

/**
 * ImageReader 이미지를 Bitmap으로 변환한다.
 *
 * ImageReader는 행 단위로 패딩(rowStride)을 넣을 수 있어 폭보다 넓은 비트맵으로 받은 뒤
 * 실제 화면 크기만큼 잘라내야 오른쪽에 검은 띠가 생기지 않는다.
 */
private fun android.media.Image.toBitmap(): Bitmap {
    val plane = planes[0]
    val pixelStride = plane.pixelStride
    val rowPadding = plane.rowStride - pixelStride * width
    val paddedWidth = width + rowPadding / pixelStride
    val padded =
        Bitmap.createBitmap(paddedWidth, height, Bitmap.Config.ARGB_8888).apply {
            copyPixelsFromBuffer(plane.buffer)
        }
    if (paddedWidth == width) return padded
    val cropped = Bitmap.createBitmap(padded, 0, 0, width, height)
    padded.recycle()
    return cropped
}
