package io.rami.screenrecorder.presentation.library

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import io.rami.screenrecorder.core.common.time.DurationFormatter
import io.rami.screenrecorder.domain.model.NameValidation
import io.rami.screenrecorder.domain.model.Recording
import io.rami.screenrecorder.domain.model.RecordingNameValidator
import io.rami.screenrecorder.presentation.R
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** 이름 변경 다이얼로그 (기능명세서 6.3절: 확장자 고정, 유효성 검사). */
@Composable
fun RenameDialog(
    initialName: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by rememberSaveable { mutableStateOf(initialName.removeSuffix(MP4_EXTENSION)) }
    val validation = RecordingNameValidator.validate(text)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.rename_title)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                suffix = { Text(MP4_EXTENSION) },
                isError = validation != NameValidation.Valid,
                supportingText = {
                    if (validation != NameValidation.Valid) {
                        Text(stringResource(R.string.rename_invalid))
                    }
                },
            )
        },
        confirmButton = {
            TextButton(
                enabled = validation == NameValidation.Valid,
                onClick = {
                    onConfirm(text)
                    onDismiss()
                },
            ) { Text(stringResource(R.string.rename_confirm)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.dialog_cancel)) }
        },
    )
}

/** 휴지통 이동 확인 다이얼로그 (기능명세서 7.3절: 삭제는 항상 2단계). */
@Composable
fun DeleteConfirmDialog(
    count: Int,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.delete_confirm_title)) },
        text = { Text(stringResource(R.string.delete_confirm_message, count)) },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm()
                    onDismiss()
                },
            ) { Text(stringResource(R.string.delete_confirm_action)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.dialog_cancel)) }
        },
    )
}

/** 상세 정보 다이얼로그 (기능명세서 7.2절 5번). */
@Composable
fun DetailDialog(
    recording: Recording,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.detail_title)) },
        text = {
            Column {
                DetailRow(stringResource(R.string.detail_name), recording.displayName)
                DetailRow(
                    stringResource(R.string.detail_resolution),
                    "${recording.resolution.width}x${recording.resolution.height}",
                )
                DetailRow(
                    stringResource(R.string.detail_duration),
                    DurationFormatter.formatElapsed(recording.duration),
                )
                DetailRow(stringResource(R.string.detail_size), formatMegabytes(recording.sizeBytes))
                DetailRow(
                    stringResource(R.string.detail_created),
                    formatDateTime(recording.createdAtEpochMillis),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.options_confirm)) }
        },
    )
}

@Composable
private fun DetailRow(
    label: String,
    value: String,
) {
    Text(text = "$label: $value")
}

/** 파일 크기 표시 (MB). */
fun formatMegabytes(bytes: Long): String = "%.1fMB".format(bytes / BYTES_PER_MB)

/** 생성일시 표시 (기능명세서 7.1절: "2026-08-19 14:30"). */
fun formatDateTime(epochMillis: Long): String =
    DATE_TIME_FORMAT.format(Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()))

private val DATE_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
private const val BYTES_PER_MB = 1_000_000f
private const val MP4_EXTENSION = ".mp4"
