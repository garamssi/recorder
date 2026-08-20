package io.rami.screenrecorder.presentation.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.rami.screenrecorder.core.designsystem.component.CircleIconButton
import io.rami.screenrecorder.core.designsystem.component.MinTouchTarget
import io.rami.screenrecorder.core.designsystem.component.rememberPressScale
import io.rami.screenrecorder.core.designsystem.theme.TileCorner
import io.rami.screenrecorder.presentation.R

/**
 * 앱 셸 (DESIGN_GUIDE.md 3절 "Layout & Responsiveness").
 *
 * 가로에서는 좌측 레일, 세로에서는 상단 바 + 하단 내비게이션으로 전환한다.
 *
 * @param showChrome 레일/상단 바/하단 바를 표시할지. 플레이어처럼 전체 화면을 쓰는 목적지에서는 false다.
 *   [content]의 호출 위치는 고정이므로 크롬이 사라져도 NavHost 상태는 유지된다.
 * @param selected 현재 강조할 목적지. 하위 화면(휴지통)에서도 상위 목적지를 유지한다.
 * @param settingsSelected 설정 화면이 열려 있는지 (레일/상단 바의 설정 버튼 강조용).
 */
@Composable
fun AppShell(
    showChrome: Boolean,
    selected: ShellDestination?,
    settingsSelected: Boolean,
    onNavigate: (ShellDestination) -> Unit,
    onOpenSettings: () -> Unit,
    content: @Composable () -> Unit,
) {
    if (isLandscape()) {
        Row(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            if (showChrome) {
                NavigationRail(
                    selected = selected,
                    settingsSelected = settingsSelected,
                    onNavigate = onNavigate,
                    onOpenSettings = onOpenSettings,
                )
            }
            Box(modifier = Modifier.weight(1f).fillMaxHeight()) { content() }
        }
    } else {
        Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            if (showChrome) {
                ShellTopBar(settingsSelected = settingsSelected, onOpenSettings = onOpenSettings)
            }
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) { content() }
            if (showChrome) BottomNavigationBar(selected = selected, onNavigate = onNavigate)
        }
    }
}

/** 가로 방향 여부. 태블릿은 가로가 기본이므로 레일을 노출한다. */
@Composable
fun isLandscape(): Boolean {
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    return configuration.screenWidthDp >= configuration.screenHeightDp
}

@Composable
private fun NavigationRail(
    selected: ShellDestination?,
    settingsSelected: Boolean,
    onNavigate: (ShellDestination) -> Unit,
    onOpenSettings: () -> Unit,
) {
    Row {
        Column(
            modifier =
                Modifier
                    .width(RAIL_WIDTH)
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .padding(vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            BrandMark()
            ShellDestination.entries.forEach { destination ->
                NavButton(
                    icon = destination.icon,
                    label = stringResource(destination.labelRes),
                    selected = destination == selected,
                    showLabel = false,
                    onClick = { onNavigate(destination) },
                )
            }
            Spacer(Modifier.weight(1f))
            NavButton(
                icon = Icons.Default.Settings,
                label = stringResource(R.string.nav_settings),
                selected = settingsSelected,
                showLabel = false,
                onClick = onOpenSettings,
            )
        }
        VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

@Composable
private fun ShellTopBar(
    settingsSelected: Boolean,
    onOpenSettings: () -> Unit,
) {
    Column {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.RadioButtonChecked,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
                Text(
                    text = stringResource(R.string.home_app_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            CircleIconButton(
                icon = Icons.Default.Settings,
                contentDescription = stringResource(R.string.nav_settings),
                onClick = onOpenSettings,
                container =
                    if (settingsSelected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerHigh
                    },
            )
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

@Composable
private fun BottomNavigationBar(
    selected: ShellDestination?,
    onNavigate: (ShellDestination) -> Unit,
) {
    Column {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(BOTTOM_BAR_HEIGHT)
                    .background(MaterialTheme.colorScheme.surfaceContainer),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            ShellDestination.entries.forEach { destination ->
                NavButton(
                    icon = destination.icon,
                    label = stringResource(destination.labelRes),
                    selected = destination == selected,
                    showLabel = true,
                    onClick = { onNavigate(destination) },
                )
            }
        }
    }
}

/** 앱 브랜드 마크 — primary 10% 배경 위 REC 아이콘. */
@Composable
private fun BrandMark() {
    Box(
        modifier =
            Modifier
                .size(BRAND_MARK)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = BRAND_MARK_ALPHA)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Default.RadioButtonChecked,
            contentDescription = stringResource(R.string.home_app_title),
            tint = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun NavButton(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    showLabel: Boolean,
    onClick: () -> Unit,
) {
    val press = rememberPressScale()
    val container =
        if (selected) MaterialTheme.colorScheme.primary else androidx.compose.ui.graphics.Color.Transparent
    val tint =
        if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        modifier =
            press
                .applyTo(Modifier)
                .width(NAV_BUTTON_WIDTH)
                .clip(TileCorner)
                .background(container)
                .clickable(
                    interactionSource = press.interactionSource,
                    indication = null,
                    onClick = onClick,
                ).padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(imageVector = icon, contentDescription = label, tint = tint)
        if (showLabel) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = tint,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

/** 아이콘만 있는 내비 버튼도 최소 터치 타깃(48dp)을 넉넉히 넘기게 한다. */
private val NAV_BUTTON_WIDTH = MinTouchTarget + 16.dp
private val RAIL_WIDTH = 96.dp
private val BOTTOM_BAR_HEIGHT = 80.dp
private val BRAND_MARK = 48.dp
private const val BRAND_MARK_ALPHA = 0.12f
