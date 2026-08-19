package io.rami.screenrecorder.core.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val LightColors =
    lightColorScheme(
        primary = LightPrimary,
        onPrimary = LightOnPrimary,
        primaryContainer = LightPrimaryContainer,
        secondaryContainer = LightSecondaryContainer,
        onSecondaryContainer = LightOnSecondaryContainer,
        surface = LightSurface,
        surfaceContainer = LightSurfaceContainer,
        surfaceContainerLow = LightSurfaceContainerLow,
        surfaceContainerHigh = LightSurfaceContainerHigh,
        onSurface = LightOnSurface,
        onSurfaceVariant = LightOnSurfaceVariant,
        outline = LightOutline,
        outlineVariant = LightOutlineVariant,
        error = LightError,
        errorContainer = LightErrorContainer,
        onErrorContainer = LightOnErrorContainer,
    )

private val DarkColors =
    darkColorScheme(
        primary = DarkPrimary,
        secondaryContainer = DarkSecondaryContainer,
        onSecondaryContainer = DarkOnSecondaryContainer,
        surface = DarkSurface,
        surfaceContainer = DarkSurfaceContainer,
        surfaceContainerHigh = DarkSurfaceContainerHigh,
        onSurface = DarkOnSurface,
        onSurfaceVariant = DarkOnSurfaceVariant,
        outline = DarkOutline,
        outlineVariant = DarkOutlineVariant,
    )

/**
 * 앱 전역 Material 3 테마.
 *
 * DESIGN_GUIDE.md 규칙:
 * - 다이내믹 컬러(Material You)가 기본이며, 끄면 DESIGN_GUIDE 1절의 고정 팔레트를 쓴다.
 * - [RecRed], [SplashBackground] 등 브랜드 색은 다이내믹 컬러와 무관하게 고정이다.
 *
 * @param darkTheme 다크 테마 사용 여부. 설정 화면의 테마 값(시스템/라이트/다크)과 조합해 전달한다.
 * @param dynamicColor 다이내믹 컬러 사용 여부 (설정 > 화면 > 다이내믹 컬러).
 */
@Composable
fun ScreenRecorderTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colorScheme =
        when {
            dynamicColor && darkTheme -> dynamicDarkColorScheme(LocalContext.current)
            dynamicColor -> dynamicLightColorScheme(LocalContext.current)
            darkTheme -> DarkColors
            else -> LightColors
        }
    MaterialTheme(
        colorScheme = colorScheme,
        content = content,
    )
}
