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
import io.rami.screenrecorder.domain.model.RecordingSessionEvent
import io.rami.screenrecorder.domain.model.RecordingState
import io.rami.screenrecorder.domain.model.Resolution
import io.rami.screenrecorder.domain.model.VideoCodec
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
class SaveOverlayTest {
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

    private class FakeOverlay : SaveOverlay {
        val saving = mutableListOf<Triple<String, String, Float?>>()
        val saved = mutableListOf<String>()
        var failedCount = 0
        var dismissCount = 0
        var endCount = 0

        override fun showSaving(
            elapsed: String,
            fileName: String,
            progress: Float?,
        ) {
            saving += Triple(elapsed, fileName, progress)
        }

        override fun showSaved(fileName: String) {
            saved += fileName
        }

        override fun dismiss() {
            dismissCount++
        }

        override fun showFailed() {
            failedCount++
        }

        override fun endSaving() {
            endCount++
        }
    }

    private fun settle() = shadowOf(Looper.getMainLooper()).idle()

    private fun advanceMillis(millis: Long) = shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(millis))

    private fun View.gauge(): SavingGaugeView = checkNotNull(gaugeOrNull()) { "게이지가 없다" }

    private fun View.gaugeOrNull(): SavingGaugeView? =
        when (this) {
            is SavingGaugeView -> this
            is ViewGroup -> (0 until childCount).firstNotNullOfOrNull { getChildAt(it).gaugeOrNull() }
            else -> null
        }

    /** 화면에 실제로 보이는 글자만 모은다. GONE 뷰까지 읽으면 "감췄는가" 를 못 잰다. */
    private fun View.visibleTexts(): List<String> =
        when {
            visibility != View.VISIBLE -> emptyList()
            this is TextView -> listOf(text.toString())
            this is ViewGroup -> (0 until childCount).flatMap { getChildAt(it).visibleTexts() }
            else -> emptyList()
        }

    private fun View.allTexts(): List<String> =
        when (this) {
            is TextView -> listOf(text.toString())
            is ViewGroup -> (0 until childCount).flatMap { getChildAt(it).allTexts() }
            else -> emptyList()
        }

    // --- 시점: 발행이 확정될 때만 ---

    @Test
    fun `발행이 확정되면 완료 표시로 바꾼다`() =
        runTest {
            val overlay = FakeOverlay()

            presenter(overlay).observeCompletion(flowOf(SAVED))

            assertEquals(listOf(FILE_NAME), overlay.saved)
        }

    /**
     * 발행 실패와 빈 세션(프레임 0개)도 `Stopping -> Idle` 로 똑같이 끝난다. 상태 전이로
     * 판정하면 저장되지 않은 녹화를 "저장했습니다" 로 알린다 (기능명세서 2.1절 [결정]).
     */
    @Test
    fun `저장되지 않고 중지만 끝나면 완료 표시를 띄우지 않는다`() =
        runTest {
            val overlay = FakeOverlay()

            presenter(overlay).observeState(
                flowOf(
                    RecordingState.Stopping(elapsed = 3.minutes, fileName = FILE_NAME),
                    RecordingState.Idle,
                ),
            )

            assertEquals(emptyList<String>(), overlay.saved)
        }

    /**
     * 발행이 실패하거나(SaveFailed) 저장할 내용이 없으면 완료가 흐르지 않은 채 유휴로 끝난다.
     * 그때 저장 중 카드를 내리지 않으면 "저장 중 87%" 가 화면에 영구히 남고 그 내내 다시 그린다.
     */
    @Test
    fun `발행이 확정되지 않은 채 끝나면 저장 중 표시를 내린다`() =
        runTest {
            val overlay = FakeOverlay()

            presenter(overlay).observeState(
                flowOf(
                    RecordingState.Stopping(elapsed = 3.minutes, fileName = FILE_NAME, progress = 0.87f),
                    RecordingState.Idle,
                ),
            )

            assertEquals(1, overlay.endCount)
        }

    /**
     * 성공을 화면에 알리기로 한 근거가 실패에는 더 세게 적용된다 — 저장된 줄 알고 넘어가는
     * 쪽이 저장 안 된 것을 모르고 넘어가는 쪽보다 나쁘다 (기능명세서 6.1절 [결정]).
     */
    @Test
    fun `발행이 실패하면 화면에도 알린다`() =
        runTest {
            val overlay = FakeOverlay()

            presenter(overlay).observeEvents(flowOf(RecordingSessionEvent.SaveFailed))

            assertEquals(1, overlay.failedCount)
        }

    /**
     * 실패 카드는 그때까지 그리던 것을 그대로 물려받는다 — 무엇이, 어디까지 가다 실패했는지가
     * 그때 필요한 정보다. 링을 채우지는 않는다(채우면 저장된 것으로 읽힌다).
     */
    @Test
    fun `실패 표시는 저장 중이던 값을 그대로 물려받는다`() {
        ShadowSettings.setCanDrawOverlays(true)
        val overlay = SaveOverlayWindow(context)
        overlay.showSaving(ELAPSED, FILE_NAME, progress = 0.87f)
        settle()

        overlay.showFailed()
        settle()

        val card = windowShadow.views.single()
        // 화면에 실제로 보이는 것만 센다 — GONE 뷰의 텍스트까지 읽으면 숨긴 회귀를 못 잡는다.
        val texts = card.visibleTexts()
        assertTrue("실패 문구가 없다: $texts", context.getString(R.string.save_failed_overlay) in texts)
        assertTrue("완료 문구가 떴다: $texts", context.getString(R.string.save_complete_banner) !in texts)
        assertTrue("파일명이 사라졌다: $texts", FILE_NAME in texts)
        assertTrue("길이가 사라졌다: $texts", ELAPSED in texts)
        assertEquals("진행률이 지워졌다", 0.87f, card.gauge().progress)
        assertFalse("실패인데 역회전이 돈다", card.gauge().spinning)
    }

    /** 권한이 없으면 화면에 그릴 수단이 토스트뿐이다. 실패야말로 알려야 한다. */
    @Test
    fun `권한이 없으면 실패도 토스트로 알린다`() {
        ShadowSettings.setCanDrawOverlays(false)
        val overlay = SaveOverlayWindow(context)
        overlay.showSaving(ELAPSED, FILE_NAME, progress = 0.87f)
        settle()

        overlay.showFailed()
        settle()

        // 두 문구가 모두 파일명을 담는다. 파일명만 보면 템플릿이 뒤집혀도 통과한다.
        assertEquals(
            context.getString(R.string.save_failed_toast, FILE_NAME),
            ShadowToast.getTextOfLatestToast(),
        )
    }

    /**
     * 결말이 새 세션의 dismiss 를 지나치면 안 된다.
     *
     * 결말을 그리는 데 메시지를 두 번 쓰면(읽기 한 번, 그리기 한 번) 그 사이에 들어온
     * dismiss 를 건너뛰어, 새 세션이 시작된 뒤에 지난 결말이 다시 붙는다.
     */
    @Test
    fun `새 세션이 시작되면 직전 결말이 다시 붙지 않는다`() {
        ShadowSettings.setCanDrawOverlays(true)
        val overlay = SaveOverlayWindow(context)

        overlay.showSaved(FILE_NAME)
        overlay.dismiss()
        settle()

        assertEquals("지난 결말이 새 세션 위에 붙었다", 0, windowShadow.views.size)
    }

    /**
     * 유휴가 실패보다 먼저 올 수 있다 — 코디네이터가 실패 이벤트를 흘린 직후 유휴로 간다.
     * 그때 물려줄 값까지 지우면 실패 카드가 빈 링·빈 중앙으로 뜬다.
     */
    @Test
    fun `유휴가 실패보다 먼저 와도 실패가 값을 물려받는다`() {
        ShadowSettings.setCanDrawOverlays(true)
        val overlay = SaveOverlayWindow(context)
        overlay.showSaving(ELAPSED, FILE_NAME, progress = 0.87f)
        settle()

        overlay.endSaving()
        settle()
        assertEquals("발행 중 표시가 남았다", 0, windowShadow.views.size)

        overlay.showFailed()
        settle()

        val card = windowShadow.views.single()
        assertTrue("파일명을 잃었다", FILE_NAME in card.visibleTexts())
        assertEquals("진행률을 잃었다", 0.87f, card.gauge().progress)
    }

    /**
     * 결말을 그린 뒤에는 물려줄 것이 없다.
     *
     * 남겨 두면 다음 세션이 준비 구간을 못 거친 채(상태 병합) 곧장 실패했을 때, 지난 녹화의
     * 이름과 진행률을 물려받은 실패 카드가 뜬다.
     */
    @Test
    fun `결말을 그린 뒤에는 물려줄 내용이 남지 않는다`() {
        ShadowSettings.setCanDrawOverlays(true)
        val overlay = SaveOverlayWindow(context)
        overlay.showSaving(ELAPSED, FILE_NAME, progress = 0.5f)
        settle()
        overlay.showSaved(FILE_NAME)
        settle()
        advanceMillis(SAVE_OVERLAY_DISPLAY_MILLIS + MARGIN_MILLIS)
        assertEquals(0, windowShadow.views.size)

        overlay.showFailed()
        settle()

        assertEquals("지난 발행의 내용을 물려받았다", 0, windowShadow.views.size)
    }

    /** 새 세션이 시작되면 지난 발행의 내용을 물려줄 곳이 없다. */
    @Test
    fun `새 세션이 시작되면 물려줄 내용도 버린다`() {
        ShadowSettings.setCanDrawOverlays(true)
        val overlay = SaveOverlayWindow(context)
        overlay.showSaving(ELAPSED, FILE_NAME, progress = 0.5f)
        settle()

        overlay.dismiss()
        settle()
        overlay.showFailed()
        settle()

        assertEquals("지난 발행의 내용을 물려받았다", 0, windowShadow.views.size)
    }

    /** 실패도 결말이다 — 뒤이어 오는 유휴가 표시를 즉시 지워서는 안 된다. */
    @Test
    fun `실패를 보여 준 뒤에도 유휴가 표시를 지우지 않는다`() {
        ShadowSettings.setCanDrawOverlays(true)
        val overlay = SaveOverlayWindow(context)
        overlay.showSaving(ELAPSED, FILE_NAME, progress = 0.87f)
        settle()
        overlay.showFailed()
        settle()

        overlay.endSaving()
        settle()

        assertEquals(1, windowShadow.views.size)
        advanceMillis(SAVE_OVERLAY_DISPLAY_MILLIS + MARGIN_MILLIS)
        assertEquals("스스로 접히지 않았다", 0, windowShadow.views.size)
    }

    /**
     * 유휴가 완료보다 먼저 도착할 수 있다 — 상태 흐름과 완료 흐름은 별개 수집기다.
     * 그때는 완료가 창을 다시 붙이고 제 수명을 살아야 한다.
     */
    @Test
    fun `유휴가 완료보다 먼저 와도 완료 표시가 제 수명을 산다`() {
        ShadowSettings.setCanDrawOverlays(true)
        val overlay = SaveOverlayWindow(context)
        overlay.showSaving(ELAPSED, FILE_NAME, progress = 0.9f)
        settle()

        overlay.endSaving()
        settle()
        assertEquals("발행 중 표시가 남았다", 0, windowShadow.views.size)

        overlay.showSaved(FILE_NAME)
        settle()
        assertEquals("완료가 다시 붙지 않았다", 1, windowShadow.views.size)

        advanceMillis(SAVE_OVERLAY_DISPLAY_MILLIS + MARGIN_MILLIS)
        assertEquals(0, windowShadow.views.size)
    }

    /** 성공했을 때는 완료 표시가 제 수명을 살아야 한다 — 유휴는 완료 직후에 온다. */
    @Test
    fun `완료를 보여 준 뒤에는 유휴가 와도 표시가 남는다`() {
        ShadowSettings.setCanDrawOverlays(true)
        val overlay = SaveOverlayWindow(context)
        overlay.showSaved(FILE_NAME)
        settle()

        overlay.endSaving()
        settle()

        assertEquals("완료 표시가 즉시 지워졌다", 1, windowShadow.views.size)
    }

    @Test
    fun `완료 없이 끝나면 창이 사라진다`() {
        ShadowSettings.setCanDrawOverlays(true)
        val overlay = SaveOverlayWindow(context)
        overlay.showSaving(ELAPSED, FILE_NAME, progress = 0.87f)
        settle()
        assertEquals(1, windowShadow.views.size)

        overlay.endSaving()
        settle()

        assertEquals(0, windowShadow.views.size)
    }

    /** 발행 중 표시가 없었다면 물려받을 것이 없다. 알림이 이미 사실을 전달했다. */
    @Test
    fun `저장 중을 그린 적 없으면 실패 표시도 띄우지 않는다`() {
        ShadowSettings.setCanDrawOverlays(true)
        val overlay = SaveOverlayWindow(context)

        overlay.showFailed()
        settle()

        assertEquals(0, windowShadow.views.size)
    }

    /** 지난 녹화의 완료 배너가 새 녹화의 첫 프레임에 찍히면 안 된다. */
    @Test
    fun `준비 구간에 들어가면 오버레이를 내린다`() =
        runTest {
            val overlay = FakeOverlay()

            presenter(overlay).observeState(flowOf(RecordingState.Preparing))

            assertEquals(1, overlay.dismissCount)
        }

    /** 카운트다운을 켠 설정이 기본값이므로 이쪽이 오히려 주 경로다. */
    @Test
    fun `카운트다운이 시작되면 오버레이를 내린다`() =
        runTest {
            val overlay = FakeOverlay()

            presenter(overlay).observeState(flowOf(RecordingState.CountingDown(remainingSeconds = 3)))

            assertEquals(1, overlay.dismissCount)
        }

    /**
     * 홈 카드는 앱 안에 있을 때만 보인다. 발행 2~4분 동안 사용자가 보는 화면은 다른 앱이므로
     * 진행률이 실제로 닿아야 하는 곳은 오버레이다 (기능명세서 6.1절 [결정]).
     */
    @Test
    fun `발행 중에는 경과 시간과 진행률을 오버레이에 실어 보낸다`() =
        runTest {
            val overlay = FakeOverlay()

            presenter(overlay).observeState(
                flowOf(RecordingState.Stopping(elapsed = 3.minutes, fileName = FILE_NAME, progress = 0.62f)),
            )

            assertEquals(listOf(Triple("03:00", FILE_NAME, 0.62f)), overlay.saving)
        }

    /** 발행이 끝날 때까지 계속 떠 있어야 한다 — 갱신마다 창을 다시 붙이면 링이 처음부터 돈다. */
    @Test
    fun `진행률이 갱신돼도 창은 하나로 유지된다`() {
        ShadowSettings.setCanDrawOverlays(true)
        val overlay = SaveOverlayWindow(context)

        overlay.showSaving(ELAPSED, FILE_NAME, progress = 0.1f)
        settle()
        val first = windowShadow.views.single()
        overlay.showSaving(ELAPSED, FILE_NAME, progress = 0.9f)
        settle()

        assertEquals(1, windowShadow.views.size)
        assertTrue("창을 새로 붙였다", first === windowShadow.views.single())
        assertTrue(
            "갱신한 진행률이 반영되지 않았다",
            windowShadow.views
                .single()
                .allTexts()
                .any { "90" in it },
        )
    }

    /**
     * 상태 흐름과 완료 흐름은 서로 다른 수집기에서 돈다. 마지막 진행률 갱신이 완료보다 늦게
     * 도착하면 완료 표시를 덮고 제거 예약까지 거둬 가, 카드가 "저장 중 100%" 로 굳는다.
     * 실기기에서 실제로 그렇게 굳었다.
     */
    @Test
    fun `완료 뒤 늦은 진행률 갱신이 완료 표시를 덮지 않는다`() {
        ShadowSettings.setCanDrawOverlays(true)
        val overlay = SaveOverlayWindow(context)
        overlay.showSaved(FILE_NAME)
        settle()

        overlay.showSaving(ELAPSED, FILE_NAME, progress = 1f)
        settle()

        val texts = windowShadow.views.single().allTexts()
        assertTrue("완료 문구가 사라졌다: $texts", context.getString(R.string.save_complete_banner) in texts)

        advanceMillis(SAVE_OVERLAY_DISPLAY_MILLIS + MARGIN_MILLIS)
        assertEquals("제거 예약이 거둬졌다", 0, windowShadow.views.size)
    }

    /** 저장 중에는 스스로 사라지면 안 된다 — 발행이 끝날 때까지가 사용자가 기다리는 시간이다. */
    @Test
    fun `저장 중 표시는 시간이 지나도 사라지지 않는다`() {
        ShadowSettings.setCanDrawOverlays(true)
        val overlay = SaveOverlayWindow(context)

        overlay.showSaving(ELAPSED, FILE_NAME, progress = 0.2f)
        settle()
        advanceMillis(SAVE_OVERLAY_DISPLAY_MILLIS * 3)

        assertEquals("발행이 끝나기 전에 사라졌다", 1, windowShadow.views.size)
    }

    // --- 내용: 무엇이 저장됐는지 ---

    /** 누를 것이 없으므로 터치를 받을 이유도 없다 — 배너 아래의 앱이 그대로 눌려야 한다. */
    @Test
    fun `오버레이는 아래 앱의 터치를 가로채지 않는다`() {
        val params = saveOverlayLayoutParams(widthPx = 100, topOffsetPx = 0)

        assertTrue(
            "FLAG_NOT_TOUCHABLE 이 없다",
            params.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE != 0,
        )
    }

    // --- 수명: 스스로 사라진다 ---

    @Test
    fun `완료 표시는 정해진 시간 동안만 머문다`() {
        ShadowSettings.setCanDrawOverlays(true)

        SaveOverlayWindow(context).showSaved(FILE_NAME)
        settle()
        // 시간을 재는 테스트여야 한다. "충분히 오래 뒤에 없다"만 보면 3초가 30ms 가 돼도 통과한다.
        //
        // 1ms 경계를 고정하지 않는 이유는 섀도 시계가 부정확해서가 아니다. addView 가 실제
        // measure/layout 을 돌리며 시계를 수십 ms 밀어 올려, settle() 뒤에 읽는 기준점이 실제
        // 예약 시점보다 그만큼 뒤에 있다. 그 오차는 뷰 작업량과 머신 속도에 따라 달라진다.
        // 500ms 여유는 그 오차보다 훨씬 크고, 3초가 300ms 나 30초가 되는 회귀는 여전히 잡는다.
        advanceMillis(SAVE_OVERLAY_DISPLAY_MILLIS - MARGIN_MILLIS)
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
    fun `연달아 저장해도 뒤 표시가 제 수명을 산다`() {
        ShadowSettings.setCanDrawOverlays(true)
        val window = SaveOverlayWindow(context)
        window.showSaved(FILE_NAME)
        settle()
        advanceMillis(SAVE_OVERLAY_DISPLAY_MILLIS - 100)

        window.showSaved(OTHER_FILE_NAME)
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
    fun `서비스가 바뀌어도 다음 세션이 지난 표시를 내린다`() =
        runTest {
            ShadowSettings.setCanDrawOverlays(true)
            val shared = SaveOverlayWindow(context)

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
    fun `오버레이를 내리면 창이 사라진다`() {
        ShadowSettings.setCanDrawOverlays(true)
        val window = SaveOverlayWindow(context)
        window.showSaved(FILE_NAME)
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

        SaveOverlayWindow(context, windows = RejectingWindows).showSaved(FILE_NAME)
        settle()

        assertEquals(0, windowShadow.views.size)
        val toast = ShadowToast.getTextOfLatestToast()
        assertTrue("거부됐는데 토스트도 없다", toast != null && FILE_NAME in toast)
    }

    /** 오버레이 권한이 없으면 화면에 그릴 수단이 토스트뿐이다. 그래도 내용은 같아야 한다. */
    @Test
    fun `오버레이 권한이 없으면 파일명까지 담은 토스트로 대신한다`() {
        ShadowSettings.setCanDrawOverlays(false)

        SaveOverlayWindow(context).showSaved(FILE_NAME)
        settle()

        assertEquals(0, windowShadow.views.size)
        val toast = ShadowToast.getTextOfLatestToast()
        assertTrue("토스트가 없다", toast != null)
        assertTrue("완료 문구가 없다: $toast", context.getString(R.string.save_complete_banner) in toast)
        assertTrue("파일 이름이 없다: $toast", FILE_NAME in toast)
    }

    private fun presenter(overlay: SaveOverlay) =
        RecordingSessionPresenter(
            context = context,
            notifications = RecordingNotifications(context),
            countdownOverlay = CountdownOverlayWindow(context),
            saveOverlay = overlay,
            onIdle = {},
            onSkipCountdown = {},
        )

    private companion object {
        const val ELAPSED = "03:42"
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
