# ScreenRecorder 디자인 가이드 (개발용)

목업 파일: `ScreenRecorder 목업.dc.html` — 프레임 뱃지(1a~1s, 2a~2e)로 상호 참조.
대상: Android 16 태블릿, Jetpack Compose + Material 3. 기능 동작은 `spec.md`(기능명세서)가 우선.

---

## 1. 컬러 토큰

Material 3 ColorScheme에 그대로 매핑한다. 다이내믹 컬러(Material You) 켬이 기본이며, 아래 값은 **다이내믹 컬러 꺼짐/미지원 시 기본 팔레트**다.

### 라이트 (lightColorScheme)
| 역할 | HEX | 사용처 |
|---|---|---|
| primary | #0B57D0 | 확인 버튼, 링크, 선택 값, 진행바 |
| onPrimary | #FFFFFF | primary 위 텍스트/아이콘 |
| primaryContainer | #D8E2FF | (예비) 강조 컨테이너 |
| secondaryContainer | #DBE2F9 | 세그먼트 선택, 선택 칩, 설정 선택 항목 |
| onSecondaryContainer | #141B2C | 위 요소의 텍스트 |
| surface | #FAF9FE | 화면 배경 (v1) |
| surfaceContainer | #EEEDF4 | 검색바, 프리셋 카드, 채움 컨테이너 |
| surfaceContainerHigh | #E8E7EF | 트랙, 스위치 트랙 |
| surfaceContainerLow | #F3F3F9 | 다이얼로그/시트 배경 |
| onSurface | #1A1B20 | 본문 텍스트 |
| onSurfaceVariant | #44464F | 보조 텍스트, 아이콘 |
| outline | #757780 | 버튼/세그먼트 외곽선 |
| outlineVariant | #C5C6D0 | 구분선, 칩 외곽선 |
| error | #BA1A1A | 삭제, 경고 |
| errorContainer | #FCEEEE (텍스트 #8C1D18) | 녹화 중 카드, 보관일 뱃지 |

### 다크 (darkColorScheme)
| 역할 | HEX |
|---|---|
| primary | #AFC6FF |
| secondaryContainer | #3F4759 (on: #DBE2F9) |
| surface | #121318 (v2 홈: #101116) |
| surfaceContainer | #1E1F25 |
| surfaceContainerHigh | #292A31 |
| 카드 서피스(v2) | #1B1C22 |
| onSurface | #E3E2E9 |
| onSurfaceVariant | #C5C6D0 |
| outline | #8F9099 |
| outlineVariant | #44464F |

### 브랜드/고정 색 (테마 불변)
| 이름 | HEX | 사용처 |
|---|---|---|
| recRed | #D93025 (hover #C5221F) | 녹화 버튼, REC 표시, 중지 버튼, 앱 아이콘 |
| heroGradient (라이트) | 150deg: #0B57D0 → #083E96(55%) → #062D6E | 홈 히어로 (2a) |
| heroGradient (다크) | 150deg: #123A85 → #0C2B66(55%) → #081D47 | 홈 히어로 (2d) |
| splashBg | #05070F | 스플래시 배경 (2e) — 일러스트 원본 배경색과 동일 |
| bubbleBg | rgba(20,21,26,.92~.94) + blur 8~12 | 플로팅 버블/펼침 메뉴 (2b, 2c) |
| scrim | rgba(26,27,32,.42) | 다이얼로그/시트 뒤 딤 |
| thumbDurationBadge | rgba(0,0,0,.7), 텍스트 #FFF | 썸네일 재생시간 뱃지 |
| gold | #FFC400 / #FFD54F | 스플래시 장식 |

## 2. 타이포그래피

폰트: **Noto Sans KR** (400 / 500 / 700). 숫자 타이머는 `font-variant-numeric: tabular-nums` (Compose: `FontFeature "tnum"`).

| 스타일 | 크기/굵기 | 사용처 |
|---|---|---|
| displaySmall | 56px / 700 | 녹화 중 경과 시간 (1e) |
| headlineMedium | 32px / 700, 자간 -0.3 | 스플래시 앱명 |
| titleLarge | 22px / 700 | 앱바 타이틀, 다이얼로그 제목 |
| titleMedium | 20px / 700 | 시트 제목 |
| bodyLarge | 16px / 500 | 리스트 항목 제목, 프리셋 요약 |
| bodyMedium | 14~15px / 400·500 | 버튼, 칩, 본문 |
| bodySmall | 13px / 400 | 보조 정보 (날짜·해상도·용량) |
| labelSmall | 11~12px / 500 | 뱃지, 캡션, 도움말 |

행간: 제목 1.2, 본문 1.5. 태그라인/보조 텍스트 자간 +0.3.

## 3. 형태 · 간격 · 아이콘

- 모서리: 버튼/칩 8px, 카드 16~20px, 히어로·큰 카드 28~32px, 시트 상단 28px, 다이얼로그 28px, 완전 원형(pill) 버튼 radius=height/2
- 기본 간격 스케일: 4 / 8 / 12 / 16 / 20 / 24 / 32
- 화면 좌우 패딩: 32px (가로), 24px (세로)
- 최소 터치 타깃: 48×48 (스펙상 44 이상)
- 아이콘: **Material Symbols Outlined**, 16~26px. 채움이 필요한 곳(stop, play)은 FILL 1
- 앱바 높이 64px, 상태바 34px, 아이콘 버튼 48×48 원형

## 4. 핵심 컴포넌트 스펙

- **녹화 시작 버튼 (2a)**: 흰 원 132px + 내부 빨간 원 52px, 그림자 `0 12px 40px rgba(6,45,110,.5)`. 히어로 안 동심원 링 250/360/480px, 흰색 8~20% 외곽선
- **모드 세그먼트 (2a)**: pill 컨테이너 rgba(255,255,255,.12), 선택 = 흰 배경 + 히어로색 텍스트 700
- **프리셋 칩 (2a)**: rgba(255,255,255,.14), 13px 500, 아이콘 tune + chevron_right
- **리스트 행 (1f)**: 썸네일 168×94 r10 + 제목 16px/500 + 보조 13px + more_vert. 행 hover surfaceContainer
- **그리드 카드 (1h)**: 16:9 r12, 선택 시 outline 3px primary + 체크 원 24px
- **플로팅 버블 접힘 (2b)**: 다크 pill, REC 원 40px(빨강, ring rgba(217,48,37,.25) 4px), 시간 16px/700. 드래그 이동, 가장자리 스냅
- **플로팅 버블 펼침 (2c)**: 280px 카드 r28, 헤더(REC+시간/포맷) + 일시정지·중지 2버튼(76px, 중지=recRed) + 앱으로 가기 / 설정 / 접기 리스트
- **다이얼로그 (1l~1m)**: 폭 440~480, r28, surfaceContainerLow, 제목 22px/500, 우하단 텍스트 버튼(취소=primary 텍스트, 확인=primary 채움 pill)
- **스낵바 (1o)**: #1A1B20 배경, r8, 액션 텍스트 #A8C7FA

## 5. 화면별 참조

| 화면 | 프레임 | 비고 |
|---|---|---|
| 스플래시 | 2e | 배경 #05070F, 일러스트 `uploads/KakaoTalk_Photo_2026-08-19-23-17-48.png` (리소스로 복사), 로딩 도트 3개 |
| 홈 (확정안) | 2a (라이트) / 2d (다크) | 히어로+우측 최근 녹화/통계. 1a는 구버전 |
| 녹화 옵션 시트 | 1b | 칩 선택형, 확인 버튼 |
| 카운트다운 | 1c | 딤 72%, 숫자 120px, 탭=스킵 |
| 영역 선택 | 1d | 대시 외곽선 #A8C7FA, 핸들 14px, 크기 뱃지 |
| 녹화 중 상태/알림 | 1e | 카드 errorContainer, 타이머 tabular |
| 플로팅 버블 | 2b, 2c | 백그라운드 녹화 컨트롤 |
| 목록 리스트/그리드·다중선택 | 1f, 1h, 1g | 더보기 메뉴 6액션 |
| 플레이어 | 1i | 상하 그라디언트 오버레이, 배속 pill |
| 휴지통 | 1j | 보관일 뱃지, 복원/영구삭제 |
| 설정 | 1k | 태블릿 2-페인, 좌측 내비 선택=secondaryContainer pill |
| 이름변경/압축/상세정보/저장직후 | 1l, 1m, 1n, 1o | |
| 세로 방향 | 1r, 1s | 단일 컬럼 스택 규칙 |

## 6. Compose 적용 요약

```kotlin
val LightColors = lightColorScheme(
  primary = Color(0xFF0B57D0), onPrimary = Color.White,
  secondaryContainer = Color(0xFFDBE2F9), onSecondaryContainer = Color(0xFF141B2C),
  surface = Color(0xFFFAF9FE), surfaceContainer = Color(0xFFEEEDF4),
  surfaceContainerLow = Color(0xFFF3F3F9), surfaceContainerHigh = Color(0xFFE8E7EF),
  onSurface = Color(0xFF1A1B20), onSurfaceVariant = Color(0xFF44464F),
  outline = Color(0xFF757780), outlineVariant = Color(0xFFC5C6D0),
  error = Color(0xFFBA1A1A),
)
val DarkColors = darkColorScheme(
  primary = Color(0xFFAFC6FF),
  secondaryContainer = Color(0xFF3F4759), onSecondaryContainer = Color(0xFFDBE2F9),
  surface = Color(0xFF121318), surfaceContainer = Color(0xFF1E1F25),
  surfaceContainerHigh = Color(0xFF292A31),
  onSurface = Color(0xFFE3E2E9), onSurfaceVariant = Color(0xFFC5C6D0),
  outline = Color(0xFF8F9099), outlineVariant = Color(0xFF44464F),
)
val RecRed = Color(0xFFD93025) // 테마 불변
```

- 다이내믹 컬러: `dynamicLightColorScheme(context)` 사용 가능 시 우선, RecRed·splashBg·heroGradient는 고정
- 테마 전환: 설정값(시스템/라이트/다크) → `isSystemInDarkTheme()` 조합
- 아이콘: Material Symbols Outlined 폰트 또는 `androidx.compose.material.icons` 대응 아이콘
- 스플래시: `androidx.core.splashscreen` + 일러스트는 windowSplashScreenBackground=#05070F
