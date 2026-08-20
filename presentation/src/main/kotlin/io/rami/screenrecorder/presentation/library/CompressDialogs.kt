package io.rami.screenrecorder.presentation.library

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.rami.screenrecorder.domain.model.CompressionPreset
import io.rami.screenrecorder.domain.model.TranscodeJob
import io.rami.screenrecorder.presentation.R

/** 압축 프리셋 선택 다이얼로그 (기능명세서 8절 [결정]: 3종 + 재인코딩 안내). */
@Composable
fun CompressDialog(
    onConfirm: (CompressionPreset) -> Unit,
    onDismiss: () -> Unit,
) {
    var selected by rememberSaveable { mutableStateOf(CompressionPreset.STANDARD) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.compress_title)) },
        text = {
            Column {
                // 명세 8절: 재인코딩임을 상단에 명시
                Text(
                    text = stringResource(R.string.compress_notice),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                CompressionPreset.entries.forEach { preset ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 8.dp),
                    ) {
                        RadioButton(selected = selected == preset, onClick = { selected = preset })
                        Column {
                            Text(presetLabel(preset), style = MaterialTheme.typography.bodyLarge)
                            Text(
                                presetDescription(preset),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(selected)
                    onDismiss()
                },
            ) { Text(stringResource(R.string.compress_start)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.dialog_cancel)) }
        },
    )
}

/** 진행률 표시줄 + 취소 (기능명세서 8절: 진행률, 취소 가능). */
@Composable
fun CompressProgressBar(
    job: TranscodeJob,
    onCancel: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.compress_in_progress, job.progressPercent),
                style = MaterialTheme.typography.bodySmall,
            )
            LinearProgressIndicator(
                progress = { job.progressPercent / PERCENT_MAX },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        TextButton(onClick = onCancel) { Text(stringResource(R.string.dialog_cancel)) }
    }
}

/** 완료 후 원본 휴지통 이동 확인 (기능명세서 8절 [결정]). */
@Composable
fun TrashOriginalDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.compress_done_title)) },
        text = { Text(stringResource(R.string.compress_done_trash_prompt)) },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm()
                    onDismiss()
                },
            ) { Text(stringResource(R.string.compress_trash_original)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.compress_keep_original)) }
        },
    )
}

@Composable
private fun presetLabel(preset: CompressionPreset): String =
    when (preset) {
        CompressionPreset.HIGH_EFFICIENCY -> stringResource(R.string.compress_preset_high_efficiency)
        CompressionPreset.STANDARD -> stringResource(R.string.compress_preset_standard)
        CompressionPreset.MAXIMUM -> stringResource(R.string.compress_preset_maximum)
    }

@Composable
private fun presetDescription(preset: CompressionPreset): String =
    when (preset) {
        CompressionPreset.HIGH_EFFICIENCY ->
            stringResource(R.string.compress_preset_high_efficiency_desc)

        CompressionPreset.STANDARD -> stringResource(R.string.compress_preset_standard_desc)
        CompressionPreset.MAXIMUM -> stringResource(R.string.compress_preset_maximum_desc)
    }

private const val PERCENT_MAX = 100f
