package io.rami.screenrecorder.core.designsystem.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale

/** 눌림 피드백 지속 시간 (DESIGN_GUIDE.md 5절: 150~300ms, ease-out). */
const val KINETIC_MOTION_MILLIS = 200

/** 짧은 상태 전환(딤, 페이드)에 쓰는 지속 시간. */
const val KINETIC_FADE_MILLIS = 150

/**
 * 누르는 동안 살짝 축소되는 Kinetic 피드백 (`active:scale-95`).
 *
 * 반환된 [MutableInteractionSource]를 clickable에 그대로 넘겨야 눌림이 감지된다.
 */
@Composable
fun rememberPressScale(): PressScale {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) PRESSED_SCALE else 1f,
        label = "pressScale",
    )
    return PressScale(interactionSource, scale)
}

/** [rememberPressScale]의 결과 — clickable에 넘길 소스와 적용할 배율. */
class PressScale(
    /** clickable/combinedClickable의 `interactionSource`로 넘긴다. */
    val interactionSource: MutableInteractionSource,
    private val scale: Float,
) {
    /** 눌림 배율을 적용한다. clickable보다 앞에 붙여야 터치 영역이 흔들리지 않는다. */
    fun applyTo(modifier: Modifier): Modifier = modifier.scale(scale)
}

private const val PRESSED_SCALE = 0.95f
