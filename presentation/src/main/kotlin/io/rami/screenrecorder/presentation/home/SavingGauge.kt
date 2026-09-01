package io.rami.screenrecorder.presentation.home

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.rami.screenrecorder.core.common.design.SavingGaugeSpec
import io.rami.screenrecorder.core.common.time.DurationFormatter
import io.rami.screenrecorder.core.designsystem.theme.tabularNumbers
import io.rami.screenrecorder.domain.model.Recording
import io.rami.screenrecorder.domain.model.RecordingState
import io.rami.screenrecorder.presentation.R

// 저장 중·저장 완료 국면의 링 게이지 (기능명세서 2.1절 [결정], DESIGN_GUIDE.md 4절).
//
// 대기 상태의 링을 진행 게이지로 승격시킨다. 링은 런처 아이콘과 같은 상징이므로
// (DESIGN_GUIDE.md 5.1절) 저장 중이 새 화면이 아니라 같은 흐름의 한 칸으로 읽힌다.

/** 저장 중 — 링이 실제 발행 진행률만큼 차오르고, 중앙에 녹화 길이가 남는다. */
@Composable
internal fun ColumnScope.SavingStatus(state: RecordingState.Stopping) {
    val savingLabel = stringResource(R.string.home_saving)
    SavingGauge(progress = state.progress, label = savingLabel, spinning = true) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            // 녹화 중(56sp)보다 작게 둔다 — 흘러가는 값이 아니라 이미 끝난 값이다.
            // 한 시간을 넘으면 "HH:MM:SS" 여덟 자가 되어 40sp 로는 링 폭을 넘는다. 글꼴 배율을
            // 키운 기기에서 끝자리가 잘리므로, 자릿수에 따라 크기를 낮춘다.
            val elapsedText = DurationFormatter.formatElapsed(state.elapsed)
            Text(
                text = elapsedText,
                style = MaterialTheme.typography.displaySmall.tabularNumbers(),
                fontSize = if (elapsedText.length > MINUTES_ONLY_CHARS) SAVE_ELAPSED_SIZE_LONG else SAVE_ELAPSED_SIZE,
                maxLines = 1,
                softWrap = false,
            )
            state.progress?.let { progress ->
                Text(
                    text = stringResource(R.string.home_saving_percent, (progress * PERCENT).toInt()),
                    style = MaterialTheme.typography.labelMedium.tabularNumbers(),
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
    SavingCaption(label = savingLabel, fileName = state.fileName)
    // 저장은 취소할 수 없으므로 일시정지·중지 버튼을 두지 않는다 (기능명세서 2.1절 [결정]).
}

/**
 * 저장 완료 — 링이 꽉 찬 채 중앙이 체크로 바뀐다 (DESIGN_GUIDE.md 4절 "저장 완료").
 *
 * [saved]는 발행이 확정된 녹화본이다. 길이와 이름을 발행 결과에서 읽으므로 스톱워치가
 * 아니라 실제 파일이 진실이다. 표시는 스스로 사라진다 — 누를 것이 없다.
 */
@Composable
internal fun ColumnScope.SavedStatus(saved: Recording) {
    val savedLabel = stringResource(R.string.home_saved)
    // 다 끝난 상태에서 원호가 계속 돌면 아직 일하는 중으로 읽힌다.
    SavingGauge(progress = 1f, label = savedLabel, spinning = false) {
        Icon(
            imageVector = Icons.Default.Check,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(SAVED_CHECK_SIZE),
        )
    }
    SavingCaption(label = savedLabel, fileName = saved.displayName)
}

/** 상태 문구 한 줄 + 무엇이 저장되는지 못 박는 파일명. */
@Composable
private fun SavingCaption(
    label: String,
    fileName: String,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CAPTION_GAP),
    ) {
        // 녹화가 끝났으므로 점은 맥동하지 않는다.
        RecordingPulseDot(animated = false)
        Text(text = label, style = MaterialTheme.typography.titleMedium)
    }
    if (fileName.isNotEmpty()) {
        Text(
            text = fileName,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * 진행 게이지 링. [content]는 링 가운데에 놓인다.
 *
 * @param progress 0f..1f. null이면 진행률을 아직 모르는 구간이라 채움이 없다.
 * @param spinning 역회전 원호를 돌릴지. 저장이 끝난 뒤에는 멈춘다.
 */
@Composable
private fun SavingGauge(
    progress: Float?,
    label: String,
    spinning: Boolean,
    content: @Composable () -> Unit,
) {
    val transition = rememberInfiniteTransition(label = "saving")
    // 진행률이 정체돼도 화면이 죽어 보이지 않게 하는 층이다. remux 는 균일하게 진행되지 않는다.
    val trailRotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = -SavingGaugeSpec.FULL_TURN_DEGREES,
        animationSpec =
            infiniteRepeatable(
                tween(SavingGaugeSpec.TRAIL_MILLIS, easing = LinearEasing),
                RepeatMode.Restart,
            ),
        label = "savingTrail",
    )
    val glowAlpha by transition.animateFloat(
        initialValue = SavingGaugeSpec.GLOW_MIN_ALPHA,
        targetValue = SavingGaugeSpec.GLOW_MAX_ALPHA,
        animationSpec = infiniteRepeatable(tween(PULSE_MILLIS), repeatMode = RepeatMode.Reverse),
        label = "savingGlow",
    )
    // 0.5% 단위로 올라오는 값을 그대로 그리면 원호가 끊겨 보인다.
    val sweepFraction by animateFloatAsState(
        targetValue = (progress ?: 0f).coerceIn(0f, 1f),
        animationSpec = tween(SavingGaugeSpec.SWEEP_TWEEN_MILLIS),
        label = "savingSweep",
    )
    val gaugeColor = MaterialTheme.colorScheme.primary
    // 후광 브러시를 draw 안에서 만들면 프레임마다 네이티브 shader 가 새로 할당된다. 발행은
    // 분 단위로 걸리므로 수천 개가 쌓여 remux 와 CPU 를 다툰다. 크기가 상수라 미리 만든다.
    val glowBrush = rememberGlowBrush(gaugeColor)
    Box(
        modifier =
            Modifier
                .size(RECORD_RING)
                // 자식 텍스트가 각각 낭독되면 퍼센트와 문구가 두 번 읽힌다. 한 노드로 합친다.
                // 자식 시맨틱을 지우지는 않는다 — 지우면 값 자체가 접근성 트리에서 사라진다.
                .semantics(mergeDescendants = true) {
                    contentDescription = label
                    // liveRegion 을 두지 않는다 — 진행률이 0.5% 마다 바뀌므로 발행 2~4분 동안
                    // 스크린리더가 같은 문구를 수백 번 다시 읽으며 다른 음성을 끊는다.
                    progressBarRangeInfo =
                        progress
                            ?.let { ProgressBarRangeInfo(it.coerceIn(0f, 1f), PROGRESS_RANGE) }
                            // 진행률을 모르는 구간도 "진행 중" 역할은 알려야 한다.
                            ?: ProgressBarRangeInfo.Indeterminate
                },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawSavingGauge(
                glowBrush,
                gaugeColor,
                GaugeFrame(glowAlpha, trailRotation, sweepFraction, spinning),
            )
        }
        content()
    }
}

/**
 * 한 프레임에 그릴 게이지의 상태.
 *
 * 애니메이션 값 네 개를 그리기 함수에 따로 넘기면 파라미터가 길어져 읽기 어렵다.
 *
 * @param glowAlpha 후광의 현재 불투명도.
 * @param trailRotation 역회전 원호의 현재 각도(도).
 * @param sweepFraction 0f..1f 진행 원호가 채울 비율.
 * @param spinning 역회전 원호를 그릴지. 저장이 끝난 뒤에는 멈춘다.
 */
private data class GaugeFrame(
    val glowAlpha: Float,
    val trailRotation: Float,
    val sweepFraction: Float,
    val spinning: Boolean,
)

/**
 * 링 테두리 밖으로 번지는 후광 브러시.
 *
 * 링 지름이 [RECORD_RING] 상수라 그릴 크기를 미리 알 수 있다. 그래서 컴포지션에서 한 번
 * 만들어 두고, 맥동은 `drawCircle` 의 alpha 인자로 준다.
 */
@Composable
private fun rememberGlowBrush(color: Color): Brush {
    val density = LocalDensity.current
    return remember(color, density) {
        val ringRadius = with(density) { (RECORD_RING / 2 - RING_WIDTH / 2).toPx() }
        Brush.radialGradient(
            colorStops =
                arrayOf(
                    SavingGaugeSpec.GLOW_HOLLOW_STOP to Color.Transparent,
                    SavingGaugeSpec.GLOW_PEAK_STOP to color,
                    1f to Color.Transparent,
                ),
            radius = ringRadius * SavingGaugeSpec.GLOW_RADIUS_SCALE,
        )
    }
}

/**
 * 겹쳐 쌓은 네 층 (DESIGN_GUIDE.md 4절 "저장 중").
 *
 * 트랙·역회전·진행 원호는 모두 [RING_WIDTH]/2 만큼 들인 같은 궤도를 쓴다. 대기 상태의
 * `border` 와 같은 반지름이어야 상태가 바뀔 때 링이 움직이지 않는다.
 */
private fun DrawScope.drawSavingGauge(
    glowBrush: Brush,
    color: Color,
    frame: GaugeFrame,
) {
    val ringRadius = size.minDimension / 2 - RING_WIDTH.toPx() / 2
    // 링 테두리에서 밖으로 번지는 후광. 중심을 비워 두어야 가운데 시간 텍스트의 대비를
    // 깎지 않는다. 노드 경계를 넘어 그려도 Canvas 는 클립하지 않는다.
    // 맥동은 브러시를 다시 만들지 않고 alpha 인자로만 준다.
    drawCircle(brush = glowBrush, radius = ringRadius * SavingGaugeSpec.GLOW_RADIUS_SCALE, alpha = frame.glowAlpha)
    drawCircle(
        color = color.copy(alpha = RING_ALPHA),
        radius = ringRadius,
        style = Stroke(RING_WIDTH.toPx()),
    )
    val inset = RING_WIDTH.toPx() / 2
    val arcOffset = Offset(inset, inset)
    val arcSize = Size(size.width - RING_WIDTH.toPx(), size.height - RING_WIDTH.toPx())
    if (frame.spinning) {
        drawArc(
            color = color.copy(alpha = SavingGaugeSpec.TRAIL_ALPHA),
            startAngle = SavingGaugeSpec.TOP_ANGLE_DEGREES + frame.trailRotation,
            sweepAngle = SavingGaugeSpec.TRAIL_SWEEP_DEGREES,
            useCenter = false,
            topLeft = arcOffset,
            size = arcSize,
            style = Stroke(RING_WIDTH.toPx(), cap = StrokeCap.Round),
        )
    }
    if (frame.sweepFraction <= 0f) return
    drawArc(
        color = color,
        startAngle = SavingGaugeSpec.TOP_ANGLE_DEGREES,
        sweepAngle = SavingGaugeSpec.FULL_TURN_DEGREES * frame.sweepFraction,
        useCenter = false,
        topLeft = arcOffset,
        size = arcSize,
        style = Stroke(SAVE_ARC_WIDTH.toPx(), cap = StrokeCap.Round),
    )
}

// 저장 중 게이지 (DESIGN_GUIDE.md 4절 "저장 중")
private val SAVE_ARC_WIDTH = SavingGaugeSpec.ARC_WIDTH_DP.dp
private val SAVE_ELAPSED_SIZE = SavingGaugeSpec.ELAPSED_SP.sp

private val SAVE_ELAPSED_SIZE_LONG = SavingGaugeSpec.ELAPSED_LONG_SP.sp
private const val MINUTES_ONLY_CHARS = SavingGaugeSpec.MINUTES_ONLY_CHARS
private val CAPTION_GAP = 10.dp

private const val PERCENT = 100
private val PROGRESS_RANGE = 0f..1f

// 저장 완료 (DESIGN_GUIDE.md 4절 "저장 완료")
private val SAVED_CHECK_SIZE = SavingGaugeSpec.CHECK_DP.dp
