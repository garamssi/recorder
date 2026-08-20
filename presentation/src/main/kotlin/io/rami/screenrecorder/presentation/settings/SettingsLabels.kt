package io.rami.screenrecorder.presentation.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.rami.screenrecorder.domain.model.CountdownDuration
import io.rami.screenrecorder.domain.model.LanguageSetting
import io.rami.screenrecorder.domain.model.MicrophoneDevice
import io.rami.screenrecorder.domain.model.OrientationPolicy
import io.rami.screenrecorder.domain.model.VideoCodec
import io.rami.screenrecorder.presentation.R

// 설정 선택지의 표시 문구. 섹션 구성은 SettingsSections.kt 참조.

@Composable
internal fun codecLabel(codec: VideoCodec): String =
    when (codec) {
        VideoCodec.H264 -> stringResource(R.string.settings_codec_h264)
        VideoCodec.HEVC -> stringResource(R.string.settings_codec_hevc)
    }

@Composable
internal fun countdownLabel(countdown: CountdownDuration): String =
    if (countdown == CountdownDuration.NONE) {
        stringResource(R.string.settings_countdown_none)
    } else {
        stringResource(R.string.settings_countdown_format, countdown.seconds)
    }

@Composable
internal fun orientationLabel(policy: OrientationPolicy): String =
    when (policy) {
        OrientationPolicy.FOLLOW_ROTATION -> stringResource(R.string.settings_orientation_follow)
        OrientationPolicy.LOCK_START_ORIENTATION -> stringResource(R.string.settings_orientation_lock)
    }

@Composable
internal fun microphoneDeviceLabel(device: MicrophoneDevice): String =
    when (device) {
        MicrophoneDevice.AUTO -> stringResource(R.string.settings_mic_device_auto)
        MicrophoneDevice.BUILT_IN -> stringResource(R.string.settings_mic_device_built_in)
        MicrophoneDevice.BLUETOOTH -> stringResource(R.string.settings_mic_device_bluetooth)
        MicrophoneDevice.WIRED -> stringResource(R.string.settings_mic_device_wired)
    }

@Composable
internal fun languageLabel(language: LanguageSetting): String =
    when (language) {
        LanguageSetting.KOREAN -> stringResource(R.string.settings_language_korean)
        LanguageSetting.ENGLISH -> stringResource(R.string.settings_language_english)
        LanguageSetting.SYSTEM -> stringResource(R.string.settings_language_system)
    }
