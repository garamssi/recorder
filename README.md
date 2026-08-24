# ScreenRecorder

Android 16(API 36) 태블릿용 화면 녹화 앱. FHD 60fps 녹화, 백그라운드 녹화, 내부/마이크 오디오,
부분 영역 크롭, 압축, 내장 플레이어를 지원한다.

오프라인 전용 앱이다. INTERNET 권한을 선언하지 않으며 녹화물은 기기를 벗어나지 않는다.

## 기능

- 녹화 모드: 전체 화면 / 단일 앱 / 부분 영역(GPU 크롭)
- 품질: 기기 최대 / 1080p / 720p, 60·30fps, 자동·고정 비트레이트, H.264·HEVC
- 오디오: 내부 재생음 + 마이크(개별/동시), 볼륨 게인, 마이크 장치 선택(블루투스 SCO 포함)
- 백그라운드 녹화: Foreground Service, 알림에서 일시정지·재개·중지
- 자동 안전 중지: 타이머, 저장 공간 부족, 일시정지 30분 초과
- 크래시 복구: 저장되지 못한 녹화를 다음 실행에서 복구 또는 삭제
- 라이브러리: 리스트/그리드, 정렬·검색·다중 선택, 이름 변경, 공유
- 휴지통: 삭제 시 30일 보관, 복원·영구 삭제
- 압축: 고효율(HEVC)/표준/최대 프리셋, 원본 보존, 백그라운드 진행
- 플레이어: 배속, ±10초 점프, 화면 채우기, 볼륨·음소거(시스템 미디어 볼륨 연동)
- 화면 캡처: PNG 한 장 저장
- 음성 전용 녹음: 마이크만 m4a로 저장
- 설정: 앱별 언어(한국어/English/시스템). 테마는 다크 고정

## 아키텍처

Clean Architecture + MVVM. 의존 방향은 Gradle 모듈로 강제한다.

```
app               DI 조립, MainActivity, 동의·권한, NavHost
core/common       순수 JVM 유틸
core/designsystem Compose 테마·공용 컴포넌트
domain            모델 / repository 인터페이스 / UseCase (Android 의존 없음)
data              repository 구현 + 플랫폼 어댑터
presentation      Compose UI + ViewModel
service           RecordingForegroundService, 알림
```

`presentation -> domain <- data` 이며 data와 presentation은 서로 참조하지 않는다.
플랫폼 API는 전부 data 계층의 인터페이스 뒤로 격리해 JVM 페이크로 단위 테스트한다.

설계 결정 기록: [docs/adr/](docs/adr/)

## 기술 스택

Kotlin, Jetpack Compose + Material 3, Hilt, Coroutines/Flow,
MediaProjection + VirtualDisplay, MediaCodec + Media3 FragmentedMp4Muxer,
AudioRecord + AudioPlaybackCapture, MediaStore, Media3 Transformer + WorkManager, ExoPlayer, DataStore.

minSdk 34 / targetSdk 36 / compileSdk 37.

빌드 JDK와 바이트코드 타깃은 별개다.

- Gradle 실행 및 컴파일 JDK: 설치된 것 중 가장 새 버전을 쓴다(현재 Temurin 25 LTS).
  보안 패치를 받는 대상이 이 JDK이므로 최신을 따라간다.
- 바이트코드 타깃: Java 25 (class major 69). AGP 9.3.1 내장 D8 9.3.16 이 처리한다.

버전을 올릴 때 함께 봐야 하는 것이 셋 있다.

- AGP 는 Android 모듈의 자바 툴체인을 자체 기본값(JDK 21)으로 고정한다. 그래서 각
  Android 모듈이 `kotlin { jvmToolchain { ... } }` 로 툴체인을 명시한다.
- Hilt 가 만드는 `hiltJavaCompile*` 태스크는 모듈의 툴체인 설정을 따라오지 않는다.
  루트에서 모든 `JavaCompile` 을 같은 툴체인으로 묶는다.
- detekt 내장 컴파일러가 받는 `jvmTarget` 상한이 프로젝트 타깃보다 낮다. 정적 분석
  결과에는 영향이 없으므로 루트에서 상한까지만 낮춰 넘긴다.

`scripts/lib/env.sh`가 설치된 JDK의 버전을 비교해 가장 새 것을 고른다.
특정 버전을 강제하려면 `JAVA_HOME_FOR_GRADLE`을 지정한다.

## 빌드

```bash
scripts/device.sh build            # 디버그 APK
scripts/device.sh install          # 빌드 후 재설치

./gradlew ktlintCheck detekt       # 정적 분석
./gradlew test                     # JVM 단위 테스트
```

`scripts/device.sh`는 JDK와 Android SDK 경로를 직접 찾아 Gradle에 넘긴다.
`JAVA_HOME`이나 `local.properties`를 미리 맞춰 둘 필요가 없다.
`./gradlew`를 직접 쓸 때는 JDK 25와 `ANDROID_HOME`을 환경에 설정한다.

커밋 전 `ktlintCheck detekt test`가 통과해야 한다.

## 전체 검증

```bash
scripts/verify-all.sh              # 8단계 전부, 20초 녹화
scripts/verify-all.sh -s 30        # 녹화 길이 지정 (최소 10초)
scripts/verify-all.sh -B           # 빌드/테스트 생략 (기기 검증만)
scripts/verify-all.sh -D           # 기기 단계 생략 (빌드/테스트만)
```

환경 점검 → 정적 분석·단위 테스트 → 빌드 → 바이트코드 타깃 확인 → 설치 →
기기 스모크 → 타이머 녹화 E2E → 녹화 오디오 검증을 한 번에 돌리고 단계별로 보고한다.
실패해도 멈추지 않고 끝까지 돌려 어디까지 되는지 보여준다.

바이트코드 타깃 확인 단계는 `libs.versions.toml`의 `javaTarget`과 실제 class 파일의
major 버전을 대조한다. 툴체인 설정이 어긋나면 빌드는 통과하면서 산출물만 다른
버전이 되는 일이 있어 별도 가드로 둔다.

이 스크립트는 녹화 파일을 지우지 않는다. 실행 중 새로 생긴 녹화가 있으면 목록과
삭제 명령만 알려준다.

## 기기 제어 스크립트

```bash
scripts/doctor.sh                            # 개발 환경 점검 (여기서 시작한다)
scripts/device.sh info                       # 기기 모델·OS·해상도·앱 버전
scripts/device.sh restart                    # 앱 재시작
scripts/device.sh shot ./화면.png            # 스크린샷
scripts/device.sh tap "전체 화면"            # 화면의 문구를 찾아 탭
scripts/device.sh log                        # 앱 프로세스 logcat
scripts/device.sh pull-latest video          # 최신 녹화 회수
```

전체 명령은 `scripts/device.sh help`로 확인한다.

## 검증 스크립트

```bash
scripts/verify-timer-recording.sh -s 30      # 타이머 녹화 시간이 맞는지 확인
scripts/verify-recording-audio.sh            # 최신 녹화의 오디오가 무음이 아닌지 확인
scripts/verify-recording-audio.sh -t voice   # 최신 음성 녹음 확인
```

기기 1대가 연결된 상태에서 실행한다.
종료 코드는 0이 통과, 1이 검증 실패, 2가 준비·조작 오류다.

## 스크립트 실행 환경

macOS와 Windows(Git Bash)에서 모두 동작한다. 필요한 도구는
`adb`, JDK 25, `ffmpeg`/`ffprobe`, Python(Pillow)이며,
`scripts/lib/env.sh`가 PATH에 없어도 표준 설치 위치에서 찾아낸다.
부족한 것은 `scripts/doctor.sh`가 설치 명령까지 알려준다.

```bash
# Windows
winget install Gyan.FFmpeg
winget install EclipseAdoptium.Temurin.25.JDK
py -3 -m pip install Pillow

# macOS
brew install ffmpeg
brew install --cask temurin
pip3 install Pillow
```

Windows에서는 Git Bash(MSYS)가 네이티브 실행 파일에 넘기는 `/sdcard/...` 인자를
로컬 경로로 바꿔 버린다. `scripts/lib/env.sh`의 `adb` 래퍼가 이 변환을 막으므로,
스크립트에서 `adb`를 직접 호출하지 말고 반드시 이 래퍼를 거친다.

## 개발 방식

- TDD: 실패하는 테스트를 먼저 작성하고 최소 구현 후 리팩터한다.
- Root cause 원칙: 증상을 try-catch나 sleep으로 감추지 않고 원인을 규명해 수정한다.
- 기능 동작은 [기능명세서.md](기능명세서.md)의 [결정] 사항을 단일 진실 공급원으로 따른다.
- 규칙 전문은 [CLAUDE.md](CLAUDE.md).

## 문서

- [docs/performance-report.md](docs/performance-report.md) 성능 검증 결과
- [docs/security-checklist.md](docs/security-checklist.md) 보안 체크리스트
- [docs/postmortem/](docs/postmortem/) 장애 원인 분석
- [DESIGN_GUIDE.md](DESIGN_GUIDE.md) 디자인 가이드

## 라이선스

미정 (내부 프로젝트). Media3, AndroidX 등 오픈소스 라이브러리를 사용한다.
