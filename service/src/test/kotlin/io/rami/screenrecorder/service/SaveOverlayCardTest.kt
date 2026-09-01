package io.rami.screenrecorder.service

import android.text.TextUtils
import android.view.View
import android.view.View.MeasureSpec.UNSPECIFIED
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import io.rami.screenrecorder.core.common.design.SavingGaugeSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * 저장 오버레이 카드가 홈의 링 게이지와 같은 것을 보여 주는지 (DESIGN_GUIDE.md 4절).
 *
 * 홈 카드는 앱 안에 있을 때만 보인다. 녹화를 마친 사용자는 다른 앱에 있으므로 이 디자인이
 * 실제로 쓰이는 자리는 오버레이다.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "ko-w1280dp-h800dp")
class SaveOverlayCardTest {
    private val context get() = RuntimeEnvironment.getApplication()

    private fun View.allTexts(): List<String> =
        when (this) {
            is TextView -> listOf(text.toString())
            is ViewGroup -> (0 until childCount).flatMap { getChildAt(it).allTexts() }
            else -> emptyList()
        }

    private fun View.imageViews(): List<ImageView> =
        when (this) {
            is ImageView -> listOf(this)
            is ViewGroup -> (0 until childCount).flatMap { getChildAt(it).imageViews() }
            else -> emptyList()
        }

    /** 화면에 실제로 보이는 글자만 모은다. GONE 뷰의 텍스트까지 읽으면 "감췄는가" 를 못 잰다. */
    private fun View.visibleTexts(): List<String> =
        when {
            visibility != View.VISIBLE -> emptyList()
            this is TextView -> listOf(text.toString())
            this is ViewGroup -> (0 until childCount).flatMap { getChildAt(it).visibleTexts() }
            else -> emptyList()
        }

    private fun View.textViews(): List<TextView> =
        when (this) {
            is TextView -> listOf(this)
            is ViewGroup -> (0 until childCount).flatMap { getChildAt(it).textViews() }
            else -> emptyList()
        }

    private fun View.gauge(): SavingGaugeView = checkNotNull(gaugeOrNull()) { "게이지가 없다" }

    private fun View.gaugeOrNull(): SavingGaugeView? =
        when (this) {
            is SavingGaugeView -> this
            is ViewGroup -> (0 until childCount).firstNotNullOfOrNull { getChildAt(it).gaugeOrNull() }
            else -> null
        }

    private fun render(content: SaveOverlayContent): View = SaveOverlayCard(context).apply { render(content) }.root

    @Test
    fun `저장 중에는 경과 시간과 퍼센트와 파일명을 함께 보여 준다`() {
        val texts = render(saving(progress = 0.62f)).allTexts()

        assertTrue("경과 시간이 없다: $texts", ELAPSED in texts)
        assertTrue("퍼센트가 없다: $texts", texts.any { it.contains("62") })
        assertTrue("파일명이 없다: $texts", FILE_NAME in texts)
        assertTrue("상태 문구가 없다: $texts", context.getString(R.string.floating_saving) in texts)
    }

    /** 발행은 메타데이터 판독으로 시작하는데 그 구간에는 진행률 신호가 없다. */
    @Test
    fun `진행률을 아직 모르면 퍼센트를 감춘다`() {
        // 62% 를 한 번 보여 준 뒤 진행률을 잃는 순서를 재현한다. 갓 만든 카드만 보면 텍스트가
        // 비어 있어서 통과하므로, 감췄는지가 아니라 아직 그린 적 없는지를 재게 된다.
        val card = SaveOverlayCard(context)
        card.render(saving(progress = 0.62f))

        card.render(saving(progress = null))

        val visible = card.root.visibleTexts()
        assertTrue("길이는 그대로 보여야 한다: $visible", ELAPSED in visible)
        assertTrue("퍼센트가 아직 보인다: $visible", visible.none { "%" in it })
    }

    /** 다 끝난 값에 퍼센트를 남겨 두면 아직 진행 중으로 읽힌다. */
    @Test
    fun `발행이 확정되면 퍼센트 대신 체크를 보여 준다`() {
        val card = render(SaveOverlayContent(ELAPSED, FILE_NAME, progress = 1f, SaveOutcome.SAVED))

        val visible = card.visibleTexts()
        assertTrue("완료 문구가 없다: $visible", context.getString(R.string.save_complete_banner) in visible)
        assertTrue("퍼센트가 아직 보인다: $visible", visible.none { "%" in it })
        assertTrue("체크 아이콘이 없다", card.imageViews().any { it.visibility == View.VISIBLE })
    }

    /**
     * 진행률은 0.5% 단위로 올라와 발행 2~4분 동안 수백 번 갱신된다. 그때마다 뷰를 새로 만들면
     * 링 애니메이션이 매번 처음부터 시작한다.
     */
    @Test
    fun `값을 갱신해도 링을 새로 만들지 않는다`() {
        val card = SaveOverlayCard(context)
        card.render(saving(progress = 0.1f))
        val firstGauge = card.root.gauge()

        card.render(saving(progress = 0.9f))

        // 링을 새로 만들면 후광 맥동과 역회전이 매번 처음부터 시작한다.
        assertTrue("갱신이 링을 새로 만들었다", firstGauge === card.root.gauge())
        assertTrue("갱신한 값이 반영되지 않았다", card.root.allTexts().any { it.contains("90") })
    }

    // --- 링에 실제로 연결돼 있는가 (DESIGN_GUIDE.md 4절) ---

    @Test
    fun `진행률이 링에 그대로 전달된다`() {
        val card = render(saving(progress = 0.42f))

        assertEquals(0.42f, card.gauge().progress)
        assertTrue("발행 중에는 역회전 원호가 돌아야 한다", card.gauge().spinning)
    }

    /** 다 끝났는데 원호가 계속 돌면 아직 일하는 중으로 읽힌다. */
    @Test
    fun `발행이 확정되면 링이 꽉 차고 역회전이 멈춘다`() {
        val card = render(SaveOverlayContent(ELAPSED, FILE_NAME, progress = 0.4f, SaveOutcome.SAVED))

        assertEquals(1f, card.gauge().progress)
        assertFalse("완료인데 역회전이 돈다", card.gauge().spinning)
    }

    /**
     * 실패한 진행률을 100%로 채우면 저장된 것으로 읽힌다 (기능명세서 6.1절 [결정]).
     *
     * 입력은 실제 경로와 같다 — 창이 저장 중이던 내용을 그대로 물려주므로 진행률이 실려 온다.
     */
    @Test
    fun `실패하면 링을 채우지 않고 멈추기만 한다`() {
        val card = render(SaveOverlayContent(ELAPSED, FILE_NAME, progress = 0.87f, SaveOutcome.FAILED))

        assertEquals(0.87f, card.gauge().progress)
        assertFalse("실패인데 역회전이 돈다", card.gauge().spinning)
        assertTrue("완료 문구가 떴다", context.getString(R.string.save_complete_banner) !in card.allTexts())
        assertTrue("실패 문구가 없다", context.getString(R.string.save_failed_overlay) in card.allTexts())
    }

    /**
     * 한 시간을 넘으면 "HH:MM:SS" 여덟 자가 되어 40sp 로는 링 폭을 넘는다 — 홈이 자릿수에 따라
     * 크기를 낮추는 이유다. 그리고 발행이 오래 걸리는 것이 바로 그 긴 녹화라, 이 오버레이가
     * 가장 오래 떠 있는 경우가 곧 깨지는 경우다.
     */
    @Test
    fun `한 시간을 넘는 길이는 글자를 줄여 링 안에 담는다`() {
        val short = render(saving(progress = 0.5f)).textViews().single { it.text == ELAPSED }

        val longCard = SaveOverlayCard(context)
        longCard.render(SaveOverlayContent(LONG_ELAPSED, FILE_NAME, 0.5f, SaveOutcome.IN_PROGRESS))
        val long = longCard.root.textViews().single { it.text == LONG_ELAPSED }

        assertTrue("여덟 자인데 글자가 줄지 않았다", long.textSize < short.textSize)
    }

    /**
     * 카드 폭은 링이 정한다 — 내용이 정하면 국면마다 카드가 옆으로 늘었다 줄었다 한다.
     *
     * 세로 LinearLayout 은 WRAP_CONTENT 일 때 MATCH_PARENT 자식을 폭 계산에서 뺀다. 링을 담은
     * 프레임이 기본 LayoutParams 로 MATCH_PARENT 가 되면 폭에 기여하지 못하고, 상태 문구와
     * 파일명이 카드 폭을 정하게 된다. 그러면 링이 그 폭으로 눌려 좌우가 잘린다.
     */
    @Test
    fun `카드 폭은 내용이 바뀌어도 그대로다`() {
        val saving = measuredWidth(saving(progress = 0.9f))
        val saved = measuredWidth(SaveOverlayContent(ELAPSED, FILE_NAME, 1f, SaveOutcome.SAVED))
        val failed = measuredWidth(SaveOverlayContent(ELAPSED, FILE_NAME, 0.9f, SaveOutcome.FAILED))

        val glowDiameterDp = SavingGaugeSpec.RING_DP * SavingGaugeSpec.GLOW_RADIUS_SCALE
        assertTrue("링이 카드 밖으로 잘린다", saving >= context.dpToPx(glowDiameterDp))
        assertEquals("완료로 바뀌며 카드가 늘었다", saving, saved)
        assertEquals("실패로 바뀌며 카드가 늘었다", saving, failed)
    }

    /** 창이 폭을 못 박아야 국면이 바뀌어도 카드가 흔들리지 않는다. */
    @Test
    fun `창은 고정 폭으로 열린다`() {
        val params = saveOverlayLayoutParams(widthPx = context.dpToPx(SavingGaugeSpec.CARD_WIDTH_DP), topOffsetPx = 0)

        assertEquals(context.dpToPx(SavingGaugeSpec.CARD_WIDTH_DP), params.width)
        val glowDiameterDp = SavingGaugeSpec.RING_DP * SavingGaugeSpec.GLOW_RADIUS_SCALE
        assertTrue("링이 카드 폭을 넘는다", params.width >= context.dpToPx(glowDiameterDp))
    }

    /** 이름이 길어도 카드가 넓어지면 안 된다. 화면 밖으로 나가고 국면마다 크기가 흔들린다. */
    @Test
    fun `긴 파일명이 카드를 넓히지 않는다`() {
        val short = measuredWidth(saving(progress = 0.5f))
        val long = measuredWidth(SaveOverlayContent(ELAPSED, LONG_FILE_NAME, 0.5f, SaveOutcome.IN_PROGRESS))

        assertEquals("긴 파일명이 카드를 늘렸다", short, long)
        val fileName = render(saving(progress = 0.5f)).textViews().single { it.text == FILE_NAME }
        assertEquals(TextUtils.TruncateAt.MIDDLE, fileName.ellipsize)
    }

    private fun measuredWidth(content: SaveOverlayContent): Int =
        render(content).also { it.measure(UNSPECIFIED, UNSPECIFIED) }.measuredWidth

    /**
     * 뷰가 링 크기로 재면 후광이 사각으로 잘린다 — 플랫폼 뷰는 Compose 와 달리 자기 경계에서
     * 자르기 때문이다. 실기기에서 붉은 얼룩으로 드러났던 회귀다.
     */
    @Test
    fun `링 뷰는 후광이 들어갈 만큼 크다`() {
        val gauge = render(saving(progress = 0.5f)).gauge()

        gauge.measure(UNSPECIFIED, UNSPECIFIED)

        val glowDiameterDp = SavingGaugeSpec.RING_DP * SavingGaugeSpec.GLOW_RADIUS_SCALE
        val expected = context.dpToPx(glowDiameterDp)
        assertEquals(expected, gauge.measuredWidth)
    }

    private fun saving(progress: Float?) =
        SaveOverlayContent(ELAPSED, FILE_NAME, progress = progress, SaveOutcome.IN_PROGRESS)

    private companion object {
        const val ELAPSED = "03:42"
        const val LONG_ELAPSED = "01:02:03"
        const val LONG_FILE_NAME = "Rec_20260901_120000_아주_긴_이름을_가진_녹화본_파일입니다.mp4"
        const val FILE_NAME = "Rec_20260901_120000.mp4"
    }
}
