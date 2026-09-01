package io.rami.screenrecorder.service

import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import io.rami.screenrecorder.core.common.design.SavingGaugeSpec

/**
 * 저장 오버레이가 한 번에 그릴 내용 (DESIGN_GUIDE.md 4절 "저장 오버레이").
 *
 * @param elapsed 방금 녹화한 길이. 흘러가는 값이 아니라 이미 끝난 값이다. 완료 국면에서는
 *   그리지 않는다 — 중앙이 체크로 바뀌므로 값이 없어도 된다.
 * @param fileName 무엇이 저장되는지 못 박는다.
 * @param progress 0f..1f 발행 진행률. 아직 모르면 null — 발행은 메타데이터 판독으로 시작하는데
 *   그 구간에는 진행률 신호가 없다.
 * @param outcome 발행 구간의 결말. 진행 중이면 링이 돌고, 확정되면 체크, 실패면 멈춘다.
 */
internal data class SaveOverlayContent(
    val elapsed: String,
    val fileName: String,
    val progress: Float?,
    val outcome: SaveOutcome,
)

/** 발행 구간의 결말 (기능명세서 6.1절 [결정]). */
internal enum class SaveOutcome {
    /** 아직 진행 중. */
    IN_PROGRESS,

    /** 발행이 확정됐다 — 링이 꽉 차고 중앙이 체크로 바뀐다. */
    SAVED,

    /** 발행이 실패했다 — 링을 채우지 않는다. 채우면 저장된 것으로 읽힌다. */
    FAILED,
}

/**
 * 저장 오버레이 카드 — 홈의 링 게이지를 플랫폼 뷰로 옮긴 것.
 *
 * 값이 바뀔 때마다 뷰를 새로 만들지 않는다. 발행 진행률은 0.5% 단위로 올라와 2~4분 동안 수백 번
 * 갱신되는데, 그때마다 다시 만들면 링 애니메이션이 매번 처음부터 시작한다.
 */
internal class SaveOverlayCard(
    private val context: Context,
) {
    private val gauge = SavingGaugeView(context)

    private val elapsedText =
        TextView(context).apply {
            setTextColor(BUBBLE_FOREGROUND)
            fontFeatureSettings = "tnum"
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
            gravity = Gravity.CENTER
            maxLines = 1
        }

    private val percentText =
        TextView(context).apply {
            setTextColor(BUBBLE_ACCENT)
            textSize = PERCENT_TEXT_SP
            fontFeatureSettings = "tnum"
            gravity = Gravity.CENTER
        }

    private val check =
        ImageView(context).apply {
            setImageResource(R.drawable.ic_banner_check)
            visibility = View.GONE
        }

    private val statusLabel =
        TextView(context).apply {
            setTextColor(BUBBLE_FOREGROUND)
            textSize = LABEL_TEXT_SP
        }

    private val fileNameText =
        TextView(context).apply {
            setTextColor(BUBBLE_MUTED)
            textSize = NAME_TEXT_SP
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.MIDDLE
            // 폭을 묶지 않으면 말줄임이 발동하지 않고 카드가 파일명만큼 늘어난다. 부모가
            // WRAP_CONTENT 라 카드가 화면보다 넓어질 수 있다.
            maxWidth = context.dpToPx(SavingGaugeSpec.RING_DP * SavingGaugeSpec.GLOW_RADIUS_SCALE)
        }

    /** 창에 붙일 뷰. 갱신해도 이 참조는 바뀌지 않는다. */
    val root: View = buildRoot()

    /** 카드 내용을 [content] 로 맞춘다. */
    fun render(content: SaveOverlayContent) {
        val settled = content.outcome != SaveOutcome.IN_PROGRESS
        elapsedText.text = content.elapsed
        // 한 시간을 넘으면 "HH:MM:SS" 여덟 자가 되어 링 폭을 넘는다. 자릿수에 따라 낮춘다.
        elapsedText.textSize =
            if (content.elapsed.length > SavingGaugeSpec.MINUTES_ONLY_CHARS) {
                SavingGaugeSpec.ELAPSED_LONG_SP
            } else {
                SavingGaugeSpec.ELAPSED_SP
            }
        fileNameText.text = content.fileName
        gauge.spinning = !settled
        // 실패한 진행률을 100%로 채우면 저장된 것으로 읽힌다.
        gauge.progress = if (content.outcome == SaveOutcome.SAVED) 1f else content.progress
        statusLabel.setText(content.outcome.labelRes())
        // 다 끝난 값에 퍼센트를 남겨 두면 아직 진행 중으로 읽힌다.
        val percent = content.progress?.takeIf { !settled }
        percentText.visibility = if (percent == null) View.GONE else View.VISIBLE
        percent?.let { percentText.text = context.getString(R.string.save_overlay_percent, percentOf(it)) }
        // 중앙은 체크만 그린다. 길이는 저장 중에 이미 보여 줬다. 실패는 길이를 그대로 둔다 —
        // 무엇이 저장되지 못했는지가 그때 필요한 정보다.
        val saved = content.outcome == SaveOutcome.SAVED
        check.visibility = if (saved) View.VISIBLE else View.GONE
        elapsedText.visibility = if (saved) View.GONE else View.VISIBLE
    }

    private fun SaveOutcome.labelRes(): Int =
        when (this) {
            SaveOutcome.IN_PROGRESS -> R.string.floating_saving
            SaveOutcome.SAVED -> R.string.save_complete_banner
            SaveOutcome.FAILED -> R.string.save_failed_overlay
        }

    private fun percentOf(progress: Float): Int = (progress.coerceIn(0f, 1f) * PERCENT_SCALE).toInt()

    private fun buildRoot(): View {
        val paddingH = context.dpToPx(PADDING_H_DP)
        val paddingV = context.dpToPx(PADDING_V_DP)
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            background =
                GradientDrawable().apply {
                    setColor(OVERLAY_SURFACE)
                    cornerRadius = context.dpToPx(CORNER_DP).toFloat()
                }
            setPadding(paddingH, paddingV, paddingH, paddingV)
            addView(buildRing())
            addView(buildStatusRow(), rowParams())
            addView(fileNameText)
        }
    }

    /** 링 위에 중앙 내용을 얹은 층. */
    private fun buildRing(): View =
        FrameLayout(context).apply {
            addView(gauge)
            addView(
                buildCenter(),
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    Gravity.CENTER,
                ),
            )
        }

    /** 링 가운데 — 저장 중에는 길이와 퍼센트, 완료 뒤에는 체크. */
    private fun buildCenter(): View =
        LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            addView(elapsedText)
            addView(percentText)
            addView(check, context.dpToPx(SavingGaugeSpec.CHECK_DP), context.dpToPx(SavingGaugeSpec.CHECK_DP))
        }

    /** REC 점 + 상태 문구 한 줄. */
    private fun buildStatusRow(): View =
        LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            // 맥동을 멈춘 REC 점. 홈의 저장 중 상태 줄과 같은 밝기다 — recRed 를 절반으로
            // 흐리게 한다 (RecordControl.kt 의 animated = false). active = false 의 회색은
            // 버블에서 "녹화 중이 아님" 을 뜻하는 다른 신호라 여기에 쓰면 뜻이 어긋난다.
            addView(context.statusDot(active = true).apply { alpha = DOT_DIM_ALPHA })
            addView(statusLabel, statusLabelParams())
        }

    private fun rowParams() =
        LinearLayout
            .LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = context.dpToPx(ROW_GAP_DP) }

    private fun statusLabelParams() =
        LinearLayout
            .LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { marginStart = context.dpToPx(GAP_DP) }

    private companion object {
        /**
         * 카드 배경 — 버블 알약과 같은 색을 **불투명**하게 쓴다.
         *
         * 알약(93%)은 작아서 살짝 비쳐도 읽히지만, 이 카드는 200dp가 넘어 아래 앱의 제목과
         * 본문이 그대로 뚫고 올라온다. 실기기에서 앱 글자가 링 위에 겹쳐 보였다.
         *
         * 색을 다시 적지 않고 알약 토큰에서 알파만 올린다 — 카드색이 바뀌면 함께 바뀌어야 한다.
         */
        val OVERLAY_SURFACE = ColorUtils.setAlphaComponent(BUBBLE_SURFACE, OPAQUE_ALPHA)

        const val OPAQUE_ALPHA = 255

        const val PERCENT_TEXT_SP = 12f
        const val LABEL_TEXT_SP = 15f
        const val NAME_TEXT_SP = 12f
        const val CORNER_DP = 24f
        const val PADDING_H_DP = 24f
        const val PADDING_V_DP = 20f
        const val GAP_DP = 8f
        const val ROW_GAP_DP = 12f
        const val PERCENT_SCALE = 100

        /** 홈의 `DISABLED_ALPHA` 와 같은 값. */
        const val DOT_DIM_ALPHA = 0.5f
    }
}
