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

    /** 시스템이 창을 거부하는 상황. Robolectric 으로는 addView 를 던지게 만들 수 없다. */
    private object RejectingWindows : OverlayWindows {
        override fun attach(
            view: View,
            params: WindowManager.LayoutParams,
        ): Boolean = false

        override fun detach(view: View) = Unit
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

    /**
     * 발행 실패와 빈 세션(프레임 0개)도 `Stopping -> Idle` 로 똑같이 끝난다. 상태 전이로
     * 판정하면 저장되지 않은 녹화를 "저장했습니다" 로 알린다 (기능명세서 2.1절 [결정]).
     */
    @Test
    fun `저장되지 않고 중지만 끝나면 배너를 띄우지 않는다`() =
        runTest {
            val banner = FakeBanner()

            presenter(banner).observeState(
                flowOf(
                    RecordingState.Stopping(elapsed = 3.minutes, fileName = FILE_NAME),
                    RecordingState.Idle,
                ),
            )

            assertEquals(emptyList<String>(), banner.shown)
        }

    /** 지난 녹화의 완료 배너가 새 녹화의 첫 프레임에 찍히면 안 된다. */
    @Test
    fun `준비 구간에 들어가면 배너를 내린다`() =
        runTest {
            val banner = FakeBanner()

            presenter(banner).observeState(flowOf(RecordingState.Preparing))

            assertEquals(1, banner.dismissCount)
        }

    /** 카운트다운을 켠 설정이 기본값이므로 이쪽이 오히려 주 경로다. */
    @Test
    fun `카운트다운이 시작되면 배너를 내린다`() =
        runTest {
            val banner = FakeBanner()

            presenter(banner).observeState(flowOf(RecordingState.CountingDown(remainingSeconds = 3)))

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
        //
        // 1ms 경계를 고정하지 않는 이유는 섀도 시계가 부정확해서가 아니다. addView 가 실제
        // measure/layout 을 돌리며 시계를 수십 ms 밀어 올려, settle() 뒤에 읽는 기준점이 실제
        // 예약 시점보다 그만큼 뒤에 있다. 그 오차는 뷰 작업량과 머신 속도에 따라 달라진다.
        // 500ms 여유는 그 오차보다 훨씬 크고, 3초가 300ms 나 30초가 되는 회귀는 여전히 잡는다.
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
        // 남은 한 장이 뒤 배너여야 한다. 개수만 보면 앞 배너가 그대로 남는 회귀를 놓친다.
        assertTrue(
            "떠 있는 것이 뒤 배너가 아니다",
            OTHER_FILE_NAME in windowShadow.views.first().allTexts(),
        )
    }

    /**
     * 배너를 띄운 서비스는 발행 직후 스스로 접히고, 다음 세션은 새 서비스 인스턴스에서 시작된다.
     * 프레젠터를 두 번 만드는 것이 그 경계를 그대로 모형화한다.
     *
     * 이 테스트가 고정하는 것은 "배너를 공유하면 다음 세션이 지난 배너를 내린다" 는 동작이다.
     * 실제로 공유되는지(= `ServiceModule` 의 `@Singleton`)는 [ServiceModuleTest] 가 본다.
     */
    @Test
    fun `서비스가 바뀌어도 다음 세션이 지난 배너를 내린다`() =
        runTest {
            ShadowSettings.setCanDrawOverlays(true)
            val shared = SaveCompleteOverlayWindow(context)

            presenter(shared).observeCompletion(flowOf(SAVED))
            settle()
            assertEquals(1, windowShadow.views.size)

            presenter(shared).observeState(flowOf(RecordingState.Preparing))
            settle()

            assertEquals("다음 세션이 지난 배너를 내리지 못했다", 0, windowShadow.views.size)
        }

    /** 시스템이 권한 회수와 함께 창을 먼저 떼어 간 상황. 떼지 못한다고 죽어서는 안 된다. */
    @Test
    fun `붙은 적 없는 창을 떼도 죽지 않는다`() {
        SystemOverlayWindows(context).detach(View(context))
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

    /**
     * 권한 검사와 실제 창 붙이기는 스레드가 달라 그 사이 권한이 사라질 수 있다. 명세는 그 상황을
     * "오버레이 권한이 없으면 토스트로 대체한다" 로 규정하므로, 거부됐다고 아무것도 안 보여
     * 주어서는 안 된다 (기능명세서 6.1절 [결정]).
     */
    @Test
    fun `창이 거부되면 토스트로 넘어간다`() {
        ShadowSettings.setCanDrawOverlays(true)

        SaveCompleteOverlayWindow(context, windows = RejectingWindows).show(FILE_NAME)
        settle()

        assertEquals(0, windowShadow.views.size)
        val toast = ShadowToast.getTextOfLatestToast()
        assertTrue("거부됐는데 토스트도 없다", toast != null && FILE_NAME in toast)
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
