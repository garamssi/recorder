package io.rami.screenrecorder.presentation.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.rami.screenrecorder.core.common.time.DurationFormatter
import io.rami.screenrecorder.presentation.R

/** 우측 패널: 최근 녹화 + 저장 공간 (기능명세서 2.1절). */
@Composable
internal fun SidePanel(
    uiState: HomeUiState,
    onOpenLibrary: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Card(modifier = Modifier.weight(1f)) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        stringResource(R.string.home_recent_title),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = stringResource(R.string.home_view_all),
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.clickable(onClick = onOpenLibrary),
                    )
                }
                if (uiState.recentRecordings.isEmpty()) {
                    Text(
                        stringResource(R.string.home_recent_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    uiState.recentRecordings.forEach { recording ->
                        Text(
                            text = recording.displayName,
                            style = MaterialTheme.typography.bodyLarge,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
        Card(colors = CardDefaults.cardColors()) {
            Text(
                text =
                    stringResource(
                        R.string.home_storage_format,
                        formatGigabytes(uiState.availableBytes),
                        DurationFormatter.formatElapsed(uiState.estimatedRecordableTime),
                    ),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(20.dp),
            )
        }
    }
}

/** 카운트다운 오버레이 (기능명세서 3절, DESIGN_GUIDE 1c: 딤 72%, 숫자 120sp, 탭=스킵). */
@Composable
internal fun CountdownOverlay(
    remainingSeconds: Int,
    onTap: () -> Unit,
) {
    // 뒤로가기도 탭과 동일하게 스킵 처리한다 (화면 이탈로 좀비 세션이 생기는 것을 방지).
    androidx.activity.compose.BackHandler(onBack = onTap)
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = COUNTDOWN_DIM_ALPHA))
                .clickable(onClick = onTap),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = remainingSeconds.toString(),
                color = Color.White,
                fontSize = 120.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                style =
                    MaterialTheme.typography.displaySmall.copy(
                        fontFeatureSettings = TABULAR_NUMBERS_FEATURE,
                    ),
            )
            Text(
                text = stringResource(R.string.home_countdown_skip_hint),
                color = Color.White,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

internal fun formatGigabytes(bytes: Long): String {
    val gigabytes = bytes / BYTES_PER_GB_FLOAT
    return "%.1fGB".format(gigabytes)
}

private const val COUNTDOWN_DIM_ALPHA = 0.72f
private const val TABULAR_NUMBERS_FEATURE = "tnum"

private const val BYTES_PER_GB_FLOAT = 1_000_000_000f
