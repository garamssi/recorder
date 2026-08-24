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

minSdk 34 / targetSdk 36 / compileSdk 37. 빌드 JDK는 Temurin 21이 필요하다.

## 빌드

```bash
export JAVA_HOME=/path/to/temurin-21

./gradlew ktlintCheck detekt      # 정적 분석
./gradlew test                    # JVM 단위 테스트
./gradlew assembleDebug           # 디버그 APK

adb install -r app/build/outputs/apk/debug/app-debug.apk
```

커밋 전 `ktlintCheck detekt test`가 통과해야 한다.

## 검증 스크립트

```bash
scripts/verify-timer-recording.sh -s 30      # 타이머 녹화 시간이 맞는지 확인
scripts/verify-recording-audio.sh            # 최신 녹화의 오디오가 무음이 아닌지 확인
scripts/verify-recording-audio.sh -t voice   # 최신 음성 녹음 확인
```

기기 1대가 연결된 상태에서 실행한다. `adb`, `ffmpeg`/`ffprobe`, `python3`(Pillow)이 필요하다.
종료 코드는 0이 통과, 1이 검증 실패, 2가 준비·조작 오류다.

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
