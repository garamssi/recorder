package io.rami.screenrecorder.presentation.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.rami.screenrecorder.domain.model.PendingRecovery
import io.rami.screenrecorder.presentation.R

/**
 * 크래시 복구 다이얼로그 (기능명세서 6.1절: 복구/삭제 제안).
 *
 * @param isRecovering 이 파일을 복구/삭제하는 중인지. 진행 중에는 진행 표시를 띄우고
 *   두 버튼을 모두 잠근다 — 1시간짜리 녹화는 remux 에 수 초가 걸려, 화면이 그대로면
 *   사용자가 안 눌린 줄 알고 다시 누른다 (기능명세서 6.1절 [결정]).
 */
@Composable
internal fun RecoveryDialog(
    recovery: PendingRecovery,
    isRecovering: Boolean,
    onRecover: () -> Unit,
    onDiscard: () -> Unit,
) {
    AlertDialog(
        // 명시적 선택을 강제한다 — 임의 dismiss로 임시 파일이 방치되지 않게.
        onDismissRequest = {},
        title = { Text(stringResource(R.string.recovery_title)) },
        text = {
            if (isRecovering) {
                ProgressRow()
            } else {
                Text(
                    stringResource(
                        R.string.recovery_message,
                        formatRecoverySize(recovery.sizeBytes),
                    ),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onRecover, enabled = !isRecovering) {
                Text(stringResource(R.string.recovery_restore))
            }
        },
        dismissButton = {
            TextButton(onClick = onDiscard, enabled = !isRecovering) {
                Text(stringResource(R.string.recovery_delete))
            }
        },
    )
}

/** 진행 중임을 알리는 줄. 남은 시간을 알 수 없어 무한 회전으로 둔다. */
@Composable
private fun ProgressRow() {
    val label = stringResource(R.string.recovery_in_progress)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SPINNER_GAP.dp),
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(SPINNER_SIZE.dp).semantics { contentDescription = label },
            strokeWidth = SPINNER_STROKE.dp,
        )
        Text(text = label, style = MaterialTheme.typography.bodyMedium)
    }
}

/** 1MB 미만은 KB로 표시해 작은 잔여 파일도 크기를 알아볼 수 있게 한다. */
private fun formatRecoverySize(bytes: Long): String =
    if (bytes < BYTES_PER_MB) {
        "%.0fKB".format(bytes / BYTES_PER_KB)
    } else {
        "%.1fMB".format(bytes / BYTES_PER_MB)
    }

private const val BYTES_PER_KB = 1_000f
private const val BYTES_PER_MB = 1_000_000f
private const val SPINNER_SIZE = 20
private const val SPINNER_STROKE = 2
private const val SPINNER_GAP = 12
