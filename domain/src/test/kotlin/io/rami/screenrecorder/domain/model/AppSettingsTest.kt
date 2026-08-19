package io.rami.screenrecorder.domain.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.minutes

class AppSettingsTest {
    @Test
    fun `기본 설정은 기능명세서 4절의 기본값과 일치한다`() {
        val settings = AppSettings.DEFAULT

        assertEquals(RecordingConfig.DEFAULT, settings.recording)
        assertEquals(CaptureModeKind.FULL_SCREEN, settings.selectedCaptureMode)
        assertEquals(FileNamePrefix.DEFAULT, settings.fileNamePrefix)
        assertEquals(StorageLocation.MediaStoreDefault, settings.storageLocation)
        assertEquals(ThemeSetting.SYSTEM, settings.theme)
        assertEquals(true, settings.dynamicColor)
        assertEquals(LanguageSetting.KOREAN, settings.language)
        assertEquals(false, settings.showFloatingBubble)
        assertEquals(false, settings.showTouches)
    }

    @Test
    fun `테마 선택지는 시스템_라이트_다크 3가지다`() {
        assertEquals(
            listOf(ThemeSetting.SYSTEM, ThemeSetting.LIGHT, ThemeSetting.DARK),
            ThemeSetting.entries.toList(),
        )
    }

    @Test
    fun `언어 선택지는 한국어_영어_시스템 3가지다`() {
        assertEquals(
            listOf(LanguageSetting.KOREAN, LanguageSetting.ENGLISH, LanguageSetting.SYSTEM),
            LanguageSetting.entries.toList(),
        )
    }

    @Test
    fun `설정에서 녹화 구성을 그대로 꺼내 쓴다`() {
        val custom =
            AppSettings.DEFAULT.copy(
                recording =
                    RecordingConfig.DEFAULT.copy(
                        frameRate = FrameRate.FPS_30,
                        audioSource = AudioSource.MICROPHONE,
                        timeLimit = TimeLimit.Limited(10.minutes),
                    ),
            )

        assertEquals(FrameRate.FPS_30, custom.recording.frameRate)
        assertEquals(AudioSource.MICROPHONE, custom.recording.audioSource)
        assertEquals(TimeLimit.Limited(10.minutes), custom.recording.timeLimit)
    }
}
