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
import io.rami.screenrecorder.domain.model.Resolution
import io.rami.screenrecorder.domain.model.VideoCodec
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

        override fun show(fileName: String) {
            shown += fileName
        }
    }

    private fun settle() = shadowOf(Looper.getMainLooper()).idle()

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
    fun `배너는 창에 붙었다가 스스로 사라진다`() {
        ShadowSettings.setCanDrawOverlays(true)

        SaveCompleteOverlayWindow(context).show(FILE_NAME)
        settle()
        assertEquals(1, windowShadow.views.size)

        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(BANNER_LIFETIME_SECONDS))

        assertEquals(0, windowShadow.views.size)
    }

    /** 오버레이 권한이 없으면 화면에 그릴 수단이 토스트뿐이다. 창을 만들려 들면 예외가 난다. */
    @Test
    fun `오버레이 권한이 없으면 창을 만들지 않는다`() {
        ShadowSettings.setCanDrawOverlays(false)

        SaveCompleteOverlayWindow(context).show(FILE_NAME)
        settle()

        assertEquals(0, windowShadow.views.size)
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

        /** 배너가 머무는 시간보다 넉넉히 잡아 "사라졌다"를 확인한다. */
        const val BANNER_LIFETIME_SECONDS = 10L

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
