# ScreenRecorder 디자인 가이드 (개발용) — "Kinetic"

원본 디자인: `design/new_design/` — Figma Make 프로토타입에서 디자인 실체만 남긴 것이다.
`src/App.tsx`(화면 구성), `src/index.css`(컬러·폰트 토큰), `guidelines/Guidelines.md`(디자인 원칙).
Vite/Figma 빌드 스캐폴딩은 이 저장소에서 쓸 일이 없어 제거했다.
대상: Android 16 태블릿, Jetpack Compose + Material 3. 기능 동작은 `기능명세서.md`가 우선한다.

---

## 0. 미학적 입장과 전제

- **Kinetic**: 모션이 주인공인 다크 UI. 방송 장비/제품 발표 화면처럼 정밀하고 즉각적인 느낌을 목표로 한다.
- **다크 전용**: 라이트 테마와 다이내믹 컬러(Material You)는 쓰지 않는다. 앱 화면 대부분이 어두운 배경 위 영상이고,
  강조 레드가 곧 "녹화 중" 신호이기 때문이다. 설정에도 테마 항목을 두지 않는다.
- **태블릿 우선**: 가로를 기본으로 설계하고, 세로에서는 단일 컬럼으로 재배치한다.

## 1. 컬러 토큰

`core/designsystem/theme/Color.kt`가 단일 진실 공급원이며, `darkColorScheme`에 아래처럼 매핑한다.

| 토큰 | HEX | Material 3 매핑 | 사용처 |
|---|---|---|---|
| background | #09090B | background, surface, surfaceContainerLowest | 화면 배경 |
| card | #18181B | surfaceContainer, surfaceContainerLow | 카드·패널·레일·상단/하단 바 |
| secondary | #27272A | surfaceContainerHigh, surfaceVariant, outlineVariant | 채움 버튼, 섹션 헤더, 구분선 |
| accent | #3F3F46 | surfaceContainerHighest, outline | 썸네일 플레이스홀더, 스위치 트랙(꺼짐) |
| foreground | #FAFAFA | onSurface, onPrimary | 본문 텍스트·아이콘 |
| muted-foreground | #A1A1AA | onSurfaceVariant, secondary | 보조 텍스트, 타임스탬프, 메타데이터 |
| **recRed** | **#EF4444** | primary, error | 녹화 버튼, REC 표시, 선택 강조, 삭제 |
| primaryContainer | #7F1D1D (on: #FECACA) | primaryContainer | 영구 삭제 등 위험 강조 컨테이너 |

### 브랜드/고정 색 (테마 불변)

| 이름 | 값 | 사용처 |
|---|---|---|
| recRed / recRedPressed | #EF4444 / #DC2626 | 녹화 버튼, 앱 아이콘, 영역 선택 외곽선, 플로팅 버블 타이머 |
| splashBackground | #09090B | 스플래시·런치 윈도우 배경 (`values/colors.xml`의 `kinetic_background`와 동일해야 한다) |
| overlayScrim | rgba(0,0,0,.8) | 썸네일 재생시간 뱃지 |
| playerGradient | 상·하단 rgba(0,0,0,.8~.9) → 투명 | 플레이어 컨트롤 배경 |
| bubbleBackground | #EE18181B | 플로팅 컨트롤 버블 |

## 2. 타이포그래피

폰트: **Inter** (400 / 500 / 600 / 700). `core/designsystem/res/font/`에 번들하며 라이선스는 `docs/licenses/Inter-OFL.txt`.
Inter에는 한글 글리프가 없으므로 한글은 Android 폰트 폴백 체인이 시스템 한글 폰트로 대체한다.

| 용도 | 스타일 | 굵기 |
|---|---|---|
| 화면 제목 ("녹화 준비 완료", "보관함") | headlineLarge, 자간 -0.5 | 700 |
| 섹션 카드 헤더, 카드 제목 | titleMedium / headlineSmall | 600 |
| 버튼·칩·뱃지 값 | labelLarge / bodyMedium | 500 |
| 본문·설명 | bodyMedium / bodySmall | 400 |
| 경과 시간(56sp), 카운트다운(120sp) | displayMedium / displayLarge + `tnum` | 700 |

숫자가 매초 바뀌는 곳(타이머, 재생 위치)은 `TextStyle.tabularNumbers()`로 고정폭 숫자를 쓴다.

## 3. 형태 · 간격 · 아이콘

- 모서리: 버튼·칩·입력 8dp(`ControlCorner`), 썸네일·아이콘 타일 12dp(`TileCorner`), 카드 16dp(`CardCorner`), pill = 완전 원형
- 간격 스케일: 4 / 8 / 12 / 16 / 20 / 24 / 32
- 화면 좌우 패딩 32dp(`ScreenPadding`), 본문 최대 폭 1100dp(`ContentMaxWidth`), 설정 본문 840dp — 태블릿 그립 영역 확보
- 최소 터치 타깃 48dp(`MinTouchTarget`)
- 아이콘: `androidx.compose.material.icons` (extended). 16dp(뱃지) / 20dp(원형 버튼) / 28dp(모드 타일)
- 카드는 서피스 + **1dp outlineVariant 외곽선**을 항상 함께 쓴다 (다크 배경에서 면 구분이 색만으로는 약하다)

## 4. 레이아웃과 핵심 컴포넌트

### 셸 (`presentation/navigation/AppShell.kt`)
- **가로**: 좌측 레일 96dp — 브랜드 마크(48dp, primary 12% 배경) + 녹화/보관함 + 하단 설정
- **세로**: 상단 바(브랜드 + 설정) + 하단 내비게이션 80dp(라벨 표시)
- 선택된 내비 항목: primary 채움 + onPrimary 아이콘. 휴지통은 보관함 탭 강조를 유지한다.
- 플레이어는 크롬을 감춘다 (`showChrome = false`) — `content()` 호출 위치는 고정이라 NavHost 상태가 유지된다.

### 플로팅 캡처 버블 (`service/FloatingCaptureBubble.kt`)
앱 안의 FAB가 아니라 **다른 앱 위에 떠 있는 오버레이 창**이다 (기능명세서 11.1절).
Compose를 쓰지 않고 플랫폼 뷰로 만들며, 색·치수는 Kinetic 토큰을 그대로 옮긴다 (`BubbleViews.kt`).

- 접힘: primary 원형 52dp + "+" 아이콘. 드래그로 이동하고 손을 떼면 가까운 좌우 가장자리에 스냅
- 펼침: 위로 "라벨 + 원형 버튼" 4줄 — 화면 녹화(primary) / 화면 캡처 / 음성만 녹음 / 앱으로 가기, 맨 아래 닫기
- **녹화·녹음 중에도 펼칠 수 있다**: pill(REC 점 + 경과 시간 + 일시정지/중지)에서 시간 영역을 탭하면
  위로 "앱으로 가기"가 펼쳐진다. 일시정지·중지는 pill에서 한 번에 누를 수 있게 남긴다.
  화면 캡처·음성 녹음은 세션이 겹쳐 시작할 수 없으므로 진행 중 메뉴에서 뺀다.
- **가장자리에서도 잘리지 않는다** (`BubbleWindowPosition`): 붙어 있던 좌우 변과 "아래쪽 기준선"을 기억해
  펼칠 때 위치를 다시 계산한다. 아래쪽에서는 위로, 위쪽에서는 아래로 펼쳐지고 토글은 제자리에 머문다.
  시스템 바 인셋을 피해 배치한다 — 상태 바에 겹치면 그려져도 터치를 SystemUI가 가져가 눌러지지 않는다.
- 오버레이 특성상 버블 자체가 녹화·캡처 결과물에 찍힌다 (명세에 명시)

앱 화면(홈·보관함)에는 FAB를 두지 않는다. 홈의 대형 녹화 버튼이 앱 내 진입점이다.

### 카운트다운 (`service/CountdownOverlayWindow.kt`)
- 화면 전체 딤 72% + 숫자 120sp(tabular) + "탭하면 바로 시작합니다" 힌트, 어디를 탭해도 스킵
- 앱 내부가 아니라 **시스템 오버레이 창**이다 — 다른 앱 위에서 플로팅 버튼으로 시작해도 보여야 한다
- 인코딩은 카운트다운이 끝난 뒤 시작하므로 녹화 영상에 담기지 않는다 (실기기 첫 프레임으로 확인)

### 저장 오버레이 (`service/SaveOverlayWindow.kt`)
중지 직후부터 발행이 끝날 때까지 화면 위에 뜨는 카드 (기능명세서 6.1절 [결정]).

**홈의 "저장 중"·"저장 완료" 링 게이지를 그대로 옮긴 것이다.** 홈은 앱 안에 있을 때만 보이고
실사용에서 사용자는 다른 앱에 있으므로, 그 디자인이 실제로 쓰이는 자리는 이쪽이다. 오버레이는
Compose 를 쓰지 않으므로 같은 그림을 `SavingGaugeView`(플랫폼 뷰 + Canvas)로 다시 그린다.

- 화면 **상단 중앙**, 상태 바 아래로 24dp. 하단은 제스처 바·버블과 부딪힌다
- **폭은 300dp 고정**이다. 내용에 맡기면 "저장 중…" → "녹화를 저장했습니다" 로 바뀔 때마다
  카드가 옆으로 늘었다 줄었다 하고, 짧은 문구에서는 링이 그 폭으로 눌려 좌우가 잘린다
- 카드색 배경 + 24dp 라운드. 버블 알약(`BUBBLE_SURFACE`, 93%)과 달리 **불투명하게** 선언한다 —
  200dp가 넘는 면이라 조금만 비쳐도 아래 앱의 제목과 본문이 링 위로 올라온다
- 링은 홈과 **같은 값**을 쓴다. 수치는 양쪽이 함께 읽는 `core/common/design/SavingGaugeSpec.kt`
  하나에 있다 — 각자 상수를 들면 한쪽만 고쳐져 같은 국면이 표시면마다 다르게 보인다.
  층 구성과 근거는 아래 "홈 > 저장 중" 항목과 같다
- 후광은 완료 국면에도 계속 맥동한다 (홈과 같다). 멈추면 그 프레임의 임의 밝기로 굳는다
- 진행 원호는 목표로 150ms 옮겨 간다. 진행률이 0.5% 단위로 올라와 그대로 그리면 계단으로 튄다
- 중앙: 경과 시간 40sp(tabular) + 퍼센트. 발행이 확정되면 링이 꽉 차고 중앙이 체크 44dp로 바뀐다
- **실패**: 링을 채우지 않고 역회전만 멈춘다 — 실패한 진행률을 100%로 채우면 저장된 것으로
  읽힌다. 중앙은 그때까지의 길이를 그대로 두고, 상태 줄만 "저장하지 못했습니다" 로 바뀐다
- 상태 줄: REC 점(recRed 50% — 홈의 `animated = false` 와 같은 밝기) + "저장 중…" /
  "녹화를 저장했습니다" / "저장하지 못했습니다", 그 아래 파일명 한 줄(말줄임)
- 체크는 primary(recRed)로 칠하지 않는다. 이 앱에서 recRed 는 "녹화 중" 신호라(0절),
  끝났다는 표시에 쓰면 뜻이 부딪힌다
- **터치를 가로채지 않는다** (`FLAG_NOT_TOUCHABLE`): 카드 아래의 앱이 그대로 눌린다.
  누를 것이 없으므로 터치를 받을 이유도 없다
  - 대가로 **불투명도가 80%로 깎인다.** Android 12+ 는 터치를 받지 않는 오버레이를
    `maximum_obscuring_opacity_for_touch`(기본 0.8)로 제한한다. 배경을 불투명으로 선언해도
    밝은 앱 위에서는 아래 글자가 옅게 비친다 — 카드색을 더 어둡게 해도 넘을 수 없는 천장이다.
    터치를 가로채는 쪽이 더 나쁘므로 감수한다
- 완료로 바뀐 뒤 3초 머물고 스스로 사라진다. 서비스가 먼저 접혀도 남아 있어야 하므로 창의
  수명은 코루틴 스코프가 아니라 메인 스레드 핸들러가 쥔다
- 새 세션이 시작되면 즉시 내린다 — 지난 녹화의 완료가 새 녹화의 첫 프레임에 찍히면 안 된다
- 오버레이 권한이 없으면 완료 시점에 토스트로 대체한다. 진행률은 알림이 이미 퍼센트로 알린다

### 홈 (녹화)
- 헤더 → 캡처 모드 카드 3종 → 녹화 제어 카드 → 현재 설정 카드 → 최근 녹화 + 저장 공간
- **모드 카드(`SelectableTile`)**: 선택 시 primary 외곽선 + 아이콘 타일 primary 채움
- **녹화 버튼**: 링 160dp(primary 30% 외곽선) 안에 primary 원 112dp + 그림자. 저장 공간 부족이면 50% 불투명 + 경고
- **녹화 중**: 맥동하는 REC 점 + 경과 시간 56sp(tabular) + 일시정지/중지 버튼
- **저장 중**: 대기의 링(160dp)을 진행 게이지로 승격시킨 상태 (기능명세서 2.1절 [결정]). 아래 층을 겹친다.
  1. 링 테두리 밖으로 번지는 recRed 후광이 alpha .10↔.28 로 맥동 (`PULSE_MILLIS` 900ms 재사용).
     반지름은 링의 1.35배이고 컬러 스톱으로 **중심을 비운다**(0.62 투명 → 0.78 최대 → 1.0 투명).
     중심을 채우면 가운데 40sp 시간 텍스트의 대비를 깎는다
  2. 트랙 링 — 대기와 같은 자리·값 (160dp, 2dp, recRed 30%)
  3. 역회전 흐린 원호 (recRed 25%, 2dp, 3초/바퀴) — 리먹스가 정체돼도 화면이 죽어 보이지 않게 한다
  4. 진행 원호 (recRed, 4dp, 둥근 끝, 12시에서 시계 방향) — 실제 발행 진행률.
     트랙·역회전·진행 원호는 모두 `RING_WIDTH`/2 만큼 들인 같은 궤도를 쓴다 (대기 `border` 와 같은 반지름)
  5. 중앙: 경과 시간 40sp(tabular) + 퍼센트. 녹화 중 56sp 보다 작은 것이 "이미 끝난 값"을 뜻한다
  6. 상태 줄: 맥동을 멈춘 REC 점 + "저장 중…", 그 아래 파일명 한 줄(말줄임)
- **저장 완료**: 링이 꽉 차고(100%) 역회전 원호가 멈추며 중앙이 체크로 바뀐다.
  `SAVED_DISPLAY_MILLIS`(900ms) 만 머문 뒤 **누를 것 없이 스스로 접혀** 대기로 돌아간다.
  - 판정은 **발행이 확정된 녹화본**(`HomeViewModel.justSaved` ← `completedRecordings`)으로만
    한다. 세션 상태의 `Stopping -> Idle` 전이로 판정하면 발행 실패와 빈 세션도 같은 전이를 만들어
    저장되지 않은 녹화를 "저장했습니다" 로 알린다.
  - 중앙은 체크만 그린다. 길이는 저장 중에 이미 보여 줬고, 완료 순간에 필요한 정보는
    "무엇이 저장됐는가"(파일명)뿐이다. 파일명은 발행 결과에서 읽는다 — 스톱워치가 아니라
    실제 파일이 진실이다.
  - 이 구간에는 녹화 시작 버튼이 없다. 스스로 접히고, 버블·알림에서 새 세션이
    시작되면 즉시 접히므로 다음 녹화를 막지 않는다.
  - 이 카드는 앱 안에 있을 때의 확인이다. 다른 앱 위에서의 통지는 "저장 완료 배너"가 맡는다.
  - 완료 안내 자체는 기존 스낵바가 맡는다 (기능명세서 6.2절)
- **제어 카드 높이**: 대기·녹화 중·저장 중이 같은 최소 높이를 쓴다. 상태 전환에 레이아웃이 튀면 안 된다
- **현재 설정**: 해상도·프레임레이트·비트레이트·오디오·시간 제한 뱃지 (가로 3열 / 세로 2열)

### 보관함
- 헤더(제목 + 부제) 우측에 선택/휴지통, 선택 모드에서는 선택 수 + 전체 선택 + 공유 + 삭제 + 닫기
- 툴바: 검색 필드 + 정렬 메뉴 + 리스트/그리드 토글
- **선택 모드**: 선택되지 않은 항목은 50% 불투명(`UNSELECTED_ALPHA`), 선택 항목은 primary 외곽선 + 체크 아이콘
- 그리드 카드: 16:9 썸네일(Crop) + 재생시간 뱃지 + 제목 + 날짜/용량. 열 수는 폭 반응형 2~4열

### 휴지통
- 썸네일 **채도 0** (`ColorMatrix.setToSaturation(0f)`), 제목 취소선, 남은 보관일은 primary 색
- 선택하면 불투명도 100% + primary 외곽선. 선택 시 복원 / 영구 삭제, 미선택 시 전체 비우기

### 플레이어 (`presentation/player/`)
- 전체 화면 검정 배경 + ExoPlayer TextureView 서피스. **내장 컨트롤러는 끄고** Compose로 직접 그린다.
- 상단: 그라디언트 위 뒤로가기 / 제목 / 더보기 (반투명 흰 15% 원형 버튼)
- 중앙: -10초(64dp) · 재생·일시정지(primary 96dp) · +10초(64dp)
- 하단 첫 줄: 위치 · 시크바 · 길이
- 하단 둘째 줄: 왼쪽에 볼륨 pill(음소거 버튼 + 140dp 슬라이더, 흰 15% 배경), 오른쪽에 배속 pill + 화면 채우기 토글 (둘 사이 24dp 간격)
- 볼륨 pill은 시스템 미디어 볼륨을 가리킨다. 음소거이거나 0이면 아이콘이 `VolumeOff`로 바뀐다
- 재생이 끝나면 중앙 버튼이 "처음부터 재생" 아이콘으로 바뀐다
- **자동 숨김**: 재생 중 3초 무조작이면 250ms 페이드 아웃, 화면 탭으로 즉시 복귀

### 설정
- 단일 스크롤 컬럼의 **그룹 카드**(`SectionCard`): 헤더(secondary 배경) + 구분선으로 나뉜 행
- 행 형태: 값 + chevron(드롭다운 선택) / iOS 스타일 토글(`KineticSwitch`) / 슬라이더 / 읽기 전용 값

## 5. 인터랙션과 모션

- **눌림**: 모든 버튼·카드에 `rememberPressScale()` (0.95배 축소). 리플 대신 스케일을 쓴다.
- **선택 강조**: primary 외곽선으로 전환 (`animateColorAsState`)
- **토글**: 트랙 색 전환 + 노브 슬라이드
- 지속 시간: 상태 전환 200ms(`KINETIC_MOTION_MILLIS`), 페이드 150ms(`KINETIC_FADE_MILLIS`), 플레이어 컨트롤 250ms
- 화면 방향은 `isLandscape()`(폭 ≥ 높이)로 판단해 레이아웃을 바꾼다.

## 5.1 앱 아이콘

`design/new_design/`에는 아이콘 리소스 파일이 없다. 디자인의 브랜드 마크는 `App.tsx`가 lucide `CircleDot`을
primary 레드로 그리는 것뿐이므로, 런처 아이콘도 같은 상징(링 + 중앙 REC 점)을 쓴다.

- 배경: Kinetic 다크 대각선 그라데이션 (#23232A → #09090B)
- 전경: recRed 링(지름 61dp, 두께 7) + 중앙 점(지름 22dp) — 어댑티브 안전 영역(66dp) 안에 배치
- 모노크롬: 같은 형태의 단색 실루엣 (Android 13+ 테마 아이콘)
- 앱 셸 좌측 레일의 브랜드 마크와 홈의 녹화 버튼이 같은 상징을 공유한다

## 6. 코드 위치

| 대상 | 경로 |
|---|---|
| 컬러/타이포/셰이프 | `core/designsystem/theme/` |
| 공용 컴포넌트 | `core/designsystem/component/` (Surfaces, Controls, SpeedDial, Interaction) |
| 셸/내비게이션 | `presentation/navigation/` |
| 화면 | `presentation/{home,library,trash,settings,player}/` |
| 플로팅 버블 | `service/FloatingCaptureBubble.kt`, `BubbleViews.kt`, `BubblePills.kt`, `BubbleDragHandler.kt` |
| 시스템 오버레이 | `presentation/overlay/RegionSelectorView.kt` |
| 런치 윈도우 배경 | `app/res/values/themes.xml` + `colors.xml` |
| 런처 아이콘 | `app/res/drawable/ic_launcher_{background,foreground,monochrome}.xml` |
