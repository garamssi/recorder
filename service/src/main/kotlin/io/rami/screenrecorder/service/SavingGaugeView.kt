package io.rami.screenrecorder.service

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.view.View
import android.view.animation.AnimationUtils
import androidx.core.graphics.ColorUtils

/**
 * 저장 중 링 게이지 (DESIGN_GUIDE.md 4절 "저장 오버레이" · "홈 > 저장 중").
 *
 * 홈의 Compose 게이지(`presentation/home/SavingGauge.kt`)와 같은 그림이다. 오버레이 창은
 * Compose 를 쓰지 않으므로 같은 층 구성을 Canvas 로 다시 그린다. 값이 갈라지면 같은 국면이
 * 표시면마다 다르게 보이므로 상수는 그쪽과 같은 값을 쓴다.
 *
 * 애니메이션은 스스로 굴린다 — 창에 붙어 있는 동안에만 다음 프레임을 예약하므로, 창을 떼면
 * 그리기가 멈춘다. 발행은 분 단위로 걸려 떼어 낸 뒤에도 도는 타이머를 두면 그대로 낭비가 된다.
 */
internal class SavingGaugeView(
    context: Context,
) : View(context) {
    /** 0f..1f 진행 원호가 채울 비율. 아직 모르면 null (원호 없음). */
    var progress: Float? = null
        set(value) {
            field = value
            invalidate()
        }

    /** 역회전 원호를 돌릴지. 발행이 끝난 뒤에는 멈춘다. */
    var spinning: Boolean = true
        set(value) {
            field = value
            invalidate()
        }

    private val density = resources.displayMetrics.density
    private val ringWidthPx = RING_WIDTH_DP * density
    private val arcWidthPx = ARC_WIDTH_DP * density

    private val trackPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = ringWidthPx
            color = ColorUtils.setAlphaComponent(BUBBLE_ACCENT, (RING_ALPHA * OPAQUE).toInt())
        }

    private val trailPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = ringWidthPx
            strokeCap = Paint.Cap.ROUND
            color = ColorUtils.setAlphaComponent(BUBBLE_ACCENT, (TRAIL_ALPHA * OPAQUE).toInt())
        }

    private val arcPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = arcWidthPx
            strokeCap = Paint.Cap.ROUND
            color = BUBBLE_ACCENT
        }

    // 후광 셰이더를 onDraw 에서 만들면 프레임마다 네이티브 셰이더가 새로 할당된다. 발행은 분
    // 단위로 걸리므로 수천 개가 쌓여 remux 와 CPU 를 다툰다. 크기가 상수라 미리 만든다.
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val arcBounds = RectF()

    private val startedAtMillis = AnimationUtils.currentAnimationTimeMillis()

    override fun onMeasure(
        widthMeasureSpec: Int,
        heightMeasureSpec: Int,
    ) {
        val size = (RING_SIZE_DP * density).toInt()
        setMeasuredDimension(size, size)
    }

    override fun onSizeChanged(
        width: Int,
        height: Int,
        oldWidth: Int,
        oldHeight: Int,
    ) {
        val ringRadius = minOf(width, height) / 2f - ringWidthPx / 2
        glowPaint.shader =
            RadialGradient(
                width / 2f,
                height / 2f,
                ringRadius * GLOW_RADIUS_SCALE,
                intArrayOf(TRANSPARENT, BUBBLE_ACCENT, TRANSPARENT),
                floatArrayOf(GLOW_HOLLOW_STOP, GLOW_PEAK_STOP, 1f),
                Shader.TileMode.CLAMP,
            )
    }

    override fun onDraw(canvas: Canvas) {
        val elapsed = AnimationUtils.currentAnimationTimeMillis() - startedAtMillis
        val centerX = width / 2f
        val centerY = height / 2f
        val ringRadius = minOf(width, height) / 2f - ringWidthPx / 2

        // 1. 링 밖으로 번지는 후광. 중심을 비워 두어야 가운데 시간 텍스트의 대비를 깎지 않는다.
        glowPaint.alpha = (glowAlphaAt(elapsed) * OPAQUE).toInt()
        canvas.drawCircle(centerX, centerY, ringRadius * GLOW_RADIUS_SCALE, glowPaint)

        // 2. 트랙 링 — 대기 상태의 링과 같은 자리·값.
        canvas.drawCircle(centerX, centerY, ringRadius, trackPaint)

        arcBounds.set(centerX - ringRadius, centerY - ringRadius, centerX + ringRadius, centerY + ringRadius)

        // 3. 역회전 흐린 원호 — 진행률이 정체돼도 화면이 죽어 보이지 않게 한다.
        if (spinning) {
            canvas.drawArc(arcBounds, TOP_ANGLE - trailRotationAt(elapsed), TRAIL_SWEEP, false, trailPaint)
        }

        // 4. 진행 원호 — 실제 발행 진행률.
        progress?.coerceIn(0f, 1f)?.takeIf { it > 0f }?.let { fraction ->
            canvas.drawArc(arcBounds, TOP_ANGLE, FULL_TURN * fraction, false, arcPaint)
        }

        if (spinning || progress == null) postInvalidateOnAnimation()
    }

    /** 맥동하는 후광의 현재 불투명도 (`PULSE_MILLIS` 왕복). */
    private fun glowAlphaAt(elapsedMillis: Long): Float {
        val phase = (elapsedMillis % (PULSE_MILLIS * 2)) / PULSE_MILLIS.toFloat()
        val triangle = if (phase <= 1f) phase else 2f - phase
        return GLOW_MIN_ALPHA + (GLOW_MAX_ALPHA - GLOW_MIN_ALPHA) * triangle
    }

    /** 역회전 원호의 현재 각도(도). */
    private fun trailRotationAt(elapsedMillis: Long): Float = FULL_TURN * (elapsedMillis % TRAIL_MILLIS) / TRAIL_MILLIS

    private companion object {
        /** 홈의 `RECORD_RING`·`RING_WIDTH`·`SAVE_ARC_WIDTH` 와 같은 값이어야 한다. */
        const val RING_SIZE_DP = 160f
        const val RING_WIDTH_DP = 2f
        const val ARC_WIDTH_DP = 4f

        const val RING_ALPHA = 0.3f
        const val TRAIL_ALPHA = 0.25f
        const val TRAIL_SWEEP = 70f
        const val TRAIL_MILLIS = 3_000L
        const val PULSE_MILLIS = 900L
        const val GLOW_MIN_ALPHA = 0.10f
        const val GLOW_MAX_ALPHA = 0.28f
        const val GLOW_RADIUS_SCALE = 1.35f
        const val GLOW_HOLLOW_STOP = 0.62f
        const val GLOW_PEAK_STOP = 0.78f

        const val TOP_ANGLE = -90f
        const val FULL_TURN = 360f
        const val OPAQUE = 255f
        const val TRANSPARENT = 0
    }
}
