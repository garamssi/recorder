package io.rami.screenrecorder.service

import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
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
        val texts = render(saving(progress = null)).allTexts()

        assertTrue("길이는 그대로 보여야 한다: $texts", ELAPSED in texts)
        assertTrue("퍼센트가 남아 있다: $texts", texts.none { it.contains("%") })
    }

    /** 다 끝난 값에 퍼센트를 남겨 두면 아직 진행 중으로 읽힌다. */
    @Test
    fun `발행이 확정되면 퍼센트 대신 체크를 보여 준다`() {
        val card = render(SaveOverlayContent(ELAPSED, FILE_NAME, progress = 1f, done = true))

        val texts = card.allTexts()
        assertTrue("완료 문구가 없다: $texts", context.getString(R.string.save_complete_banner) in texts)
        assertTrue("퍼센트가 남아 있다: $texts", texts.none { it.contains("%") })
        assertTrue("체크 아이콘이 없다", card.imageViews().any { it.visibility == View.VISIBLE })
    }

    /**
     * 진행률은 0.5% 단위로 올라와 발행 2~4분 동안 수백 번 갱신된다. 그때마다 뷰를 새로 만들면
     * 링 애니메이션이 매번 처음부터 시작한다.
     */
    @Test
    fun `값을 갱신해도 같은 뷰를 그대로 쓴다`() {
        val card = SaveOverlayCard(context)
        card.render(saving(progress = 0.1f))
        val first = card.root

        card.render(saving(progress = 0.9f))

        assertTrue("갱신이 뷰를 새로 만들었다", first === card.root)
        assertTrue("갱신한 값이 반영되지 않았다", card.root.allTexts().any { it.contains("90") })
    }

    private fun saving(progress: Float?) = SaveOverlayContent(ELAPSED, FILE_NAME, progress = progress, done = false)

    private companion object {
        const val ELAPSED = "03:42"
        const val FILE_NAME = "Rec_20260901_120000.mp4"
    }
}
