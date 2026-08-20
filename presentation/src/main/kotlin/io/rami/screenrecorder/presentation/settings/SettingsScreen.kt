package io.rami.screenrecorder.presentation.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import io.rami.screenrecorder.core.designsystem.component.CircleIconButton
import io.rami.screenrecorder.presentation.R
import io.rami.screenrecorder.presentation.home.ScreenPadding

/** 설정 본문 최대 폭 — 설정 카드는 목록보다 좁게 두어 읽기 쉽게 한다. */
private val SettingsMaxWidth = 840.dp

/** 설정 화면 (기능명세서 4절, DESIGN_GUIDE.md 4절 "Settings Panels"). */
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val settings by viewModel.settings.collectAsState()
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = ScreenPadding, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().widthIn(max = SettingsMaxWidth),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            SettingsHeader(onBack = onBack)
            val current = settings ?: return@Column
            VideoQualitySection(current, viewModel::update)
            RecordingBehaviorSection(current, viewModel::update)
            AudioSection(current, viewModel::update)
            StorageSection(current, viewModel::update)
            LanguageSection(current, viewModel::update)
            AboutSection()
        }
    }
}

@Composable
private fun SettingsHeader(onBack: () -> Unit) {
    androidx.compose.foundation.layout.Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        CircleIconButton(
            icon = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = stringResource(R.string.navigate_back),
            onClick = onBack,
        )
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.settings_title), style = MaterialTheme.typography.headlineLarge)
            Text(
                text = stringResource(R.string.settings_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
