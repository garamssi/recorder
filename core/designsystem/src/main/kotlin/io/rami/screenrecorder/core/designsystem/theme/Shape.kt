package io.rami.screenrecorder.core.designsystem.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Kinetic 모서리 스케일 (DESIGN_GUIDE.md 3절).
 *
 * Tailwind 기준 rounded-sm/md/lg/xl/2xl에 대응한다.
 */
internal val KineticShapes =
    Shapes(
        extraSmall = RoundedCornerShape(4.dp),
        small = RoundedCornerShape(8.dp),
        medium = RoundedCornerShape(12.dp),
        large = RoundedCornerShape(16.dp),
        extraLarge = RoundedCornerShape(24.dp),
    )

/** 카드/패널 기본 모서리 (rounded-2xl). */
val CardCorner = RoundedCornerShape(16.dp)

/** 버튼·칩·입력 필드 모서리 (rounded-lg). */
val ControlCorner = RoundedCornerShape(8.dp)

/** 썸네일·아이콘 타일 모서리 (rounded-xl). */
val TileCorner = RoundedCornerShape(12.dp)
