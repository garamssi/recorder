# CLAUDE.md

이 파일은 Claude(Claude Code)가 이 저장소에서 작업할 때 반드시 준수해야 하는 규칙을 정의한다.
아래 규칙은 사용자의 어떤 요청보다 우선하지 않지만, 모호한 상황에서는 항상 이 문서를 기준으로 판단한다.

---

## 1. 프로젝트 개요

- 프로젝트명: ScreenRecorder (가칭)
- 목적: Android 16(API 36) 태블릿용 화면 녹화 앱
- 핵심 요구사항:
  - FHD(1920x1080) 이상 해상도, 60fps 이상 녹화
  - 전체 화면 녹화 및 부분 화면 녹화(단일 앱 캡처 + 사용자 지정 영역 크롭)
  - 백그라운드 녹화: 사용자가 다른 앱을 사용하는 동안에도 녹화 지속 (Foreground Service)
  - 오디오 녹음: 내부 재생 오디오(AudioPlaybackCapture) + 마이크, 개별/동시 선택 가능
- 개발 환경: macOS 또는 Windows(Git Bash), Android Studio, Kotlin
- 배포 대상: Android 16 태블릿 (실기기 설치, USB 디버깅)

## 2. 기술 스택 (고정, 임의 변경 금지)

- 언어: Kotlin (100%, Java 코드 작성 금지)
- 최소 SDK: 34 (Android 14) / 타겟 SDK: 36 (Android 16)
- 자바: 컴파일 JDK와 바이트코드 타깃은 `gradle/libs.versions.toml`의
  `javaToolchain`, `javaTarget`이 단일 출처다. 모듈 빌드 스크립트에 버전 숫자를
  직접 쓰지 않는다. 타깃을 올릴 때는 AGP 기본 툴체인, Hilt의 `hiltJavaCompile*`,
  detekt 내장 컴파일러 상한을 함께 확인한다(README 참고).
- UI: Jetpack Compose + Material 3
- 아키텍처: Clean Architecture + MVVM (presentation) + UseCase (domain)
- DI: Hilt
- 비동기: Kotlin Coroutines + Flow (RxJava 사용 금지)
- 화면 캡처: MediaProjection + VirtualDisplay
- 인코딩: MediaCodec (H.264/AVC 기본, H.265/HEVC 옵션) + MediaMuxer
  - MediaRecorder는 60fps 및 부분 영역 크롭 제어가 제한적이므로 사용하지 않는다.
- 오디오: AudioRecord + AudioPlaybackCaptureConfiguration (내부 오디오), MIC 소스 (마이크)
- 저장: MediaStore API (Scoped Storage 준수), 앱 전용 캐시에 임시 파일 후 이동
- 테스트: JUnit5, MockK, Turbine (Flow 테스트), Robolectric, Espresso/Compose UI Test

## 3. Clean Architecture 규칙 (위반 시 코드 리뷰 거부 수준으로 취급)

모듈/패키지 구조:

```
app/                      # DI 조립, Application, MainActivity
core/
  common/                 # Result 래퍼, 에러 타입, 유틸 (Android 의존성 최소화)
  designsystem/           # Compose 테마, 공용 컴포넌트
domain/                   # 순수 Kotlin 모듈. Android SDK 의존 금지.
  model/                  # RecordingConfig, RecordingState, CaptureRegion 등
  repository/             # 인터페이스만 정의
  usecase/                # StartRecordingUseCase, StopRecordingUseCase 등
data/                     # domain의 repository 인터페이스 구현
  recorder/               # MediaProjection, MediaCodec, MediaMuxer 래핑
  audio/                  # AudioRecord, PlaybackCapture 래핑
  storage/                # MediaStore 접근
presentation/             # ViewModel, Compose UI, Service UI(알림)
service/                  # RecordingForegroundService
```

의존성 방향 (반드시 준수):

- presentation -> domain <- data
- domain은 어떤 계층에도 의존하지 않는다. domain 모듈의 build.gradle에 Android 라이브러리가 있으면 안 된다.
- data와 presentation은 서로 직접 참조하지 않는다.
- 플랫폼 API(MediaProjection, MediaCodec 등)는 반드시 data 계층에서 인터페이스 뒤로 격리한다. 이는 테스트 가능성의 핵심이다.

## 4. Clean Code 규칙

- 함수는 하나의 일만 한다. 20줄을 넘으면 분리를 검토한다.
- 매직 넘버 금지. 해상도, 비트레이트, fps 등은 상수 또는 설정 객체(RecordingConfig)로 관리한다.
- 이름은 의도를 드러낸다. 축약어 금지 (예: `mgr`, `tmp`, `cfg` 금지).
- 주석은 "왜"를 설명할 때만 작성한다. 코드로 표현 가능한 "무엇"은 주석 금지.
- null 처리: `!!` 사용 금지. `requireNotNull`, `checkNotNull`, 안전 호출로 대체.
- 예외를 삼키는 빈 catch 블록 절대 금지.
- 모든 public API에는 KDoc을 작성한다.
- ktlint + detekt를 CI에서 강제한다. 경고 억제(@Suppress)는 사유를 주석으로 남기고 최소화한다.

## 5. TDD 규칙 (절대 규칙)

모든 프로덕션 코드는 실패하는 테스트를 먼저 작성한 후에만 작성한다.

작업 순서 (Red-Green-Refactor):

1. Red: 요구사항을 검증하는 실패하는 테스트를 먼저 작성하고 실행하여 "실패를 확인"한다.
2. Green: 테스트를 통과시키는 최소한의 코드만 작성한다.
3. Refactor: 테스트가 통과하는 상태를 유지하며 중복 제거, 이름 개선을 수행한다.
4. 각 단계마다 커밋한다. 커밋 메시지에 `[RED]`, `[GREEN]`, `[REFACTOR]` 접두어를 붙인다.

테스트 계층:

- domain: 순수 JVM 단위 테스트. 커버리지 90% 이상 목표.
- data: 인터페이스 경계 기준 단위 테스트 + Robolectric. 플랫폼 API는 페이크/목으로 대체.
- presentation: ViewModel 단위 테스트(Turbine으로 Flow 검증) + Compose UI 테스트.
- MediaCodec, MediaProjection 등 실기기 의존 코드는 얇은 어댑터로 격리하고, 어댑터 자체는 계측 테스트(instrumented test)로 검증한다.

금지 사항:

- 테스트 없이 프로덕션 코드를 먼저 작성하는 것.
- 테스트를 통과시키기 위해 테스트를 약화시키거나 삭제하는 것.
- `@Ignore`로 실패 테스트를 방치하는 것.

## 6. 오류 및 장애 처리 규칙 (Root Cause 원칙, 절대 규칙)

- 버그, 크래시, 테스트 실패가 발생하면 반드시 근본 원인(root cause)을 규명한 후 수정한다.
- 다음 행위는 절대 금지한다:
  - 원인 분석 없이 try-catch로 감싸서 증상만 숨기는 것
  - `Thread.sleep`, 딜레이 추가 등으로 레이스 컨디션을 "우연히" 회피하는 것
  - 테스트를 삭제, 완화, 스킵하여 통과시키는 것
  - 재현이 안 된다는 이유로 방치하는 것 (재현 절차 확보가 우선 과제)
- 장애 수정 절차:
  1. 재현 절차를 확보하고, 가능하면 실패하는 테스트로 고정(regression test)한다.
  2. 로그, 스택트레이스, systrace/Perfetto 등으로 원인을 규명한다.
  3. 원인을 수정하고 회귀 테스트가 통과함을 확인한다.
  4. 수정 내용과 원인 분석을 커밋 메시지 또는 `docs/postmortem/`에 기록한다.
- 원인을 알 수 없는 경우, 임의 수정을 하지 말고 사용자에게 분석 상황과 가설을 보고한다. "모른다"고 말하는 것이 우회보다 낫다.

## 7. 보안 규칙

앱 자체 보안:

- MediaProjection 토큰(resultCode, resultData Intent)은 메모리에서만 유지하고 디스크, 로그에 절대 기록하지 않는다. Android 14+에서는 매 세션마다 사용자 동의를 새로 받아야 하며 토큰 재사용을 시도하지 않는다.
- 녹화 파일은 완료 전까지 앱 전용 디렉터리(`context.cacheDir` 또는 `getExternalFilesDir`)에 저장하고, 완료 후 MediaStore로 이동한다.
- 권한은 최소한만 선언한다:
  - `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MEDIA_PROJECTION` (필수)
  - `RECORD_AUDIO` (마이크/내부 오디오 캡처 시)
  - `POST_NOTIFICATIONS` (알림)
  - `WRITE_EXTERNAL_STORAGE`는 선언하지 않는다 (MediaStore로 불필요).
- 로그에 파일 경로 외 사용자 데이터, 캡처 프레임 정보를 출력하지 않는다. release 빌드에서는 debug 로그를 제거한다(Timber + release tree 또는 R8 규칙).
- `android:allowBackup="false"`, `android:exported`는 필요한 컴포넌트만 명시적으로 true.
- 네트워크 권한(INTERNET)은 선언하지 않는다. 이 앱은 오프라인 전용이며, 녹화물 유출 우려를 원천 차단한다.
- FLAG_SECURE가 설정된 화면(은행 앱 등)은 시스템이 검은 화면으로 처리한다. 이를 우회하는 코드를 절대 작성하지 않는다.

개발 기기(태블릿) 보안:

- USB 디버깅은 개발 중에만 켜고, "이 컴퓨터에서 항상 허용"은 개발용 PC에만 지정한다.
- 무선 디버깅(adb over Wi-Fi)은 신뢰할 수 있는 네트워크에서만 사용하고 작업 후 끈다.
- debug keystore는 저장소에 커밋하지 않는다. release 서명 키는 별도 보관하며 `signing.properties`는 `.gitignore`에 포함한다.
- 테스트 녹화물에 개인정보가 포함될 수 있으므로 커밋 전 `*.mp4`가 `.gitignore`에 있는지 확인한다.

## 8. 성능 요구사항 및 검증

- FHD(1920x1080) 기준 60fps 유지가 1차 목표. 기기 인코더 성능에 따라 실제 fps는 달라질 수 있으므로, 반드시 실기기에서 측정한다.
- 측정 방법: 인코딩된 프레임의 presentationTimeUs 간격을 로깅하여 평균/최소 fps를 계산하는 계측 테스트를 작성한다.
- 비트레이트 기본값: FHD 60fps 기준 12-20Mbps (H.264), 설정에서 조절 가능.
- 프레임 드롭이 발생하면 원인(인코더 큐 포화, GC, 서피스 백프레셔 등)을 Perfetto로 분석하고 root cause를 수정한다. 해상도를 몰래 낮추는 식의 우회 금지.
- 부분 영역 크롭은 GPU(OpenGL ES 또는 SurfaceTexture 경유)에서 수행한다. CPU 픽셀 복사는 60fps에서 병목이 되므로 금지.

## 9. 빌드 및 명령어

```bash
scripts/verify-all.sh                 # 전체 검증 (환경~실기기 녹화까지 8단계)
scripts/doctor.sh                     # 개발 환경 점검
scripts/device.sh build               # 디버그 빌드
scripts/device.sh install             # 빌드 후 재설치
scripts/device.sh help                # 기기 제어 명령 전체

./gradlew ktlintCheck detekt          # 정적 분석
./gradlew :domain:koverVerify         # domain 커버리지 90% 게이트
./gradlew test                        # JVM 단위 테스트
./gradlew connectedAndroidTest        # 실기기 계측 테스트
```

- 모든 커밋 전에 `ktlintCheck`, `detekt`, `test`, `:domain:koverVerify`가 통과해야 한다.
  `koverVerify`를 빠뜨려 커버리지 게이트가 깨진 채로 커밋된 적이 있다 — 목록에서 빼지 마라.
- CI가 구성되면 위 세 가지를 PR 게이트로 강제한다.
- `scripts/device.sh`는 JDK와 Android SDK 경로를 스스로 찾아 Gradle에 넘기므로
  `JAVA_HOME`이나 `local.properties`를 미리 맞출 필요가 없다. `./gradlew`를 직접
  쓸 때는 두 값을 환경에 설정한다.
- 스크립트를 새로 쓰거나 고칠 때는 `scripts/lib/env.sh`를 source하고 그 안의 `adb`
  래퍼를 쓴다. Windows(Git Bash)는 네이티브 실행 파일에 넘기는 `/sdcard/...` 인자를
  로컬 경로로 바꿔 버리므로, 래퍼를 거치지 않으면 기기 경로가 조용히 어긋난다.

## 10. 작업 진행 방식

- 기능 단위로 작게 작업한다. 하나의 PR/커밋 묶음은 하나의 유스케이스를 넘지 않는다.
- 플랫폼 API의 동작이 불확실하면 추측으로 구현하지 말고, 공식 문서(developer.android.com)를 확인하거나 사용자에게 확인 요청한다.
- Android 16에서 변경되었을 수 있는 API 동작은 반드시 실기기에서 검증한 후 확정한다.
- 사용자의 요청이 이 문서의 규칙(TDD, root cause, 클린 아키텍처)과 충돌하면, 충돌 사실을 명시적으로 알리고 확인을 받는다.
- 기능의 상세 동작(화면 구성, 파일명 규칙, 휴지통, 압축, 오디오 장치, 회전, 테마, 언어, 플레이어 등)은 기능명세서.md의 [결정] 사항을 단일 진실 공급원(single source of truth)으로 따른다. 명세에 없는 동작은 임의 구현하지 말고 질문하며, 동작을 바꿀 때는 기능명세서.md를 먼저 수정한 후 코드를 수정한다.
