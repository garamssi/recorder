package io.rami.screenrecorder.presentation.home

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.rami.screenrecorder.core.common.time.DurationFormatter
import io.rami.screenrecorder.domain.model.AudioSource
import io.rami.screenrecorder.domain.model.BitrateOption
import io.rami.screenrecorder.domain.model.RecordingConfig
import io.rami.screenrecorder.domain.model.ResolutionOption
import io.rami.screenrecorder.domain.model.TimeLimit
import io.rami.screenrecorder.presentation.R

/** 해상도 라벨 (예: "1080p", "기기 최대"). */
@Composable
fun resolutionLabel(option: ResolutionOption): String =
    when (option) {
        is ResolutionOption.DeviceMax -> stringResource(R.string.preset_resolution_device_max)
        is ResolutionOption.Fixed ->
            stringResource(R.string.preset_resolution_format, option.resolution.height)
    }

/** 오디오 소스 라벨. */
@Composable
fun audioSourceLabel(source: AudioSource): String =
    when (source) {
        AudioSource.SILENT -> stringResource(R.string.preset_audio_silent)
        AudioSource.INTERNAL -> stringResource(R.string.preset_audio_internal)
        AudioSource.MICROPHONE -> stringResource(R.string.preset_audio_microphone)
        AudioSource.INTERNAL_AND_MICROPHONE -> stringResource(R.string.preset_audio_mixed)
    }

/** 비트레이트 라벨. */
@Composable
fun bitrateLabel(option: BitrateOption): String =
    when (option) {
        is BitrateOption.Auto -> stringResource(R.string.preset_bitrate_auto)
        is BitrateOption.Fixed -> stringResource(R.string.preset_bitrate_format, option.megabitsPerSecond)
    }

/** 프리셋 요약 한 줄 (기능명세서 2.1절: "1080p, 60fps, 15Mbps, 내부+마이크 [, 10분 제한]"). */
@Composable
fun presetSummary(preset: RecordingConfig): String {
    val parts =
        buildList {
            add(resolutionLabel(preset.resolution))
            add(stringResource(R.string.preset_fps_format, preset.frameRate.fps))
            add(bitrateLabel(preset.bitrate))
            add(audioSourceLabel(preset.audioSource))
            (preset.timeLimit as? TimeLimit.Limited)?.let {
                add(
                    stringResource(
                        R.string.preset_time_limit_format,
                        DurationFormatter.formatElapsed(it.duration),
                    ),
                )
            }
        }
    return parts.joinToString(separator = " · ")
}
