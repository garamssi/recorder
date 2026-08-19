package io.rami.screenrecorder.presentation.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import io.rami.screenrecorder.domain.model.AppSettings
import io.rami.screenrecorder.domain.model.CountdownDuration
import io.rami.screenrecorder.domain.model.LanguageSetting
import io.rami.screenrecorder.domain.model.MicrophoneDevice
import io.rami.screenrecorder.domain.model.OrientationPolicy
import io.rami.screenrecorder.domain.model.ThemeSetting
import io.rami.screenrecorder.domain.model.VideoCodec
import io.rami.screenrecorder.domain.model.VolumePercent
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
    var selectedSection by remember { mutableStateOf(SettingsSection.RECORDING) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = null,
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
                    SettingsSection.STORAGE -> StorageSection(current)
                    SettingsSection.DISPLAY -> DisplaySection(current, viewModel::update)
                    SettingsSection.LANGUAGE -> LanguageSection(current, viewModel::update)
                    SettingsSection.ABOUT -> AboutSection()
                }
            }
        }
    }
}

@Composable
private fun RecordingSection(
    settings: AppSettings,
    update: ((AppSettings) -> AppSettings) -> Unit,
) {
    ChoiceRow(
        title = stringResource(R.string.settings_codec),
        options = VideoCodec.entries,
        selected = settings.recording.codec,
        label = { codec ->
            when (codec) {
                VideoCodec.H264 -> stringResource(R.string.settings_codec_h264)
                VideoCodec.HEVC -> stringResource(R.string.settings_codec_hevc)
            }
        },
        onSelected = { codec -> update { it.copy(recording = it.recording.copy(codec = codec)) } },
    )
    ChoiceRow(
        title = stringResource(R.string.settings_countdown),
        options = CountdownDuration.entries,
        selected = settings.recording.countdown,
        label = { countdown ->
            if (countdown == CountdownDuration.NONE) {
                stringResource(R.string.settings_countdown_none)
            } else {
                stringResource(R.string.settings_countdown_format, countdown.seconds)
            }
        },
        onSelected = { countdown ->
            update { it.copy(recording = it.recording.copy(countdown = countdown)) }
        },
    )
    ChoiceRow(
        title = stringResource(R.string.settings_orientation),
        options = OrientationPolicy.entries,
        selected = settings.recording.orientationPolicy,
        label = { policy ->
            when (policy) {
                OrientationPolicy.FOLLOW_ROTATION -> stringResource(R.string.settings_orientation_follow)
                OrientationPolicy.LOCK_START_ORIENTATION ->
                    stringResource(R.string.settings_orientation_lock)
            }
        },
        onSelected = { policy ->
            update { it.copy(recording = it.recording.copy(orientationPolicy = policy)) }
        },
    )
}

@Composable
private fun AudioSection(
    settings: AppSettings,
    update: ((AppSettings) -> AppSettings) -> Unit,
) {
    ChoiceRow(
        title = stringResource(R.string.settings_mic_device),
        options = MicrophoneDevice.entries,
        selected = settings.recording.microphoneDevice,
        label = { device ->
            when (device) {
                MicrophoneDevice.AUTO -> stringResource(R.string.settings_mic_device_auto)
                MicrophoneDevice.BUILT_IN -> stringResource(R.string.settings_mic_device_built_in)
                MicrophoneDevice.BLUETOOTH -> stringResource(R.string.settings_mic_device_bluetooth)
                MicrophoneDevice.WIRED -> stringResource(R.string.settings_mic_device_wired)
            }
        },
        onSelected = { device ->
            update { it.copy(recording = it.recording.copy(microphoneDevice = device)) }
        },
    )
    VolumeRow(
        title = stringResource(R.string.settings_mic_volume),
        volume = settings.recording.microphoneVolume,
        onChanged = { volume ->
            update { it.copy(recording = it.recording.copy(microphoneVolume = volume)) }
        },
    )
    VolumeRow(
        title = stringResource(R.string.settings_internal_volume),
        volume = settings.recording.internalVolume,
        onChanged = { volume ->
            update { it.copy(recording = it.recording.copy(internalVolume = volume)) }
        },
    )
    HorizontalDivider()
    Text(
        text = stringResource(R.string.settings_capture_policy_notice),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun StorageSection(settings: AppSettings) {
    LabeledValue(
        title = stringResource(R.string.settings_file_prefix),
        value = settings.fileNamePrefix.value,
    )
    LabeledValue(
        title = stringResource(R.string.settings_storage_location),
        value = stringResource(R.string.settings_storage_location_default),
    )
    LabeledValue(
        title = stringResource(R.string.settings_trash_retention),
        value = stringResource(R.string.settings_trash_retention_value),
    )
}

@Composable
private fun DisplaySection(
    settings: AppSettings,
    update: ((AppSettings) -> AppSettings) -> Unit,
) {
    ChoiceRow(
        title = stringResource(R.string.settings_theme),
        options = ThemeSetting.entries,
        selected = settings.theme,
        label = { theme ->
            when (theme) {
                ThemeSetting.SYSTEM -> stringResource(R.string.settings_theme_system)
                ThemeSetting.LIGHT -> stringResource(R.string.settings_theme_light)
                ThemeSetting.DARK -> stringResource(R.string.settings_theme_dark)
            }
        },
        onSelected = { theme -> update { it.copy(theme = theme) } },
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = stringResource(R.string.settings_dynamic_color),
            style = MaterialTheme.typography.bodyLarge,
        )
        Switch(
            checked = settings.dynamicColor,
            onCheckedChange = { enabled -> update { it.copy(dynamicColor = enabled) } },
        )
    }
}

@Composable
private fun LanguageSection(
    settings: AppSettings,
    update: ((AppSettings) -> AppSettings) -> Unit,
) {
    ChoiceRow(
        title = stringResource(R.string.settings_section_language),
        options = LanguageSetting.entries,
        selected = settings.language,
        label = { language ->
            when (language) {
                LanguageSetting.KOREAN -> stringResource(R.string.settings_language_korean)
                LanguageSetting.ENGLISH -> stringResource(R.string.settings_language_english)
                LanguageSetting.SYSTEM -> stringResource(R.string.settings_language_system)
            }
        },
        onSelected = { language -> update { it.copy(language = language) } },
    )
}

@Composable
private fun AboutSection() {
    LabeledValue(
        title = stringResource(R.string.settings_about_version),
        value = APP_VERSION_NAME,
    )
    Text(
        text = stringResource(R.string.settings_about_privacy),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun <T> ChoiceRow(
    title: String,
    options: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    onSelected: (T) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text = title, style = MaterialTheme.typography.bodyLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { option ->
                FilterChip(
                    selected = option == selected,
                    onClick = { onSelected(option) },
                    label = { Text(label(option)) },
                )
            }
        }
    }
}

@Composable
private fun VolumeRow(
    title: String,
    volume: VolumePercent,
    onChanged: (VolumePercent) -> Unit,
) {
    Column {
        Text(
            text =
                "$title — " +
                    stringResource(R.string.settings_volume_percent_format, volume.value),
            style = MaterialTheme.typography.bodyLarge,
        )
        Slider(
            value = volume.value.toFloat(),
            onValueChange = { raw -> onChanged(VolumePercent(raw.toInt())) },
            valueRange = VolumePercent.MIN.toFloat()..VolumePercent.MAX.toFloat(),
        )
    }
}

@Composable
private fun LabeledValue(
    title: String,
    value: String,
) {
    Column {
        Text(text = title, style = MaterialTheme.typography.bodyLarge)
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private const val NAV_PANE_WIDTH = 240

// TODO(Stage 10): BuildConfig 버전으로 대체한다.
private const val APP_VERSION_NAME = "0.1.0"
