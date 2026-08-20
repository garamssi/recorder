package io.rami.screenrecorder.core.designsystem.theme

import androidx.compose.ui.graphics.Color

// DESIGN_GUIDE.md 1절 "Kinetic" 컬러 토큰.
// 이 앱은 다크 전용이다 (DESIGN_GUIDE.md 0절) — 라이트 팔레트와 다이내믹 컬러는 쓰지 않는다.

/** 화면 배경. 눈부심을 줄이는 거의 검정에 가까운 색. */
internal val KineticBackground = Color(0xFF09090B)

/** 카드/패널 서피스. 배경보다 한 단계 들린 어두운 회색. */
internal val KineticCard = Color(0xFF18181B)

/** 카드보다 한 단계 더 들린 서피스 (검색바, 세그먼트 컨테이너, 채움 버튼). */
internal val KineticSecondary = Color(0xFF27272A)

/** 가장 높은 단계의 서피스 (썸네일 플레이스홀더, 호버 배경). */
internal val KineticAccent = Color(0xFF3F3F46)

/** 본문 텍스트/아이콘 (오프 화이트). */
internal val KineticForeground = Color(0xFFFAFAFA)

/** 보조 텍스트 (타임스탬프, 메타데이터). */
internal val KineticMutedForeground = Color(0xFFA1A1AA)

/** 구분선/카드 외곽선. */
internal val KineticBorder = Color(0xFF27272A)

/** 눌린 상태 등 강조 레드의 컨테이너 색. */
internal val KineticPrimaryContainer = Color(0xFF7F1D1D)

/** [KineticPrimaryContainer] 위의 텍스트. */
internal val KineticOnPrimaryContainer = Color(0xFFFECACA)

// 브랜드/고정 색 (DESIGN_GUIDE.md "브랜드/고정 색")

/** 녹화 버튼, REC 표시, 중지 버튼, 선택 강조에 쓰는 Kinetic 레드. 앱 전역에서 고정이다. */
val RecRed = Color(0xFFEF4444)

/** [RecRed]의 눌림(pressed) 상태 색. */
val RecRedPressed = Color(0xFFDC2626)

/** 스플래시/로딩 배경색. 화면 배경과 동일해 전환 시 깜빡임이 없다. */
val SplashBackground = KineticBackground

/** 썸네일 재생시간 뱃지 등 밝은 프레임 위에 얹는 반투명 검정 스크림. */
val OverlayScrim = Color(0xCC000000)

/** 플레이어 상·하단 컨트롤 그라디언트 (위→아래: 불투명 검정 → 투명). */
val PlayerGradient = listOf(Color(0xCC000000), Color.Transparent)
