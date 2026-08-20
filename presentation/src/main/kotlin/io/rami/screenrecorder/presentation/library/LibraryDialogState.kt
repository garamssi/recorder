package io.rami.screenrecorder.presentation.library

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.rami.screenrecorder.domain.model.Recording
import io.rami.screenrecorder.domain.model.RecordingId

/** 목록 화면이 띄우는 다이얼로그들의 상태 묶음. */
internal class LibraryDialogState {
    var renameTarget by mutableStateOf<Recording?>(null)
    var detailTarget by mutableStateOf<Recording?>(null)
    var compressTarget by mutableStateOf<Recording?>(null)
    var deleteSingleTarget by mutableStateOf<RecordingId?>(null)
    var duplicateSuggestion by mutableStateOf<LibraryEvent.RenameNeedsSuffix?>(null)
    var showDeleteConfirm by mutableStateOf(false)
}
