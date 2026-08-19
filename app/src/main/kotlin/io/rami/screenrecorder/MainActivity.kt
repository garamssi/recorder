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
import io.rami.screenrecorder.domain.model.AudioSource
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

    private var pendingAudioSource = AudioSource.INTERNAL
    private var pendingTimeLimitSeconds = 0L

    private val consentLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val data = result.data
            if (result.resultCode == RESULT_OK && data != null) {
                projectionTokenHolder.store(result.resultCode, data)
                startForegroundService(
                    RecordingForegroundService.startIntent(
                        this,
                        pendingAudioSource,
                        pendingTimeLimitSeconds,
                    ),
                )
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
            ScreenRecorderTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    DebugRecordingControls(
                        observeRecordingState = observeRecordingState,
                        actions =
                            DebugActions(
                                onStart = { source ->
                                    requestProjectionConsent(source, timeLimitSeconds = 0L)
                                },
                                onStartWithLimit = {
                                    requestProjectionConsent(AudioSource.SILENT, timeLimitSeconds = 30L)
                                },
                                onPause = { startService(RecordingForegroundService.pauseIntent(this)) },
                                onResume = { startService(RecordingForegroundService.resumeIntent(this)) },
                                onStop = { startService(RecordingForegroundService.stopIntent(this)) },
                            ),
                    )
                }
            }
        }
    }

    private fun requestProjectionConsent(
        audioSource: AudioSource,
        timeLimitSeconds: Long,
    ) {
        pendingAudioSource = audioSource
        pendingTimeLimitSeconds = timeLimitSeconds
        val manager = getSystemService(MediaProjectionManager::class.java)
        consentLauncher.launch(manager.createScreenCaptureIntent())
    }
}

/** 디버그 UI 콜백 묶음 (Stage 6에서 정식 홈 화면으로 대체). */
private class DebugActions(
    val onStart: (AudioSource) -> Unit,
    val onStartWithLimit: () -> Unit,
    val onPause: () -> Unit,
    val onResume: () -> Unit,
    val onStop: () -> Unit,
)

@Composable
private fun DebugRecordingControls(
    observeRecordingState: ObserveRecordingStateUseCase,
    actions: DebugActions,
) {
    val state by observeRecordingState().collectAsState(initial = RecordingState.Idle)
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = "상태: $state", style = MaterialTheme.typography.titleLarge)
        Button(onClick = { actions.onStart(AudioSource.INTERNAL) }) { Text("시작: 내부 오디오") }
        Button(onClick = { actions.onStart(AudioSource.MICROPHONE) }) { Text("시작: 마이크") }
        Button(onClick = { actions.onStart(AudioSource.INTERNAL_AND_MICROPHONE) }) { Text("시작: 믹싱") }
        Button(onClick = { actions.onStart(AudioSource.SILENT) }) { Text("시작: 무음") }
        Button(onClick = actions.onStartWithLimit) { Text("시작: 30초 제한") }
        Button(onClick = actions.onPause) { Text("일시정지") }
        Button(onClick = actions.onResume) { Text("재개") }
        Button(onClick = actions.onStop) { Text("중지") }
    }
}
