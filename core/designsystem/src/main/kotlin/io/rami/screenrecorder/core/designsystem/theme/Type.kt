package io.rami.screenrecorder.core.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import io.rami.screenrecorder.core.designsystem.R

/**
 * Inter 폰트 패밀리 (DESIGN_GUIDE.md 2절).
 *
 * Inter에는 한글 글리프가 없다. Android의 폰트 폴백 체인이 없는 글자를 시스템 한글 폰트로
 * 대체하므로 한국어 UI도 그대로 렌더링된다 — 한글 웨이트는 시스템 폰트를 따른다.
 */
val InterFontFamily =
    FontFamily(
        Font(R.font.inter_regular, FontWeight.Normal),
        Font(R.font.inter_medium, FontWeight.Medium),
        Font(R.font.inter_semibold, FontWeight.SemiBold),
        Font(R.font.inter_bold, FontWeight.Bold),
    )

/** 숫자 폭을 고정하는 OpenType 기능. 타이머·시간 표시가 흔들리지 않게 한다. */
const val TABULAR_NUMBERS = "tnum"

/** 경과 시간·재생 위치처럼 매초 바뀌는 숫자에 쓰는 고정폭 숫자 스타일 보정. */
fun TextStyle.tabularNumbers(): TextStyle = copy(fontFeatureSettings = TABULAR_NUMBERS)

/** 큰 제목은 자간을 좁혀 "정밀한 장비" 느낌을 준다 (DESIGN_GUIDE.md 2절). */
private val TIGHT_TRACKING = (-0.5).sp

/**
 * Kinetic 타이포그래피 (DESIGN_GUIDE.md 2절).
 *
 * 본문 400, 인터랙티브 요소 500, 섹션 헤더 600, 대형 헤드라인 700을 쓴다.
 */
internal val KineticTypography =
    Typography().run {
        Typography(
            displayLarge = displayLarge.kinetic(FontWeight.Bold),
            displayMedium = displayMedium.kinetic(FontWeight.Bold),
            displaySmall = displaySmall.kinetic(FontWeight.Bold),
            headlineLarge = headlineLarge.kinetic(FontWeight.Bold, letterSpacing = TIGHT_TRACKING),
            headlineMedium = headlineMedium.kinetic(FontWeight.Bold, letterSpacing = TIGHT_TRACKING),
            headlineSmall = headlineSmall.kinetic(FontWeight.SemiBold),
            titleLarge = titleLarge.kinetic(FontWeight.SemiBold),
            titleMedium = titleMedium.kinetic(FontWeight.SemiBold),
            titleSmall = titleSmall.kinetic(FontWeight.Medium),
            bodyLarge = bodyLarge.kinetic(FontWeight.Normal),
            bodyMedium = bodyMedium.kinetic(FontWeight.Normal),
            bodySmall = bodySmall.kinetic(FontWeight.Normal),
            labelLarge = labelLarge.kinetic(FontWeight.Medium),
            labelMedium = labelMedium.kinetic(FontWeight.Medium),
            labelSmall = labelSmall.kinetic(FontWeight.Medium),
        )
    }

private fun TextStyle.kinetic(
    weight: FontWeight,
    letterSpacing: androidx.compose.ui.unit.TextUnit = this.letterSpacing,
): TextStyle =
    copy(
        fontFamily = InterFontFamily,
        fontWeight = weight,
        letterSpacing = letterSpacing,
    )
