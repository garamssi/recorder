package io.rami.screenrecorder.service

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.roundToInt

// 플로팅 버블을 이루는 뷰 조각들. 오버레이 창은 Compose를 쓰지 않으므로 플랫폼 뷰로 만든다.
// 색·치수는 DESIGN_GUIDE.md 1·4절의 Kinetic 토큰을 그대로 옮긴 값이다.

/** Kinetic 카드 서피스 (약간 투명). */
internal const val BUBBLE_SURFACE = 0xEE18181B.toInt()

/** Kinetic 레드 — 녹화·주 동작. */
internal const val BUBBLE_ACCENT = 0xFFEF4444.toInt()

/** 본문 텍스트 색. */
internal const val BUBBLE_FOREGROUND = 0xFFFAFAFA.toInt()

/** 보조 텍스트 색. */
internal const val BUBBLE_MUTED = 0xFFA1A1AA.toInt()

private const val CIRCLE_SIZE_DP = 52f
private const val LABEL_CORNER_DP = 8f
private const val PILL_CORNER_DP = 26f
private const val LABEL_PADDING_H_DP = 10f
private const val LABEL_PADDING_V_DP = 6f
private const val ROW_GAP_DP = 10f
private const val LABEL_TEXT_SP = 13f
private const val ELAPSED_TEXT_SP = 16f

/** dp를 픽셀로 바꾼다. */
internal fun Context.dpToPx(value: Float): Int = (value * resources.displayMetrics.density).roundToInt()

/** 원형 아이콘 버튼. [accent]면 Kinetic 레드로 강조한다. */
internal fun Context.circleButton(
    iconRes: Int,
    contentDescription: String,
    accent: Boolean,
    onClick: () -> Unit,
): ImageView {
    val size = dpToPx(CIRCLE_SIZE_DP)
    return ImageView(this).apply {
        setImageResource(iconRes)
        this.contentDescription = contentDescription
        scaleType = ImageView.ScaleType.CENTER
        background =
            GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(if (accent) BUBBLE_ACCENT else BUBBLE_SURFACE)
            }
        layoutParams = LinearLayout.LayoutParams(size, size)
        setOnClickListener { onClick() }
    }
}

/** 버튼 왼쪽에 붙는 설명 라벨. */
internal fun Context.actionLabel(text: String): TextView =
    TextView(this).apply {
        this.text = text
        setTextColor(BUBBLE_FOREGROUND)
        textSize = LABEL_TEXT_SP
        background =
            GradientDrawable().apply {
                setColor(BUBBLE_SURFACE)
                cornerRadius = dpToPx(LABEL_CORNER_DP).toFloat()
            }
        setPadding(
            dpToPx(LABEL_PADDING_H_DP),
            dpToPx(LABEL_PADDING_V_DP),
            dpToPx(LABEL_PADDING_H_DP),
            dpToPx(LABEL_PADDING_V_DP),
        )
    }

/**
 * "라벨 + 원형 버튼" 한 줄.
 *
 * @param labelFirst 라벨을 버튼보다 앞에 둘지. 버튼이 버블이 붙어 있는 변 쪽에 와야
 *   펼칠 때 기준 요소가 가로로 밀리지 않는다 (기능명세서 11.1절).
 */
internal fun Context.actionRow(
    iconRes: Int,
    label: String,
    accent: Boolean,
    labelFirst: Boolean,
    onClick: () -> Unit,
): LinearLayout =
    LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        // 버튼의 크기 params는 유지하고 라벨 쪽 간격만 더한다.
        val button = circleButton(iconRes, label, accent, onClick)
        val buttonParams = button.layoutParams as LinearLayout.LayoutParams
        val gap = dpToPx(ROW_GAP_DP)
        if (labelFirst) {
            buttonParams.marginStart = gap
            addView(actionLabel(label))
            addView(button)
        } else {
            buttonParams.marginEnd = gap
            addView(button)
            addView(actionLabel(label))
        }
    }

/**
 * 세로 스택에 자식을 추가한다.
 *
 * [android.view.ViewGroup.addView]에 LayoutParams를 넘기면 자식이 스스로 정한 크기를 덮어쓰므로
 * (원형 버튼이 아이콘 크기로 쪼그라든다), 자식의 기존 params에 정렬과 간격만 얹는다.
 *
 * @param withGap 위쪽 간격을 줄지. 스택의 첫 자식에는 주지 않는다.
 * @param alignEnd 세로 스택 안에서 오른쪽으로 붙일지. 가로 스택(pill)에서는 의미가 없다.
 */
internal fun LinearLayout.addStacked(
    child: View,
    withGap: Boolean = true,
    alignEnd: Boolean = true,
) {
    val params =
        child.layoutParams as? LinearLayout.LayoutParams
            ?: LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            )
    params.gravity = if (alignEnd) Gravity.END else Gravity.START
    params.topMargin = if (withGap) context.dpToPx(ROW_GAP_DP) else 0
    child.layoutParams = params
    addView(child)
}

/** 녹화 중 표시용 pill 컨테이너 (REC 점 + 경과 시간 + 제어 버튼). */
internal fun Context.pillContainer(): LinearLayout =
    LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        background =
            GradientDrawable().apply {
                setColor(BUBBLE_SURFACE)
                cornerRadius = dpToPx(PILL_CORNER_DP).toFloat()
            }
        val padding = dpToPx(LABEL_PADDING_V_DP)
        setPadding(padding, padding, padding, padding)
    }

/** 경과 시간 텍스트 (고정폭 숫자). */
internal fun Context.elapsedLabel(): TextView =
    TextView(this).apply {
        setTextColor(BUBBLE_FOREGROUND)
        textSize = ELAPSED_TEXT_SP
        fontFeatureSettings = "tnum"
        setPadding(dpToPx(LABEL_PADDING_H_DP), 0, dpToPx(LABEL_PADDING_H_DP), 0)
    }

/** 녹화 상태를 알리는 작은 원형 점. */
internal fun Context.statusDot(active: Boolean): View {
    val size = dpToPx(DOT_SIZE_DP)
    return View(this).apply {
        background =
            GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(if (active) BUBBLE_ACCENT else BUBBLE_MUTED)
            }
        layoutParams =
            LinearLayout.LayoutParams(size, size).apply { marginStart = dpToPx(LABEL_PADDING_V_DP) }
    }
}

private const val DOT_SIZE_DP = 10f
