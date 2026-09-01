package io.rami.screenrecorder

import android.media.projection.MediaProjectionManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import io.rami.screenrecorder.data.recorder.MediaProjectionTokenHolder
import io.rami.screenrecorder.domain.model.CaptureModeKind
import io.rami.screenrecorder.domain.usecase.ObserveSettingsUseCase
import io.rami.screenrecorder.foreground.TransparentTrampoline
import io.rami.screenrecorder.service.RecordingForegroundService
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 플로팅 버블에서 화면 캡처/녹화를 시작할 때 쓰는 투명 트램폴린 액티비티.
 *
 * MediaProjection 동의는 Activity에서만 받을 수 있고 Android 14+는 세션마다 새로 받아야 한다.
 * 투명 테마이므로 동의 다이얼로그 뒤로 사용자가 보던 화면이 그대로 보이며,
 * 동의가 끝나면 곧바로 스스로 종료한다 (앱을 앞으로 끌어내지 않는다).
 */
@AndroidEntryPoint
class CaptureConsentActivity :
    ComponentActivity(),
    TransparentTrampoline {
    @Inject lateinit var projectionTokenHolder: MediaProjectionTokenHolder

    @Inject lateinit var observeSettings: ObserveSettingsUseCase

    private val consentLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val data = result.data
            if (result.resultCode == RESULT_OK && data != null) {
                projectionTokenHolder.store(result.resultCode, data)
                startForegroundService(serviceIntentForRequestedAction())
            }
            finish()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 동의 결과 콜백에서 재진입하지 않도록 최초 생성에서만 요청한다 (회전 등 재생성 대비).
        if (savedInstanceState != null) return
        lifecycleScope.launch { startRequestedFlow() }
    }

    /**
     * 요청된 동작을 시작한다.
     *
     * 부분 영역 녹화는 영역 선택 오버레이가 필요해 이 투명 액티비티만으로는 끝낼 수 없다.
     * 몰래 전체 화면으로 대체하지 않고 앱을 열어 정상 플로를 태운다 (CLAUDE.md 6절).
     */
    private suspend fun startRequestedFlow() {
        val isScreenshot = intent?.action == ACTION_CONSENT_SCREENSHOT
        if (!isScreenshot && observeSettings().first().selectedCaptureMode == CaptureModeKind.REGION) {
            startActivity(MainActivity.startRecordingIntent(this))
            finish()
            return
        }
        val manager = getSystemService(MediaProjectionManager::class.java)
        consentLauncher.launch(manager.createScreenCaptureIntent())
    }

    private fun serviceIntentForRequestedAction() =
        if (intent?.action == ACTION_CONSENT_SCREENSHOT) {
            RecordingForegroundService.screenshotIntent(this)
        } else {
            RecordingForegroundService.startIntent(this)
        }

    companion object {
        /** 화면 캡처 동의 요청 액션 (manifest intent-filter와 일치해야 한다). */
        const val ACTION_CONSENT_SCREENSHOT = "io.rami.screenrecorder.action.CONSENT_SCREENSHOT"

        /** 화면 녹화 동의 요청 액션. */
        const val ACTION_CONSENT_RECORD = "io.rami.screenrecorder.action.CONSENT_RECORD"
    }
}
