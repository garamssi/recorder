package io.rami.screenrecorder.presentation.library

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.net.toUri
import io.rami.screenrecorder.core.designsystem.component.CircleIconButton
import io.rami.screenrecorder.domain.model.Recording
import io.rami.screenrecorder.presentation.R

// 목록 항목의 더보기 메뉴와 공유 인텐트. 카드/행 레이아웃은 LibraryContent.kt 참조.

/** 더보기 메뉴 (기능명세서 7.2절: 재생/이름 변경/공유/상세 정보/압축/삭제). */
@Composable
internal fun ItemMenu(
    recording: Recording,
    actions: ItemActions,
) {
    var expanded by remember { mutableStateOf(false) }
    val context = LocalContext.current
    Box {
        CircleIconButton(
            icon = Icons.Default.MoreVert,
            contentDescription = stringResource(R.string.menu_more),
            container = Color.Transparent,
            onClick = { expanded = true },
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            MenuItem(R.string.menu_play) {
                expanded = false
                actions.onPlay(recording)
            }
            MenuItem(R.string.menu_rename) {
                expanded = false
                actions.onRename(recording)
            }
            MenuItem(R.string.menu_share) {
                expanded = false
                shareRecording(context, recording)
            }
            MenuItem(R.string.menu_details) {
                expanded = false
                actions.onDetail(recording)
            }
            MenuItem(R.string.menu_compress) {
                expanded = false
                actions.onCompress(recording)
            }
            MenuItem(R.string.menu_delete) {
                expanded = false
                actions.onDelete(recording)
            }
        }
    }
}

@Composable
private fun MenuItem(
    labelRes: Int,
    onClick: () -> Unit,
) {
    DropdownMenuItem(text = { Text(stringResource(labelRes)) }, onClick = onClick)
}

/** 다중 공유 (기능명세서 7.3절: 선택 모드 상단 공유). */
internal fun shareRecordings(
    context: Context,
    recordings: List<Recording>,
) {
    if (recordings.isEmpty()) return
    if (recordings.size == 1) {
        shareRecording(context, recordings.first())
        return
    }
    val uris = ArrayList(recordings.map { it.contentUri.toUri() })
    val sendIntent =
        Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = SHARE_MIME_TYPE
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    context.startActivity(Intent.createChooser(sendIntent, null))
}

/** 공유 (기능명세서 7.2절: ACTION_SEND + 읽기 권한 부여). */
internal fun shareRecording(
    context: Context,
    recording: Recording,
) {
    val sendIntent =
        Intent(Intent.ACTION_SEND).apply {
            type = SHARE_MIME_TYPE
            putExtra(Intent.EXTRA_STREAM, recording.contentUri.toUri())
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    context.startActivity(Intent.createChooser(sendIntent, null))
}

private const val SHARE_MIME_TYPE = "video/mp4"
