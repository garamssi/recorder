package io.rami.screenrecorder.presentation.settings

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import io.rami.screenrecorder.core.designsystem.component.SectionCard
import io.rami.screenrecorder.core.designsystem.component.SectionRowDivider
import io.rami.screenrecorder.domain.model.AppSettings
import io.rami.screenrecorder.domain.model.AudioSource
import io.rami.screenrecorder.domain.model.CountdownDuration
import io.rami.screenrecorder.domain.model.FrameRate
import io.rami.screenrecorder.domain.model.LanguageSetting
import io.rami.screenrecorder.domain.model.MicrophoneDevice
import io.rami.screenrecorder.domain.model.OrientationPolicy
import io.rami.screenrecorder.domain.model.VideoCodec
import io.rami.screenrecorder.presentation.R
import io.rami.screenrecorder.presentation.home.audioSourceLabel
import io.rami.screenrecorder.presentation.home.bitrateLabel
import io.rami.screenrecorder.presentation.home.resolutionLabel

// 설정 상세 섹션 (기능명세서 4절). 화면 골격은 SettingsScreen.kt, 공용 행은 SettingsRows.kt 참조.

/** 해상도/프레임레이트/비트레이트/코덱 (기능명세서 4.1절). */
@Composable
internal fun VideoQualitySection(
    settings: AppSettings,
    update: ((AppSettings) -> AppSettings) -> Unit,
) {
    SectionCard(title = stringResource(R.string.settings_section_video)) {
        ChoiceRow(
            title = stringResource(R.string.options_resolution),
            options = resolutionSettingChoices,
            selected = settings.recording.resolution,
            label = { option -> resolutionLabel(option) },
            onSelected = { option ->
                update { it.copy(recording = it.recording.copy(resolution = option)) }
            },
        )
        SectionRowDivider()
        ChoiceRow(
            title = stringResource(R.string.options_frame_rate),
            options = FrameRate.entries,
            selected = settings.recording.frameRate,
            label = { frameRate -> stringResource(R.string.preset_fps_format, frameRate.fps) },
            onSelected = { frameRate ->
                update { it.copy(recording = it.recording.copy(frameRate = frameRate)) }
            },
        )
        SectionRowDivider()
        ChoiceRow(
            title = stringResource(R.string.settings_bitrate),
            options = bitrateSettingChoices,
            selected = settings.recording.bitrate,
            label = { option -> bitrateLabel(option) },
            onSelected = { option ->
                update { it.copy(recording = it.recording.copy(bitrate = option)) }
            },
        )
        SectionRowDivider()
        ChoiceRow(
            title = stringResource(R.string.settings_codec),
            options = VideoCodec.entries,
            selected = settings.recording.codec,
            label = { codec -> codecLabel(codec) },
            onSelected = { codec -> update { it.copy(recording = it.recording.copy(codec = codec)) } },
        )
    }
}

/** 카운트다운/회전/터치 표시/플로팅 컨트롤 (기능명세서 3, 4.1, 5, 11.1절). */
@Composable
internal fun RecordingBehaviorSection(
    settings: AppSettings,
    update: ((AppSettings) -> AppSettings) -> Unit,
) {
    SectionCard(title = stringResource(R.string.settings_section_recording)) {
        ChoiceRow(
            title = stringResource(R.string.settings_countdown),
            options = CountdownDuration.entries,
            selected = settings.recording.countdown,
            label = { countdown -> countdownLabel(countdown) },
            onSelected = { countdown ->
                update { it.copy(recording = it.recording.copy(countdown = countdown)) }
            },
        )
        SectionRowDivider()
        ChoiceRow(
            title = stringResource(R.string.settings_orientation),
            options = OrientationPolicy.entries,
            selected = settings.recording.orientationPolicy,
            label = { policy -> orientationLabel(policy) },
            onSelected = { policy ->
                update { it.copy(recording = it.recording.copy(orientationPolicy = policy)) }
            },
        )
        SectionRowDivider()
        // 터치 표시는 시스템 권한(WRITE_SECURE_SETTINGS) 제약으로 1차 범위 보류 (기능명세서 4.1절 [결정]).
        ToggleRow(
            title = stringResource(R.string.settings_show_touches),
            hint = stringResource(R.string.settings_show_touches_unsupported),
            checked = false,
            onChanged = {},
            enabled = false,
        )
        SectionRowDivider()
        FloatingBubbleRow(settings = settings, update = update)
    }
}

/**
 * 플로팅 캡처 버튼 토글 (기능명세서 11.1절).
 *
 * 다른 앱 위에 그리려면 오버레이 권한이 필요하므로, 켤 때 권한이 없으면 시스템 설정으로 보낸다.
 * 설정값 자체는 그대로 켜 두고, 권한이 생기면 앱이 다음 관찰에서 버블을 띄운다.
 */
@Composable
private fun FloatingBubbleRow(
    settings: AppSettings,
    update: ((AppSettings) -> AppSettings) -> Unit,
) {
    val context = LocalContext.current
    val canDrawOverlays = Settings.canDrawOverlays(context)
    ToggleRow(
        title = stringResource(R.string.settings_floating_bubble),
        hint =
            stringResource(
                if (settings.showFloatingBubble && !canDrawOverlays) {
                    R.string.settings_floating_bubble_permission_needed
                } else {
                    R.string.settings_floating_bubble_hint
                },
            ),
        checked = settings.showFloatingBubble,
        onChanged = { enabled ->
            update { it.copy(showFloatingBubble = enabled) }
            if (enabled && !canDrawOverlays) context.requestOverlayPermission()
        },
    )
}

/** 시스템 "다른 앱 위에 표시" 설정 화면을 연다. */
private fun Context.requestOverlayPermission() {
    startActivity(
        Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            "package:$packageName".toUri(),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )
}

/** 오디오 소스/장치/볼륨 (기능명세서 4.2절). */
@Composable
internal fun AudioSection(
    settings: AppSettings,
    update: ((AppSettings) -> AppSettings) -> Unit,
) {
    SectionCard(title = stringResource(R.string.settings_section_audio)) {
        ChoiceRow(
            title = stringResource(R.string.options_audio_source),
            options = AudioSource.entries,
            selected = settings.recording.audioSource,
            label = { source -> audioSourceLabel(source) },
            onSelected = { source ->
                update { it.copy(recording = it.recording.copy(audioSource = source)) }
            },
        )
        SectionRowDivider()
        ChoiceRow(
            title = stringResource(R.string.settings_mic_device),
            options = MicrophoneDevice.entries,
            selected = settings.recording.microphoneDevice,
            label = { device -> microphoneDeviceLabel(device) },
            onSelected = { device ->
                update { it.copy(recording = it.recording.copy(microphoneDevice = device)) }
            },
        )
        SectionRowDivider()
        VolumeRow(
            title = stringResource(R.string.settings_mic_volume),
            volume = settings.recording.microphoneVolume,
            onChanged = { volume ->
                update { it.copy(recording = it.recording.copy(microphoneVolume = volume)) }
            },
        )
        SectionRowDivider()
        VolumeRow(
            title = stringResource(R.string.settings_internal_volume),
            volume = settings.recording.internalVolume,
            onChanged = { volume ->
                update { it.copy(recording = it.recording.copy(internalVolume = volume)) }
            },
        )
        SectionRowDivider()
        Text(
            text = stringResource(R.string.settings_capture_policy_notice),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
        )
    }
}

/** 파일명/저장 위치/휴지통 (기능명세서 4.3, 6.1절). */
@Composable
internal fun StorageSection(
    settings: AppSettings,
    update: ((AppSettings) -> AppSettings) -> Unit,
) {
    SectionCard(title = stringResource(R.string.settings_section_storage)) {
        FilePrefixField(current = settings.fileNamePrefix, update = update)
        SectionRowDivider()
        // 사용자 지정 폴더(SAF)는 2차 이터레이션 보류 (기능명세서 6.1절 [결정]).
        LabeledValue(
            title = stringResource(R.string.settings_storage_location),
            value = stringResource(R.string.settings_storage_location_default),
            hint = stringResource(R.string.settings_storage_location_custom_planned),
        )
        SectionRowDivider()
        LabeledValue(
            title = stringResource(R.string.settings_trash_retention),
            value = stringResource(R.string.settings_trash_retention_value),
        )
    }
}

/** 표시 언어 (기능명세서 4.5절). 테마는 다크 고정이라 선택지가 없다 (DESIGN_GUIDE.md 0절). */
@Composable
internal fun LanguageSection(
    settings: AppSettings,
    update: ((AppSettings) -> AppSettings) -> Unit,
) {
    SectionCard(title = stringResource(R.string.settings_section_display)) {
        ChoiceRow(
            title = stringResource(R.string.settings_section_language),
            options = LanguageSetting.entries,
            selected = settings.language,
            label = { language -> languageLabel(language) },
            onSelected = { language -> update { it.copy(language = language) } },
        )
        SectionRowDivider()
        LabeledValue(
            title = stringResource(R.string.settings_theme),
            value = stringResource(R.string.settings_theme_dark_fixed),
            hint = stringResource(R.string.settings_theme_dark_fixed_hint),
        )
    }
}

/** 버전/개인정보 안내 (기능명세서 4.6절). */
@Composable
internal fun AboutSection() {
    SectionCard(title = stringResource(R.string.settings_section_about)) {
        LabeledValue(
            title = stringResource(R.string.settings_about_version),
            value = appVersionName(),
        )
        SectionRowDivider()
        Text(
            text = stringResource(R.string.settings_about_privacy),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
        )
    }
}

/** 설치된 패키지의 versionName을 그대로 표시한다 (app 모듈 버전과 항상 일치). */
@Composable
private fun appVersionName(): String {
    val context = androidx.compose.ui.platform.LocalContext.current
    return androidx.compose.runtime.remember(context) {
        context.packageManager
            .getPackageInfo(context.packageName, 0)
            .versionName
            ?: ""
    }
}
