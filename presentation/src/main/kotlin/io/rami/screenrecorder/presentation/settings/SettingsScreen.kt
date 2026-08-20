package io.rami.screenrecorder.presentation.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import io.rami.screenrecorder.presentation.R

/** 설정 섹션 (기능명세서 4절, DESIGN_GUIDE 1k: 태블릿 2-페인). */
private enum class SettingsSection(
    val titleRes: Int,
) {
    RECORDING(R.string.settings_section_recording),
    AUDIO(R.string.settings_section_audio),
    STORAGE(R.string.settings_section_storage),
    DISPLAY(R.string.settings_section_display),
    LANGUAGE(R.string.settings_section_language),
    ABOUT(R.string.settings_section_about),
}

/** 설정 화면 (기능명세서 4절). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val settings by viewModel.settings.collectAsState()
    var selectedSection by rememberSaveable { mutableStateOf(SettingsSection.RECORDING) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.navigate_back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        val current = settings ?: return@Scaffold
        Row(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 32.dp),
        ) {
            Column(modifier = Modifier.width(NAV_PANE_WIDTH.dp)) {
                SettingsSection.entries.forEach { section ->
                    NavigationDrawerItem(
                        label = { Text(stringResource(section.titleRes)) },
                        selected = section == selectedSection,
                        onClick = { selectedSection = section },
                    )
                }
            }
            Column(
                modifier =
                    Modifier
                        .weight(1f)
                        .padding(start = 24.dp)
                        .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                when (selectedSection) {
                    SettingsSection.RECORDING -> RecordingSection(current, viewModel::update)
                    SettingsSection.AUDIO -> AudioSection(current, viewModel::update)
                    SettingsSection.STORAGE -> StorageSection(current, viewModel::update)
                    SettingsSection.DISPLAY -> DisplaySection(current, viewModel::update)
                    SettingsSection.LANGUAGE -> LanguageSection(current, viewModel::update)
                    SettingsSection.ABOUT -> AboutSection()
                }
            }
        }
    }
}

private const val NAV_PANE_WIDTH = 240
