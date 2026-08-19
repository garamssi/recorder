package io.rami.screenrecorder

import android.Manifest
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import dagger.hilt.android.AndroidEntryPoint
import io.rami.screenrecorder.core.designsystem.theme.ScreenRecorderTheme
import io.rami.screenrecorder.data.recorder.MediaProjectionTokenHolder
import io.rami.screenrecorder.domain.model.RecordingState
import io.rami.screenrecorder.domain.usecase.ObserveRecordingStateUseCase
import io.rami.screenrecorder.service.RecordingForegroundService
import javax.inject.Inject

/**
 * 앱의 단일 진입점 Activity.
 *
 * 현재 내용은 Stage 3 파이프라인 실기기 검증용 임시 UI이며,
 * Stage 6에서 기능명세서 2절의 홈 화면으로 대체된다.
 *
 * TODO(Stage 6): data 계층 [MediaProjectionTokenHolder] 직접 참조는 기술 부채다.
 *  홈 화면 구현 시 동의 플로를 presentation 쪽 추상화(예: ProjectionConsentLauncher) 뒤로 옮긴다.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var projectionTokenHolder: MediaProjectionTokenHolder

    @Inject lateinit var observeRecordingState: ObserveRecordingStateUseCase

    private val consentLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val data = result.data
            if (result.resultCode == RESULT_OK && data != null) {
                projectionTokenHolder.store(result.resultCode, data)
                startForegroundService(RecordingForegroundService.startIntent(this))
            }
        }

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        setContent {
            ScreenRecorderTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    DebugRecordingControls(
                        observeRecordingState = observeRecordingState,
                        onStartClick = ::requestProjectionConsent,
                        onStopClick = {
                            startService(RecordingForegroundService.stopIntent(this))
                        },
                    )
                }
            }
        }
    }

    private fun requestProjectionConsent() {
        val manager = getSystemService(MediaProjectionManager::class.java)
        consentLauncher.launch(manager.createScreenCaptureIntent())
    }
}

@Composable
private fun DebugRecordingControls(
    observeRecordingState: ObserveRecordingStateUseCase,
    onStartClick: () -> Unit,
    onStopClick: () -> Unit,
) {
    val state by observeRecordingState().collectAsState(initial = RecordingState.Idle)
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = "상태: $state", style = MaterialTheme.typography.titleLarge)
        Button(onClick = onStartClick) { Text("녹화 시작 (디버그)") }
        Button(onClick = onStopClick) { Text("중지") }
    }
}
