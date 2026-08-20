package io.rami.screenrecorder.core.designsystem.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.rami.screenrecorder.core.designsystem.theme.CardCorner

/** 카드 안쪽 기본 여백 (DESIGN_GUIDE.md 3절). */
val CardPadding = PaddingValues(24.dp)

/**
 * Kinetic 기본 카드 — 카드 서피스 + 1dp 외곽선 + 16dp 모서리.
 *
 * @param borderColor 강조 상태에서 외곽선 색을 바꾼다 (예: 선택 시 primary).
 */
@Composable
fun KineticCard(
    modifier: Modifier = Modifier,
    borderColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.outlineVariant,
    contentPadding: PaddingValues = CardPadding,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    Column(
        modifier =
            modifier
                .clip(CardCorner)
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .border(BorderStroke(1.dp, borderColor), CardCorner)
                .padding(contentPadding),
        content = content,
    )
}

/**
 * 제목 헤더 + 구분선으로 나뉜 행 목록을 담는 설정형 카드 (DESIGN_GUIDE.md 4절 "Settings Panels").
 *
 * [content] 안에서는 [SectionRowDivider]로 행 사이를 나눈다.
 */
@Composable
fun SectionCard(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(CardCorner)
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .border(BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), CardCorner),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .padding(horizontal = 24.dp, vertical = 16.dp),
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        content()
    }
}

/** [SectionCard] 안에서 행과 행 사이를 나누는 구분선. */
@Composable
fun SectionRowDivider() {
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}

/** 목록이 비었을 때의 중앙 안내 (아이콘 20% 불투명 + 문구). */
@Composable
fun EmptyState(
    icon: ImageVector,
    message: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(vertical = 80.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = EMPTY_ICON_ALPHA),
            modifier = Modifier.size(EMPTY_ICON_SIZE),
        )
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

private const val EMPTY_ICON_ALPHA = 0.2f
private val EMPTY_ICON_SIZE = 48.dp
