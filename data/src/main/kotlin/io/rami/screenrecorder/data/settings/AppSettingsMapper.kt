package io.rami.screenrecorder.data.settings

import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import io.rami.screenrecorder.domain.model.AppSettings
import io.rami.screenrecorder.domain.model.BitrateOption
import io.rami.screenrecorder.domain.model.FileNamePrefix
import io.rami.screenrecorder.domain.model.Resolution
import io.rami.screenrecorder.domain.model.ResolutionOption
import io.rami.screenrecorder.domain.model.StorageLocation
import io.rami.screenrecorder.domain.model.TimeLimit
import io.rami.screenrecorder.domain.model.VolumePercent
import kotlin.time.Duration.Companion.seconds

/**
 * [AppSettings] <-> DataStore Preferences 매핑 (기능명세서 4절).
 *
 * 알 수 없는 값(손상/버전 변경)은 항목 단위로 기본값에 폴백한다.
 */
object AppSettingsMapper {
    private val KEY_RESOLUTION = stringPreferencesKey("resolution")
    private val KEY_FRAME_RATE = stringPreferencesKey("frame_rate")
    private val KEY_BITRATE_MBPS = intPreferencesKey("bitrate_mbps")
    private val KEY_CODEC = stringPreferencesKey("codec")
    private val KEY_AUDIO_SOURCE = stringPreferencesKey("audio_source")
    private val KEY_MIC_DEVICE = stringPreferencesKey("mic_device")
    private val KEY_MIC_VOLUME = intPreferencesKey("mic_volume")
    private val KEY_INTERNAL_VOLUME = intPreferencesKey("internal_volume")
    private val KEY_COUNTDOWN = stringPreferencesKey("countdown")
    private val KEY_ORIENTATION = stringPreferencesKey("orientation")
    private val KEY_TIME_LIMIT_SECONDS = longPreferencesKey("time_limit_seconds")
    private val KEY_CAPTURE_MODE = stringPreferencesKey("capture_mode")
    private val KEY_FILE_PREFIX = stringPreferencesKey("file_prefix")
    private val KEY_STORAGE_TREE_URI = stringPreferencesKey("storage_tree_uri")

    /** 손상 값 폴백 테스트용으로 공개하는 키. */
    val KEY_LANGUAGE = stringPreferencesKey("language")

    private val KEY_FLOATING_BUBBLE = booleanPreferencesKey("floating_bubble")
    private val KEY_SHOW_TOUCHES = booleanPreferencesKey("show_touches")

    private const val RESOLUTION_DEVICE_MAX = "DEVICE_MAX"
    private const val BITRATE_AUTO = 0
    private const val TIME_LIMIT_NONE = 0L

    /** [preferences]를 설정으로 변환한다. 누락/손상 항목은 기본값. */
    fun fromPreferences(preferences: Preferences): AppSettings {
        val defaults = AppSettings.DEFAULT
        val recordingDefaults = defaults.recording
        val recording =
            recordingDefaults.copy(
                resolution = preferences.readResolution(recordingDefaults.resolution),
                frameRate = preferences.readEnum(KEY_FRAME_RATE, recordingDefaults.frameRate),
                bitrate = preferences.readBitrate(recordingDefaults.bitrate),
                codec = preferences.readEnum(KEY_CODEC, recordingDefaults.codec),
                audioSource = preferences.readEnum(KEY_AUDIO_SOURCE, recordingDefaults.audioSource),
                microphoneDevice = preferences.readEnum(KEY_MIC_DEVICE, recordingDefaults.microphoneDevice),
                microphoneVolume = preferences.readVolume(KEY_MIC_VOLUME, recordingDefaults.microphoneVolume),
                internalVolume = preferences.readVolume(KEY_INTERNAL_VOLUME, recordingDefaults.internalVolume),
                countdown = preferences.readEnum(KEY_COUNTDOWN, recordingDefaults.countdown),
                orientationPolicy = preferences.readEnum(KEY_ORIENTATION, recordingDefaults.orientationPolicy),
                timeLimit = preferences.readTimeLimit(recordingDefaults.timeLimit),
            )
        return AppSettings(
            recording = recording,
            selectedCaptureMode = preferences.readEnum(KEY_CAPTURE_MODE, defaults.selectedCaptureMode),
            fileNamePrefix = preferences.readPrefix(defaults.fileNamePrefix),
            storageLocation =
                preferences[KEY_STORAGE_TREE_URI]
                    ?.let(StorageLocation::CustomTree)
                    ?: StorageLocation.MediaStoreDefault,
            language = preferences.readEnum(KEY_LANGUAGE, defaults.language),
            showFloatingBubble = preferences[KEY_FLOATING_BUBBLE] ?: defaults.showFloatingBubble,
            showTouches = preferences[KEY_SHOW_TOUCHES] ?: defaults.showTouches,
        )
    }

    /** [settings]를 Preferences로 직렬화한다. */
    fun toPreferences(settings: AppSettings): Preferences {
        val recording = settings.recording
        val preferences = mutablePreferencesOf()
        preferences[KEY_RESOLUTION] =
            when (val resolution = recording.resolution) {
                is ResolutionOption.DeviceMax -> RESOLUTION_DEVICE_MAX
                is ResolutionOption.Fixed -> "${resolution.resolution.width}x${resolution.resolution.height}"
            }
        preferences[KEY_FRAME_RATE] = recording.frameRate.name
        preferences[KEY_BITRATE_MBPS] =
            when (val bitrate = recording.bitrate) {
                is BitrateOption.Auto -> BITRATE_AUTO
                is BitrateOption.Fixed -> bitrate.megabitsPerSecond
            }
        preferences[KEY_CODEC] = recording.codec.name
        preferences[KEY_AUDIO_SOURCE] = recording.audioSource.name
        preferences[KEY_MIC_DEVICE] = recording.microphoneDevice.name
        preferences[KEY_MIC_VOLUME] = recording.microphoneVolume.value
        preferences[KEY_INTERNAL_VOLUME] = recording.internalVolume.value
        preferences[KEY_COUNTDOWN] = recording.countdown.name
        preferences[KEY_ORIENTATION] = recording.orientationPolicy.name
        preferences[KEY_TIME_LIMIT_SECONDS] =
            when (val limit = recording.timeLimit) {
                is TimeLimit.None -> TIME_LIMIT_NONE
                is TimeLimit.Limited -> limit.duration.inWholeSeconds
            }
        preferences[KEY_CAPTURE_MODE] = settings.selectedCaptureMode.name
        preferences[KEY_FILE_PREFIX] = settings.fileNamePrefix.value
        (settings.storageLocation as? StorageLocation.CustomTree)?.let {
            preferences[KEY_STORAGE_TREE_URI] = it.treeUri
        }
        preferences[KEY_LANGUAGE] = settings.language.name
        preferences[KEY_FLOATING_BUBBLE] = settings.showFloatingBubble
        preferences[KEY_SHOW_TOUCHES] = settings.showTouches
        return preferences
    }

    private inline fun <reified T : Enum<T>> Preferences.readEnum(
        key: Preferences.Key<String>,
        default: T,
    ): T = this[key]?.let { stored -> enumValues<T>().firstOrNull { it.name == stored } } ?: default

    private fun Preferences.readResolution(default: ResolutionOption): ResolutionOption {
        val stored = this[KEY_RESOLUTION] ?: return default
        if (stored == RESOLUTION_DEVICE_MAX) return ResolutionOption.DeviceMax
        val (width, height) =
            stored.split("x").mapNotNull(String::toIntOrNull).takeIf { it.size == 2 }
                ?: return default
        return runCatching { ResolutionOption.Fixed(Resolution(width, height)) }.getOrDefault(default)
    }

    private fun Preferences.readBitrate(default: BitrateOption): BitrateOption =
        when (val stored = this[KEY_BITRATE_MBPS]) {
            null -> default
            BITRATE_AUTO -> BitrateOption.Auto
            else -> runCatching { BitrateOption.Fixed(stored) }.getOrDefault(default)
        }

    private fun Preferences.readVolume(
        key: Preferences.Key<Int>,
        default: VolumePercent,
    ): VolumePercent = this[key]?.let { runCatching { VolumePercent(it) }.getOrNull() } ?: default

    private fun Preferences.readTimeLimit(default: TimeLimit): TimeLimit =
        when (val seconds = this[KEY_TIME_LIMIT_SECONDS]) {
            null -> default
            TIME_LIMIT_NONE -> TimeLimit.None
            else -> runCatching { TimeLimit.Limited(seconds.seconds) }.getOrDefault(default)
        }

    private fun Preferences.readPrefix(default: FileNamePrefix): FileNamePrefix =
        this[KEY_FILE_PREFIX]?.let { runCatching { FileNamePrefix(it) }.getOrNull() } ?: default
}
