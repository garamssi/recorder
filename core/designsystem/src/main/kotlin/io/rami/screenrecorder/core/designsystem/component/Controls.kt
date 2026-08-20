package io.rami.screenrecorder.core.designsystem.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import io.rami.screenrecorder.core.designsystem.theme.ControlCorner
import io.rami.screenrecorder.core.designsystem.theme.TileCorner

/** 최소 터치 타깃 (DESIGN_GUIDE.md 3절). */
val MinTouchTarget = 48.dp

/**
 * iOS 스타일 토글 스위치 (DESIGN_GUIDE.md 5절 "Toggles").
 *
 * 켜지면 트랙이 primary로 바뀌고 노브가 오른쪽으로 미끄러진다.
 */
@Composable
fun KineticSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val trackColor by animateColorAsState(
        targetValue =
            when {
                !enabled -> MaterialTheme.colorScheme.surfaceContainerHigh
                checked -> MaterialTheme.colorScheme.primary
                else -> MaterialTheme.colorScheme.surfaceContainerHighest
            },
        label = "switchTrack",
    )
    val knobOffset by animateDpAsState(
        targetValue = if (checked) SWITCH_TRAVEL else 0.dp,
        label = "switchKnob",
    )
    Box(
        modifier =
            modifier
                .size(width = SWITCH_WIDTH, height = SWITCH_HEIGHT)
                .clip(CircleShape)
                .background(trackColor)
                .clickable(enabled = enabled) { onCheckedChange(!checked) }
                .padding(SWITCH_INSET),
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            modifier =
                Modifier
                    .offset(x = knobOffset)
                    .size(SWITCH_KNOB)
                    .clip(CircleShape)
                    .background(if (enabled) Color.White else Color.White.copy(alpha = DISABLED_ALPHA)),
        )
    }
}

/** 강조 채움 버튼 (primary 배경). 확인·삭제 등 주 동작에 쓴다. */
@Composable
fun PrimaryActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
) {
    ActionButton(
        text = text,
        onClick = onClick,
        modifier = modifier,
        icon = icon,
        enabled = enabled,
        container = MaterialTheme.colorScheme.primary,
        content = MaterialTheme.colorScheme.onPrimary,
    )
}

/** 보조 채움 버튼 (secondary 서피스). 취소·선택 모드 진입 등에 쓴다. */
@Composable
fun SecondaryActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
) {
    ActionButton(
        text = text,
        onClick = onClick,
        modifier = modifier,
        icon = icon,
        enabled = enabled,
        container = MaterialTheme.colorScheme.surfaceContainerHigh,
        content = MaterialTheme.colorScheme.onSurface,
    )
}

@Composable
private fun ActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier,
    icon: ImageVector?,
    enabled: Boolean,
    container: Color,
    content: Color,
) {
    val press = rememberPressScale()
    val alpha = if (enabled) 1f else DISABLED_ALPHA
    Row(
        modifier =
            press
                .applyTo(modifier)
                .clip(ControlCorner)
                .background(container.copy(alpha = alpha))
                .clickable(
                    enabled = enabled,
                    interactionSource = press.interactionSource,
                    indication = null,
                    onClick = onClick,
                ).heightIn(min = MinTouchTarget)
                .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = content.copy(alpha = alpha), modifier = Modifier.size(18.dp))
        }
        Text(text = text, style = MaterialTheme.typography.labelLarge, color = content.copy(alpha = alpha))
    }
}

/** 원형 아이콘 버튼 (secondary 서피스 위 아이콘). 상단 바·오버레이 컨트롤에 쓴다. */
@Composable
fun CircleIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    container: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    tint: Color = MaterialTheme.colorScheme.onSurface,
    enabled: Boolean = true,
) {
    val press = rememberPressScale()
    Box(
        modifier =
            press
                .applyTo(modifier)
                .size(MinTouchTarget)
                .clip(CircleShape)
                .background(container.copy(alpha = if (enabled) 1f else DISABLED_ALPHA))
                .clickable(
                    enabled = enabled,
                    interactionSource = press.interactionSource,
                    indication = null,
                    onClick = onClick,
                ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint.copy(alpha = if (enabled) 1f else DISABLED_ALPHA),
            modifier = Modifier.size(20.dp),
        )
    }
}

/**
 * 설정 카드 안의 한 행 (DESIGN_GUIDE.md 4절).
 *
 * 오른쪽에는 값 텍스트·chevron·토글 등 [trailing]을 배치한다.
 */
@Composable
fun SettingRow(
    label: String,
    modifier: Modifier = Modifier,
    supportingText: String? = null,
    onClick: (() -> Unit)? = null,
    trailing: @Composable () -> Unit = {},
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
                .heightIn(min = MinTouchTarget)
                .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(text = label, style = MaterialTheme.typography.bodyLarge)
            if (supportingText != null) {
                Text(
                    text = supportingText,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        trailing()
    }
}

/** [SettingRow]의 오른쪽 값 텍스트. */
@Composable
fun SettingValue(value: String) {
    Text(
        text = value,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/**
 * 현재 설정 요약 뱃지 (DESIGN_GUIDE.md 4절 "Active Configuration").
 *
 * 배경 위에 얹는 작은 타일: 아이콘(primary) + 라벨(대문자 캡션) + 값.
 */
@Composable
fun ConfigBadge(
    icon: ImageVector,
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .clip(ControlCorner)
                .background(MaterialTheme.colorScheme.background)
                .border(BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), ControlCorner)
                .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(16.dp),
        )
        Column {
            Text(
                text = label.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(text = value, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
        }
    }
}

/**
 * 선택 가능한 큰 옵션 타일 (DESIGN_GUIDE.md 5절 "Selection Highlights").
 *
 * 선택되면 primary 외곽선과 아이콘 배경 강조가 함께 켜진다.
 */
@Composable
fun SelectableTile(
    icon: ImageVector,
    title: String,
    description: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val press = rememberPressScale()
    val border by animateColorAsState(
        targetValue =
            if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
        label = "tileBorder",
    )
    KineticCard(
        borderColor = border,
        modifier =
            press
                .applyTo(modifier)
                .clickable(interactionSource = press.interactionSource, indication = null, onClick = onClick),
    ) {
        TileIcon(icon = icon, selected = selected)
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
        )
        Text(
            text = description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun TileIcon(
    icon: ImageVector,
    selected: Boolean,
) {
    val container by animateColorAsState(
        targetValue =
            if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.surfaceContainerHigh
            },
        label = "tileIconBackground",
    )
    Box(
        modifier = Modifier.size(TILE_ICON_BOX).clip(TileCorner).background(container),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint =
                if (selected) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            modifier = Modifier.size(TILE_ICON),
        )
    }
}

private val SWITCH_WIDTH = 44.dp
private val SWITCH_HEIGHT = 26.dp
private val SWITCH_KNOB = 18.dp
private val SWITCH_INSET = 4.dp
private val SWITCH_TRAVEL = SWITCH_WIDTH - SWITCH_KNOB - SWITCH_INSET * 2
private val TILE_ICON_BOX = 56.dp
private val TILE_ICON = 28.dp
private const val DISABLED_ALPHA = 0.5f
