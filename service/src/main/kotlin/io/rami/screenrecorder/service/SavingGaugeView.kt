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
import io.rami.screenrecorder.core.common.design.SavingGaugeSpec

/**
 * 저장 중 링 게이지 (DESIGN_GUIDE.md 4절 "저장 오버레이" · "홈 > 저장 중").
 *
 * 홈의 Compose 게이지(`presentation/home/SavingGauge.kt`)와 같은 그림이다. 오버레이 창은
 * Compose 를 쓰지 않으므로 같은 층 구성을 Canvas 로 다시 그린다. 수치는 양쪽이 함께 읽는
 * [SavingGaugeSpec] 에서만 가져온다 — 각자 상수를 들면 한쪽만 고쳐져 갈라진다.
 *
 * 애니메이션은 스스로 굴린다 — 창에 붙어 있는 동안에만 다음 프레임을 예약하므로, 창을 떼면
 * 그리기가 멈춘다. 발행은 분 단위로 걸려 떼어 낸 뒤에도 도는 타이머를 두면 그대로 낭비가 된다.
 */
internal class SavingGaugeView(
    context: Context,
) : View(context) {
    /**
     * 0f..1f 진행 원호가 채울 비율. 아직 모르면 null (원호 없음).
     *
     * 바뀐 값으로 튀지 않고 [SavingGaugeSpec.SWEEP_TWEEN_MILLIS] 동안 옮겨 간다.
     */
    var progress: Float? = null
        set(value) {
            sweepFrom = currentSweep()
            sweepChangedAtMillis = AnimationUtils.currentAnimationTimeMillis()
            field = value
            invalidate()
        }

    /** 역회전 원호를 돌릴지. 발행이 끝난 뒤에는 멈춘다. */
    var spinning: Boolean = true
        set(value) {
            field = value
            invalidate()
        }

    private var sweepFrom = 0f
    private var sweepChangedAtMillis = 0L

    private val density = resources.displayMetrics.density
    private val ringWidthPx = SavingGaugeSpec.RING_WIDTH_DP * density

    /** 링 반지름. 뷰가 아니라 링 크기에서 정한다 — 뷰는 후광이 들어갈 만큼 더 크다. */
    private val ringRadiusPx = SavingGaugeSpec.RING_DP * density / 2 - ringWidthPx / 2

    private val trackPaint = strokePaint(ringWidthPx, SavingGaugeSpec.RING_ALPHA)

    private val trailPaint =
        strokePaint(ringWidthPx, SavingGaugeSpec.TRAIL_ALPHA).apply { strokeCap = Paint.Cap.ROUND }

    private val arcPaint =
        strokePaint(SavingGaugeSpec.ARC_WIDTH_DP * density, alpha = 1f).apply {
            strokeCap = Paint.Cap.ROUND
        }

    // 후광 셰이더를 onDraw 에서 만들면 프레임마다 네이티브 셰이더가 새로 할당된다. 발행은 분
    // 단위로 걸리므로 수천 개가 쌓여 remux 와 CPU 를 다툰다. 크기가 상수라 미리 만든다.
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val arcBounds = RectF()

    private val startedAtMillis = AnimationUtils.currentAnimationTimeMillis()

    private fun strokePaint(
        width: Float,
        alpha: Float,
    ) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = width
        color = ColorUtils.setAlphaComponent(BUBBLE_ACCENT, (alpha * OPAQUE).toInt())
    }

    /**
     * 뷰는 링이 아니라 **후광 지름**에 맞춘다.
     *
     * Compose 의 Canvas 는 노드 경계를 넘어 그려도 클립하지 않지만 플랫폼 뷰는 자기 경계에서
     * 자른다. 링 크기로 재면 후광이 사각으로 잘려 붉은 얼룩이 된다.
     */
    override fun onMeasure(
        widthMeasureSpec: Int,
        heightMeasureSpec: Int,
    ) {
        val size = (SavingGaugeSpec.RING_DP * SavingGaugeSpec.GLOW_RADIUS_SCALE * density).toInt()
        setMeasuredDimension(
            resolveSize(size, widthMeasureSpec),
            resolveSize(size, heightMeasureSpec),
        )
    }

    override fun onSizeChanged(
        width: Int,
        height: Int,
        oldWidth: Int,
        oldHeight: Int,
    ) {
        glowPaint.shader =
            RadialGradient(
                width / 2f,
                height / 2f,
                ringRadiusPx * SavingGaugeSpec.GLOW_RADIUS_SCALE,
                intArrayOf(TRANSPARENT, BUBBLE_ACCENT, TRANSPARENT),
                floatArrayOf(SavingGaugeSpec.GLOW_HOLLOW_STOP, SavingGaugeSpec.GLOW_PEAK_STOP, 1f),
                Shader.TileMode.CLAMP,
            )
    }

    override fun onDraw(canvas: Canvas) {
        val elapsed = AnimationUtils.currentAnimationTimeMillis() - startedAtMillis
        val centerX = width / 2f
        val centerY = height / 2f

        // 1. 링 밖으로 번지는 후광. 중심을 비워 두어야 가운데 시간 텍스트의 대비를 깎지 않는다.
        glowPaint.alpha = (glowAlphaAt(elapsed) * OPAQUE).toInt()
        canvas.drawCircle(centerX, centerY, ringRadiusPx * SavingGaugeSpec.GLOW_RADIUS_SCALE, glowPaint)

        // 2. 트랙 링 — 대기 상태의 링과 같은 자리·값.
        canvas.drawCircle(centerX, centerY, ringRadiusPx, trackPaint)

        arcBounds.set(
            centerX - ringRadiusPx,
            centerY - ringRadiusPx,
            centerX + ringRadiusPx,
            centerY + ringRadiusPx,
        )

        // 3. 역회전 흐린 원호 — 진행률이 정체돼도 화면이 죽어 보이지 않게 한다.
        if (spinning) {
            canvas.drawArc(
                arcBounds,
                SavingGaugeSpec.TOP_ANGLE_DEGREES - trailRotationAt(elapsed),
                SavingGaugeSpec.TRAIL_SWEEP_DEGREES,
                false,
                trailPaint,
            )
        }

        // 4. 진행 원호 — 실제 발행 진행률.
        currentSweep().takeIf { it > 0f }?.let { fraction ->
            canvas.drawArc(
                arcBounds,
                SavingGaugeSpec.TOP_ANGLE_DEGREES,
                SavingGaugeSpec.FULL_TURN_DEGREES * fraction,
                false,
                arcPaint,
            )
        }

        // 후광은 완료 국면에도 맥동한다 (홈과 같다). 창을 떼면 이 예약이 no-op 이 되어 멈춘다.
        postInvalidateOnAnimation()
    }

    /** 지금 그려야 할 진행 비율. 목표로 [SavingGaugeSpec.SWEEP_TWEEN_MILLIS] 동안 옮겨 간다. */
    internal fun currentSweep(): Float {
        val target = progress?.coerceIn(0f, 1f) ?: 0f
        val since = AnimationUtils.currentAnimationTimeMillis() - sweepChangedAtMillis
        if (since >= SavingGaugeSpec.SWEEP_TWEEN_MILLIS) return target
        val fraction = since.toFloat() / SavingGaugeSpec.SWEEP_TWEEN_MILLIS
        return sweepFrom + (target - sweepFrom) * fraction
    }

    /** 맥동하는 후광의 현재 불투명도. */
    private fun glowAlphaAt(elapsedMillis: Long): Float {
        val phase = (elapsedMillis % (SavingGaugeSpec.PULSE_MILLIS * 2)) / SavingGaugeSpec.PULSE_MILLIS.toFloat()
        val triangle = if (phase <= 1f) phase else 2f - phase
        return SavingGaugeSpec.GLOW_MIN_ALPHA +
            (SavingGaugeSpec.GLOW_MAX_ALPHA - SavingGaugeSpec.GLOW_MIN_ALPHA) * triangle
    }

    /** 역회전 원호의 현재 각도(도). */
    private fun trailRotationAt(elapsedMillis: Long): Float =
        SavingGaugeSpec.FULL_TURN_DEGREES * (elapsedMillis % SavingGaugeSpec.TRAIL_MILLIS) /
            SavingGaugeSpec.TRAIL_MILLIS

    private companion object {
        const val OPAQUE = 255f
        const val TRANSPARENT = 0
    }
}
