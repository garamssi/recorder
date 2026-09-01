package io.rami.screenrecorder.service

import android.content.ComponentName
import android.content.Intent
import android.content.IntentFilter
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.TextView
import io.rami.screenrecorder.domain.model.Recording
import io.rami.screenrecorder.domain.model.RecordingId
import io.rami.screenrecorder.domain.model.RecordingState
import io.rami.screenrecorder.domain.model.Resolution
import io.rami.screenrecorder.domain.model.VideoCodec
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadow.api.Shadow
import org.robolectric.shadows.ShadowSettings
import org.robolectric.shadows.ShadowToast
import org.robolectric.shadows.ShadowWindowManagerImpl
import java.time.Duration
import kotlin.time.Duration.Companion.minutes

/**
 * 발행이 확정되면 화면 위에 완료 배너가 뜬다 (기능명세서 6.1절 [결정]).
 *
 * 알림만으로는 화면에서 아무 일도 일어나지 않는다. 사용자는 다른 앱을 보고 있고 알림
 * 그림자는 접혀 있다 — 녹화가 저장됐는지 확인하려고 앱을 열어야 한다면 없는 표시와 같다.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "ko-w1280dp-h800dp")
class SaveCompleteBannerTest {
    private val context get() = RuntimeEnvironment.getApplication()

    private val windowShadow: ShadowWindowManagerImpl
        get() = Shadow.extract(context.getSystemService(WindowManager::class.java))

    /**
     * 완료 알림은 앱을 여는 PendingIntent 를 담는다. 테스트 환경에는 런처 액티비티가 없어
     * 그 조회가 실패하므로, 실제 앱과 같은 진입점을 등록해 둔다.
     */
    @Before
    fun registerLauncherActivity() {
        val launcher = ComponentName(context, "io.rami.screenrecorder.MainActivity")
        shadowOf(context.packageManager).run {
            addActivityIfNotPresent(launcher)
            addIntentFilterForActivity(
                launcher,
                IntentFilter(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_LAUNCHER) },
            )
        }
    }

    private class FakeBanner : SaveCompleteBanner {
        val shown = mutableListOf<String>()
        var dismissCount = 0

        override fun show(fileName: String) {
            shown += fileName
        }

        override fun dismiss() {
            dismissCount++
        }
    }

    private fun settle() = shadowOf(Looper.getMainLooper()).idle()

    private fun advanceMillis(millis: Long) = shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(millis))

    private fun View.allTexts(): List<String> =
        when (this) {
            is TextView -> listOf(text.toString())
            is ViewGroup -> (0 until childCount).flatMap { getChildAt(it).allTexts() }
            else -> emptyList()
        }

    // --- 시점: 발행이 확정될 때만 ---

    @Test
    fun `발행이 확정되면 완료 배너를 띄운다`() =
        runTest {
            val banner = FakeBanner()

            presenter(banner).observeCompletion(flowOf(SAVED))

            assertEquals(listOf(FILE_NAME), banner.shown)
        }

    /** 발행이 아무것도 남기지 않은 세션(빈 녹화·발행 실패)은 이 흐름에 흐르지 않는다. */
    @Test
    fun `발행이 확정되지 않으면 배너를 띄우지 않는다`() =
        runTest {
            val banner = FakeBanner()

            presenter(banner).observeCompletion(emptyFlow())

            assertEquals(emptyList<String>(), banner.shown)
        }

    /** 지난 녹화의 완료 배너가 새 녹화의 첫 프레임에 찍히면 안 된다. */
    @Test
    fun `새 세션이 시작되면 배너를 내린다`() =
        runTest {
            val banner = FakeBanner()

            presenter(banner).observeState(flowOf(RecordingState.Preparing))

            assertEquals(1, banner.dismissCount)
        }

    // --- 내용: 무엇이 저장됐는지 ---

    @Test
    fun `배너는 완료 문구와 저장된 파일 이름을 함께 보여 준다`() {
        val texts = context.buildSaveCompleteBanner(FILE_NAME).allTexts()

        assertTrue("완료 문구가 없다: $texts", context.getString(R.string.save_complete_banner) in texts)
        assertTrue("파일 이름이 없다: $texts", FILE_NAME in texts)
    }

    /** 누를 것이 없으므로 터치를 받을 이유도 없다 — 배너 아래의 앱이 그대로 눌려야 한다. */
    @Test
    fun `배너는 아래 앱의 터치를 가로채지 않는다`() {
        val params = saveCompleteLayoutParams(topOffsetPx = 0)

        assertTrue(
            "FLAG_NOT_TOUCHABLE 이 없다",
            params.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE != 0,
        )
    }

    // --- 수명: 스스로 사라진다 ---

    @Test
    fun `배너는 정해진 시간 동안만 머문다`() {
        ShadowSettings.setCanDrawOverlays(true)

        SaveCompleteOverlayWindow(context).show(FILE_NAME)
        settle()
        // 시간을 재는 테스트여야 한다. "충분히 오래 뒤에 없다"만 보면 3초가 30ms 가 돼도 통과한다.
        // 정확한 경계 1ms 는 섀도 시계 구현에 달려 있어 고정하지 않는다 — 500ms 여유로도
        // 3초가 300ms 나 30초가 되는 회귀는 잡힌다.
        advanceMillis(SAVE_COMPLETE_DISPLAY_MILLIS - MARGIN_MILLIS)
        assertEquals("아직 사라지면 안 된다", 1, windowShadow.views.size)

        advanceMillis(MARGIN_MILLIS * 2)
        assertEquals(0, windowShadow.views.size)
    }

    /**
     * 앞 배너가 예약해 둔 제거가 뒤 배너를 잡아먹으면 안 된다.
     *
     * 제거 예약은 "지금 떠 있는 창"이 아니라 자기가 띄운 배너를 지목해야 한다.
     */
    @Test
    fun `연달아 저장해도 뒤 배너가 제 수명을 산다`() {
        ShadowSettings.setCanDrawOverlays(true)
        val window = SaveCompleteOverlayWindow(context)
        window.show(FILE_NAME)
        settle()
        advanceMillis(SAVE_COMPLETE_DISPLAY_MILLIS - 100)

        window.show(OTHER_FILE_NAME)
        settle()
        advanceMillis(200)

        assertEquals("앞 배너의 예약이 뒤 배너를 지웠다", 1, windowShadow.views.size)
    }

    @Test
    fun `배너를 내리면 창이 사라진다`() {
        ShadowSettings.setCanDrawOverlays(true)
        val window = SaveCompleteOverlayWindow(context)
        window.show(FILE_NAME)
        settle()

        window.dismiss()
        settle()

        assertEquals(0, windowShadow.views.size)
    }

    /** 오버레이 권한이 없으면 화면에 그릴 수단이 토스트뿐이다. 그래도 내용은 같아야 한다. */
    @Test
    fun `오버레이 권한이 없으면 파일명까지 담은 토스트로 대신한다`() {
        ShadowSettings.setCanDrawOverlays(false)

        SaveCompleteOverlayWindow(context).show(FILE_NAME)
        settle()

        assertEquals(0, windowShadow.views.size)
        val toast = ShadowToast.getTextOfLatestToast()
        assertTrue("토스트가 없다", toast != null)
        assertTrue("완료 문구가 없다: $toast", context.getString(R.string.save_complete_banner) in toast)
        assertTrue("파일 이름이 없다: $toast", FILE_NAME in toast)
    }

    private fun presenter(banner: SaveCompleteBanner) =
        RecordingSessionPresenter(
            context = context,
            notifications = RecordingNotifications(context),
            countdownOverlay = CountdownOverlayWindow(context),
            saveCompleteBanner = banner,
            onIdle = {},
            onSkipCountdown = {},
        )

    private companion object {
        const val FILE_NAME = "Rec_20260901_120000.mp4"
        const val OTHER_FILE_NAME = "Rec_20260901_120500.mp4"

        /** 표시 시간 경계를 재는 여유. 섀도 시계의 1ms 경계 의미에 기대지 않는다. */
        const val MARGIN_MILLIS = 500L

        val SAVED =
            Recording(
                id = RecordingId(7L),
                displayName = FILE_NAME,
                contentUri = "content://media/external/video/media/7",
                sizeBytes = 12_345L,
                duration = 3.minutes,
                resolution = Resolution.FHD,
                frameRate = 60,
                codec = VideoCodec.H264,
                createdAtEpochMillis = 1_788_800_000_000L,
                bitrateBps = 15_000_000,
            )
    }
}
