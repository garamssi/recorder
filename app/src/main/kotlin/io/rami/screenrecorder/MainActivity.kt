package io.rami.screenrecorder

import android.Manifest
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import dagger.hilt.android.AndroidEntryPoint
import io.rami.screenrecorder.core.designsystem.theme.ScreenRecorderTheme
import io.rami.screenrecorder.data.recorder.MediaProjectionTokenHolder
import io.rami.screenrecorder.domain.model.AppSettings
import io.rami.screenrecorder.domain.model.LanguageSetting
import io.rami.screenrecorder.domain.model.ThemeSetting
import io.rami.screenrecorder.domain.usecase.ObserveSettingsUseCase
import io.rami.screenrecorder.presentation.navigation.AppNavHost
import io.rami.screenrecorder.presentation.navigation.RecordingControlActions
import io.rami.screenrecorder.service.RecordingForegroundService
import javax.inject.Inject

/**
 * 앱의 단일 진입점 Activity — DI 조립과 시스템 연동(동의 다이얼로그, 권한, 서비스 시작)만 담당하고
 * 화면은 presentation의 [AppNavHost]에 위임한다 (CLAUDE.md 3절: app = 조립 계층).
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var projectionTokenHolder: MediaProjectionTokenHolder

    @Inject lateinit var observeSettings: ObserveSettingsUseCase

    private val consentLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val data = result.data
            if (result.resultCode == RESULT_OK && data != null) {
                projectionTokenHolder.store(result.resultCode, data)
                startForegroundService(RecordingForegroundService.startIntent(this))
            }
        }

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        permissionLauncher.launch(
            arrayOf(Manifest.permission.POST_NOTIFICATIONS, Manifest.permission.RECORD_AUDIO),
        )
        setContent {
            val settings by observeSettings().collectAsState(initial = AppSettings.DEFAULT)
            LaunchedEffect(settings.language) { applyAppLocale(settings.language) }
            ScreenRecorderTheme(
                darkTheme =
                    when (settings.theme) {
                        ThemeSetting.SYSTEM -> isSystemInDarkTheme()
                        ThemeSetting.LIGHT -> false
                        ThemeSetting.DARK -> true
                    },
                dynamicColor = settings.dynamicColor,
            ) {
                AppNavHost(
                    control =
                        RecordingControlActions(
                            onStart = ::requestProjectionConsent,
                            onStop = { startService(RecordingForegroundService.stopIntent(this)) },
                            onPause = { startService(RecordingForegroundService.pauseIntent(this)) },
                            onResume = { startService(RecordingForegroundService.resumeIntent(this)) },
                        ),
                )
            }
        }
    }

    private fun requestProjectionConsent() {
        val manager = getSystemService(MediaProjectionManager::class.java)
        consentLauncher.launch(manager.createScreenCaptureIntent())
    }

    /** 앱별 언어 설정 적용 (기능명세서 4.5절 [결정], Android 13+ per-app language). */
    private fun applyAppLocale(language: LanguageSetting) {
        val localeManager = getSystemService(android.app.LocaleManager::class.java)
        val wanted =
            when (language) {
                LanguageSetting.KOREAN -> android.os.LocaleList.forLanguageTags("ko")
                LanguageSetting.ENGLISH -> android.os.LocaleList.forLanguageTags("en")
                LanguageSetting.SYSTEM -> android.os.LocaleList.getEmptyLocaleList()
            }
        // 동일 값 재적용은 액티비티 재생성을 유발하지 않지만, 불필요한 호출을 피한다.
        if (localeManager.applicationLocales != wanted) {
            localeManager.applicationLocales = wanted
        }
    }
}
