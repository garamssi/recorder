package io.rami.screenrecorder

import android.Manifest
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import io.rami.screenrecorder.core.designsystem.theme.ScreenRecorderTheme
import io.rami.screenrecorder.data.recorder.MediaProjectionTokenHolder
import io.rami.screenrecorder.domain.model.AppSettings
import io.rami.screenrecorder.domain.model.CaptureModeKind
import io.rami.screenrecorder.domain.model.CaptureRegion
import io.rami.screenrecorder.domain.model.LanguageSetting
import io.rami.screenrecorder.domain.usecase.ObserveSettingsUseCase
import io.rami.screenrecorder.presentation.R
import io.rami.screenrecorder.presentation.navigation.AppNavHost
import io.rami.screenrecorder.presentation.navigation.RecordingControlActions
import io.rami.screenrecorder.presentation.overlay.RegionSelectionOverlay
import io.rami.screenrecorder.service.FloatingCaptureService
import io.rami.screenrecorder.service.RecordingForegroundService
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 앱의 단일 진입점 Activity — DI 조립과 시스템 연동(동의 다이얼로그, 권한, 서비스 시작)만 담당하고
 * 화면은 presentation의 [AppNavHost]에 위임한다 (CLAUDE.md 3절: app = 조립 계층).
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var projectionTokenHolder: MediaProjectionTokenHolder

    @Inject lateinit var observeSettings: ObserveSettingsUseCase

    private val regionOverlay by lazy { RegionSelectionOverlay(this) }

    /** 부분 영역 모드에서 오버레이로 확정한 영역 (동의 완료 시 서비스로 전달, 메모리 전용). */
    private var pendingRegion: CaptureRegion? = null

    /** onResume마다 증가한다 — 권한 설정 화면에서 돌아온 뒤 버블 상태를 다시 평가하는 트리거다. */
    private val lifecycleResumeCount = androidx.compose.runtime.mutableIntStateOf(0)

    private val consentLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val data = result.data
            if (result.resultCode == RESULT_OK && data != null) {
                projectionTokenHolder.store(result.resultCode, data)
                startForegroundService(RecordingForegroundService.startIntent(this, pendingRegion))
            }
            pendingRegion = null
        }

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        permissionLauncher.launch(
            arrayOf(Manifest.permission.POST_NOTIFICATIONS, Manifest.permission.RECORD_AUDIO),
        )
        // 플로팅 버블이 부분 영역 녹화를 요청하면 영역 선택 오버레이가 필요해 앱을 경유한다.
        if (intent?.action == ACTION_START_RECORDING) startRecordingFlow()
        setContent {
            val settings by observeSettings().collectAsState(initial = AppSettings.DEFAULT)
            LaunchedEffect(settings.language) { applyAppLocale(settings.language) }
            // 설정과 오버레이 권한이 모두 갖춰졌을 때만 버블 서비스를 띄운다 (기능명세서 11.1절).
            // 권한 화면에서 돌아오면 Activity가 재개되며 이 효과가 다시 평가된다.
            LaunchedEffect(settings.showFloatingBubble, lifecycleResumeCount.intValue) {
                applyFloatingBubbleSetting(settings.showFloatingBubble)
            }
            ScreenRecorderTheme {
                AppNavHost(
                    control =
                        RecordingControlActions(
                            onStart = ::startRecordingFlow,
                            onStop = { startService(RecordingForegroundService.stopIntent(this)) },
                            onPause = { startService(RecordingForegroundService.pauseIntent(this)) },
                            onResume = { startService(RecordingForegroundService.resumeIntent(this)) },
                        ),
                )
            }
        }
    }

    /**
     * 플로팅 버블 서비스를 설정에 맞춘다.
     *
     * 오버레이 권한이 없으면 서비스를 띄우지 않는다 — 권한 없이 시작하면 서비스가 스스로 멈춰
     * 알림만 깜빡이기 때문이다. 권한 요청은 설정 화면이 담당한다.
     */
    private fun applyFloatingBubbleSetting(enabled: Boolean) {
        if (enabled && Settings.canDrawOverlays(this)) {
            startForegroundService(FloatingCaptureService.startIntent(this))
        } else {
            startService(FloatingCaptureService.hideIntent(this))
        }
    }

    /**
     * 녹화 시작 플로우 (기능명세서 2.2절): 부분 영역 모드면
     * 오버레이 권한 확인 → 영역 선택 오버레이 → 확인 후 동의 다이얼로그.
     */
    private fun startRecordingFlow() {
        lifecycleScope.launch {
            val mode = observeSettings().first().selectedCaptureMode
            if (mode != CaptureModeKind.REGION) {
                requestProjectionConsent()
                return@launch
            }
            if (!Settings.canDrawOverlays(this@MainActivity)) {
                // 명세 2.2절: 권한 안내 → 시스템 설정으로 이동
                Toast
                    .makeText(this@MainActivity, R.string.region_overlay_permission_needed, Toast.LENGTH_LONG)
                    .show()
                startActivity(
                    Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:$packageName"),
                    ),
                )
                return@launch
            }
            regionOverlay.show(
                onConfirm = { region ->
                    pendingRegion = region
                    requestProjectionConsent()
                },
                onCancel = {},
            )
        }
    }

    override fun onResume() {
        super.onResume()
        lifecycleResumeCount.intValue++
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.action == ACTION_START_RECORDING) startRecordingFlow()
    }

    private fun requestProjectionConsent() {
        val manager = getSystemService(MediaProjectionManager::class.java)
        consentLauncher.launch(manager.createScreenCaptureIntent())
    }

    companion object {
        private const val ACTION_START_RECORDING = "io.rami.screenrecorder.action.OPEN_AND_START_RECORDING"

        /** 부분 영역 녹화처럼 앱 UI가 필요한 플로를 태우기 위해 앱을 여는 인텐트. */
        fun startRecordingIntent(context: android.content.Context): Intent =
            Intent(context, MainActivity::class.java)
                .setAction(ACTION_START_RECORDING)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
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
