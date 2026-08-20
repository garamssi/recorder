package io.rami.screenrecorder.core.designsystem.theme

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

private val KineticColors =
    darkColorScheme(
        primary = RecRed,
        onPrimary = KineticForeground,
        primaryContainer = KineticPrimaryContainer,
        onPrimaryContainer = KineticOnPrimaryContainer,
        secondary = KineticMutedForeground,
        onSecondary = KineticBackground,
        secondaryContainer = KineticSecondary,
        onSecondaryContainer = KineticForeground,
        tertiary = KineticMutedForeground,
        onTertiary = KineticBackground,
        background = KineticBackground,
        onBackground = KineticForeground,
        surface = KineticBackground,
        onSurface = KineticForeground,
        surfaceVariant = KineticSecondary,
        onSurfaceVariant = KineticMutedForeground,
        surfaceContainerLowest = KineticBackground,
        surfaceContainerLow = KineticCard,
        surfaceContainer = KineticCard,
        surfaceContainerHigh = KineticSecondary,
        surfaceContainerHighest = KineticAccent,
        inverseSurface = KineticForeground,
        inverseOnSurface = KineticCard,
        outline = KineticAccent,
        outlineVariant = KineticBorder,
        error = RecRed,
        onError = KineticForeground,
        errorContainer = KineticPrimaryContainer,
        onErrorContainer = KineticOnPrimaryContainer,
        scrim = Color.Black,
    )

/**
 * 앱 전역 Material 3 테마 — "Kinetic" 다크 전용 (DESIGN_GUIDE.md 0절).
 *
 * 라이트 테마와 다이내믹 컬러(Material You)는 쓰지 않는다. 이 앱의 화면 대부분이
 * 녹화 대상 화면·영상 위에 얹히는 어두운 UI이며, 강조 레드가 곧 녹화 상태 신호이기 때문이다.
 *
 * 내용을 [Surface]로 감싸 `LocalContentColor`를 onSurface로 내려보낸다. MaterialTheme 자체는
 * 콘텐츠 색을 제공하지 않아 이게 없으면 색을 명시하지 않은 Text가 기본값인 검정으로 그려진다.
 */
@Composable
fun ScreenRecorderTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = KineticColors,
        typography = KineticTypography,
        shapes = KineticShapes,
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = KineticColors.background,
            contentColor = KineticColors.onBackground,
            content = content,
        )
    }
}
