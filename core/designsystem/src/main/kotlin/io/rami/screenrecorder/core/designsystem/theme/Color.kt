package io.rami.screenrecorder.core.designsystem.theme

import androidx.compose.ui.graphics.Color

// DESIGN_GUIDE.md 1절 컬러 토큰. 다이내믹 컬러 꺼짐/미지원 시의 기본 팔레트다.

// 라이트
internal val LightPrimary = Color(0xFF0B57D0)
internal val LightOnPrimary = Color.White
internal val LightPrimaryContainer = Color(0xFFD8E2FF)
internal val LightSecondaryContainer = Color(0xFFDBE2F9)
internal val LightOnSecondaryContainer = Color(0xFF141B2C)
internal val LightSurface = Color(0xFFFAF9FE)
internal val LightSurfaceContainer = Color(0xFFEEEDF4)
internal val LightSurfaceContainerLow = Color(0xFFF3F3F9)
internal val LightSurfaceContainerHigh = Color(0xFFE8E7EF)
internal val LightOnSurface = Color(0xFF1A1B20)
internal val LightOnSurfaceVariant = Color(0xFF44464F)
internal val LightOutline = Color(0xFF757780)
internal val LightOutlineVariant = Color(0xFFC5C6D0)
internal val LightError = Color(0xFFBA1A1A)
internal val LightErrorContainer = Color(0xFFFCEEEE)
internal val LightOnErrorContainer = Color(0xFF8C1D18)

// 다크
internal val DarkPrimary = Color(0xFFAFC6FF)
internal val DarkSecondaryContainer = Color(0xFF3F4759)
internal val DarkOnSecondaryContainer = Color(0xFFDBE2F9)
internal val DarkSurface = Color(0xFF121318)
internal val DarkSurfaceContainer = Color(0xFF1E1F25)
internal val DarkSurfaceContainerHigh = Color(0xFF292A31)
internal val DarkOnSurface = Color(0xFFE3E2E9)
internal val DarkOnSurfaceVariant = Color(0xFFC5C6D0)
internal val DarkOutline = Color(0xFF8F9099)
internal val DarkOutlineVariant = Color(0xFF44464F)

// 브랜드/고정 색 (테마 불변, DESIGN_GUIDE.md "브랜드/고정 색")

/** 녹화 버튼, REC 표시, 중지 버튼에 쓰는 브랜드 레드. 테마와 무관하게 고정이다. */
val RecRed = Color(0xFFD93025)

/** [RecRed]의 눌림(hover/pressed) 상태 색. */
val RecRedPressed = Color(0xFFC5221F)

/** 스플래시 배경색. 일러스트 원본 배경색과 동일해야 한다. */
val SplashBackground = Color(0xFF05070F)
