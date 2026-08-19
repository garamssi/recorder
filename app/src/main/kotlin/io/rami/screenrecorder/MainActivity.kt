package io.rami.screenrecorder

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import dagger.hilt.android.AndroidEntryPoint
import io.rami.screenrecorder.core.designsystem.theme.ScreenRecorderTheme

/** 앱의 단일 진입점 Activity. 네비게이션 호스트는 Stage 6에서 구성한다. */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ScreenRecorderTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    Text(
                        text = getString(R.string.app_name),
                        style = MaterialTheme.typography.titleLarge,
                    )
                }
            }
        }
    }
}
