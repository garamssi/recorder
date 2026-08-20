package io.rami.screenrecorder.core.designsystem.component

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.rami.screenrecorder.core.designsystem.theme.ControlCorner

/**
 * Kinetic 스낵바 호스트.
 *
 * Material 3 기본 스낵바는 `inverseSurface`(밝은 배경 + 어두운 글자)라 다크 전용 UI에서
 * 흰 상자처럼 튄다. 카드 서피스 + 강조 레드 액션으로 맞춘다 (DESIGN_GUIDE.md 1절).
 */
@Composable
fun KineticSnackbarHost(
    hostState: SnackbarHostState,
    modifier: Modifier = Modifier,
) {
    SnackbarHost(hostState = hostState, modifier = modifier) { data ->
        Snackbar(
            snackbarData = data,
            shape = ControlCorner,
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onSurface,
            actionColor = MaterialTheme.colorScheme.primary,
        )
    }
}
