package io.rami.screenrecorder.presentation.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.rami.screenrecorder.domain.model.AppSettings
import io.rami.screenrecorder.domain.model.AudioSource
import io.rami.screenrecorder.domain.model.CountdownDuration
import io.rami.screenrecorder.domain.model.FrameRate
import io.rami.screenrecorder.domain.model.LanguageSetting
import io.rami.screenrecorder.domain.model.MicrophoneDevice
import io.rami.screenrecorder.domain.model.OrientationPolicy
import io.rami.screenrecorder.domain.model.ThemeSetting
import io.rami.screenrecorder.domain.model.VideoCodec
import io.rami.screenrecorder.presentation.R
import io.rami.screenrecorder.presentation.home.audioSourceLabel
import io.rami.screenrecorder.presentation.home.bitrateLabel
import io.rami.screenrecorder.presentation.home.resolutionLabel

// 설정 상세 섹션 (기능명세서 4절). 화면 골격은 SettingsScreen.kt, 공용 행은 SettingsRows.kt 참조.

@Composable
internal fun RecordingSection(
    settings: AppSettings,
    update: ((AppSettings) -> AppSettings) -> Unit,
) {
    VideoQualityRows(settings, update)
    RecordingBehaviorRows(settings, update)
}

/** 해상도/프레임레이트/비트레이트/코덱 (기능명세서 4.1절). */
@Composable
private fun VideoQualityRows(
    settings: AppSettings,
    update: ((AppSettings) -> AppSettings) -> Unit,
) {
    ChoiceRow(
        title = stringResource(R.string.options_resolution),
        options = resolutionSettingChoices,
        selected = settings.recording.resolution,
        label = { option -> resolutionLabel(option) },
        onSelected = { option ->
            update { it.copy(recording = it.recording.copy(resolution = option)) }
        },
    )
    ChoiceRow(
        title = stringResource(R.string.options_frame_rate),
        options = FrameRate.entries,
        selected = settings.recording.frameRate,
        label = { frameRate -> stringResource(R.string.preset_fps_format, frameRate.fps) },
        onSelected = { frameRate ->
            update { it.copy(recording = it.recording.copy(frameRate = frameRate)) }
        },
    )
    ChoiceRow(
        title = stringResource(R.string.settings_bitrate),
        options = bitrateSettingChoices,
        selected = settings.recording.bitrate,
        label = { option -> bitrateLabel(option) },
        onSelected = { option ->
            update { it.copy(recording = it.recording.copy(bitrate = option)) }
        },
    )
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
}

/** 카운트다운/회전/터치 표시/플로팅 컨트롤 (기능명세서 3, 4.1, 5, 11.1절). */
@Composable
private fun RecordingBehaviorRows(
    settings: AppSettings,
    update: ((AppSettings) -> AppSettings) -> Unit,
) {
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
    ToggleRow(
        title = stringResource(R.string.settings_show_touches),
        checked = settings.showTouches,
        onChanged = { enabled -> update { it.copy(showTouches = enabled) } },
    )
    ToggleRow(
        title = stringResource(R.string.settings_floating_bubble),
        hint = stringResource(R.string.settings_floating_bubble_hint),
        checked = settings.showFloatingBubble,
        onChanged = { enabled -> update { it.copy(showFloatingBubble = enabled) } },
    )
}

@Composable
internal fun AudioSection(
    settings: AppSettings,
    update: ((AppSettings) -> AppSettings) -> Unit,
) {
    ChoiceRow(
        title = stringResource(R.string.options_audio_source),
        options = AudioSource.entries,
        selected = settings.recording.audioSource,
        label = { source -> audioSourceLabel(source) },
        onSelected = { source ->
            update { it.copy(recording = it.recording.copy(audioSource = source)) }
        },
    )
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
internal fun StorageSection(
    settings: AppSettings,
    update: ((AppSettings) -> AppSettings) -> Unit,
) {
    FilePrefixField(current = settings.fileNamePrefix, update = update)
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
internal fun DisplaySection(
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
internal fun LanguageSection(
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
internal fun AboutSection() {
    LabeledValue(
        title = stringResource(R.string.settings_about_version),
        value = appVersionName(),
    )
    Text(
        text = stringResource(R.string.settings_about_privacy),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** 설치된 패키지의 versionName을 그대로 표시한다 (app 모듈 버전과 항상 일치). */
@Composable
private fun appVersionName(): String {
    val context = androidx.compose.ui.platform.LocalContext.current
    return context.packageManager
        .getPackageInfo(context.packageName, 0)
        .versionName
        ?: ""
}
