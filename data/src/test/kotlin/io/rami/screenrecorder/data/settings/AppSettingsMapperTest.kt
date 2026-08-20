package io.rami.screenrecorder.data.settings

import androidx.datastore.preferences.core.emptyPreferences
import io.rami.screenrecorder.domain.model.AppSettings
import io.rami.screenrecorder.domain.model.AudioSource
import io.rami.screenrecorder.domain.model.BitrateOption
import io.rami.screenrecorder.domain.model.CaptureModeKind
import io.rami.screenrecorder.domain.model.CountdownDuration
import io.rami.screenrecorder.domain.model.FileNamePrefix
import io.rami.screenrecorder.domain.model.FrameRate
import io.rami.screenrecorder.domain.model.LanguageSetting
import io.rami.screenrecorder.domain.model.MicrophoneDevice
import io.rami.screenrecorder.domain.model.OrientationPolicy
import io.rami.screenrecorder.domain.model.Resolution
import io.rami.screenrecorder.domain.model.ResolutionOption
import io.rami.screenrecorder.domain.model.StorageLocation
import io.rami.screenrecorder.domain.model.TimeLimit
import io.rami.screenrecorder.domain.model.VideoCodec
import io.rami.screenrecorder.domain.model.VolumePercent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.minutes

class AppSettingsMapperTest {
    @Test
    fun `빈 저장소는 기본 설정으로 읽힌다`() {
        assertEquals(AppSettings.DEFAULT, AppSettingsMapper.fromPreferences(emptyPreferences()))
    }

    @Test
    fun `설정을 저장하고 다시 읽으면 동일하다 (왕복 보존)`() {
        val custom =
            AppSettings(
                recording =
                    io.rami.screenrecorder.domain.model.RecordingConfig.DEFAULT.copy(
                        resolution = ResolutionOption.DeviceMax,
                        frameRate = FrameRate.FPS_30,
                        bitrate = BitrateOption.Fixed(megabitsPerSecond = 8),
                        codec = VideoCodec.HEVC,
                        audioSource = AudioSource.INTERNAL_AND_MICROPHONE,
                        microphoneDevice = MicrophoneDevice.BLUETOOTH,
                        microphoneVolume = VolumePercent(150),
                        internalVolume = VolumePercent(50),
                        countdown = CountdownDuration.TEN_SECONDS,
                        orientationPolicy = OrientationPolicy.LOCK_START_ORIENTATION,
                        timeLimit = TimeLimit.Limited(10.minutes),
                    ),
                selectedCaptureMode = CaptureModeKind.REGION,
                fileNamePrefix = FileNamePrefix("My_Clip"),
                storageLocation = StorageLocation.CustomTree("content://tree/primary"),
                language = LanguageSetting.ENGLISH,
                showFloatingBubble = true,
                showTouches = true,
            )

        val restored = AppSettingsMapper.fromPreferences(AppSettingsMapper.toPreferences(custom))

        assertEquals(custom, restored)
    }

    @Test
    fun `고정 해상도 720p도 왕복 보존된다`() {
        val settings =
            AppSettings.DEFAULT.copy(
                recording =
                    io.rami.screenrecorder.domain.model.RecordingConfig.DEFAULT.copy(
                        resolution = ResolutionOption.Fixed(Resolution.HD),
                    ),
            )

        val restored = AppSettingsMapper.fromPreferences(AppSettingsMapper.toPreferences(settings))

        assertEquals(ResolutionOption.Fixed(Resolution.HD), restored.recording.resolution)
    }

    @Test
    fun `알 수 없는 enum 값은 기본값으로 폴백한다`() {
        val corrupted =
            AppSettingsMapper.toPreferences(AppSettings.DEFAULT).toMutablePreferences().apply {
                this[AppSettingsMapper.KEY_LANGUAGE] = "ESPERANTO"
            }

        assertEquals(LanguageSetting.KOREAN, AppSettingsMapper.fromPreferences(corrupted).language)
    }
}
