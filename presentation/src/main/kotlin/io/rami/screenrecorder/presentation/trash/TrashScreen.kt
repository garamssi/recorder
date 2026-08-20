package io.rami.screenrecorder.presentation.trash

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import io.rami.screenrecorder.domain.model.RecordingId
import io.rami.screenrecorder.presentation.R

/** 휴지통 화면 (기능명세서 9절, DESIGN_GUIDE 1j). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrashScreen(
    onBack: () -> Unit,
    viewModel: TrashViewModel = hiltViewModel(),
) {
    val items by viewModel.items.collectAsState()
    var deleteTargets by remember { mutableStateOf<List<RecordingId>?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.trash_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.navigate_back),
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            deleteTargets = items.orEmpty().map { it.recording.id }
                        },
                    ) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = stringResource(R.string.trash_empty_all),
                        )
                    }
                },
            )
        },
    ) { padding ->
        val currentItems = items
        if (currentItems.isNullOrEmpty()) {
            Text(
                text = stringResource(R.string.trash_empty),
                style = MaterialTheme.typography.bodyLarge,
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(32.dp),
            )
        } else {
            LazyColumn(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(horizontal = 32.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(currentItems, key = { it.recording.id.value }) { item ->
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(text = item.recording.displayName, maxLines = 1)
                            Text(
                                text = stringResource(R.string.trash_days_left, item.daysUntilDeletion),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                        TextButton(onClick = { viewModel.onRestore(listOf(item.recording.id)) }) {
                            Text(stringResource(R.string.trash_restore))
                        }
                        TextButton(onClick = { deleteTargets = listOf(item.recording.id) }) {
                            Text(
                                text = stringResource(R.string.trash_delete_forever),
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
            }
        }
    }

    deleteTargets?.let { targets ->
        AlertDialog(
            onDismissRequest = { deleteTargets = null },
            title = { Text(stringResource(R.string.trash_delete_forever)) },
            text = { Text(stringResource(R.string.trash_delete_forever_message, targets.size)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.onPermanentlyDeleteConfirmed(targets)
                        deleteTargets = null
                    },
                ) { Text(stringResource(R.string.trash_delete_forever)) }
            },
            dismissButton = {
                TextButton(onClick = { deleteTargets = null }) {
                    Text(stringResource(R.string.dialog_cancel))
                }
            },
        )
    }
}
