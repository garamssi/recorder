# ScreenRecorder

Android 16(API 36) 태블릿용 화면 녹화 앱. FHD 60fps 이상 녹화, 백그라운드 녹화,
내부/마이크 오디오, 부분 영역 크롭, 압축, 내장 플레이어를 지원한다.

> 오프라인 전용 앱이다. 네트워크 권한(INTERNET)을 선언하지 않으며, 녹화물은 기기를 벗어나지 않는다.

## 주요 기능

- **녹화 모드**: 전체 화면 / 단일 앱 / 부분 영역(드래그 선택, GPU 크롭)
- **해상도·품질**: 기기 최대 / 1080p / 720p, 60·30fps, 자동/고정 비트레이트, H.264·HEVC
- **오디오**: 내부 재생음(AudioPlaybackCapture) + 마이크, 개별/동시 선택, 볼륨 게인
- **백그라운드 녹화**: Foreground Service, 경과 시간·일시정지/재개/중지 알림
- **자동 안전 중지**: 타이머(직접 입력), 저장 공간 부족(200MB), 일시정지 30분 초과
- **크래시 복구**: 강제 종료로 저장되지 못한 녹화를 다음 실행에서 복구/삭제 제안 (fMP4 기반)
- **라이브러리**: 리스트/그리드, 정렬·검색·다중 선택, 이름 변경, 공유, 상세 정보
- **휴지통**: 삭제 = 30일 보관(시스템 휴지통), 복원/영구 삭제
- **압축**: 고효율(HEVC)/표준/최대 프리셋, 원본 보존, 백그라운드 진행률 알림
- **플레이어**: ExoPlayer 배속·±10초 점프·전체 화면
- **설정**: 테마(시스템/라이트/다크), 앱별 언어(한국어/English/시스템)

## 아키텍처

Clean Architecture + MVVM. 의존 방향은 Gradle 모듈로 강제한다.

```
app          — DI 조립, MainActivity, 동의/권한/서비스 연동, NavHost
core/common  — 순수 JVM 유틸 (시간·용량 포맷)
core/designsystem — Compose 테마·공용 컴포넌트
domain       — 순수 Kotlin. 모델 / repository 인터페이스 / UseCase (Android 의존 0)
data         — repository 구현 + 플랫폼 어댑터(MediaProjection/MediaCodec/fMP4/오디오/MediaStore/Transformer)
presentation — Compose UI + ViewModel (home/library/player/trash/settings/overlay)
service      — RecordingForegroundService, 알림
```

- `presentation → domain ← data` (data↔presentation 상호 참조 금지)
- 플랫폼 API는 전부 data 계층의 어댑터 인터페이스 뒤로 격리 → JVM 페이크로 단위 테스트, 실물은 계측 테스트

핵심 설계 결정은 [`docs/adr/`](docs/adr/)에 기록한다:
- [ADR-0001](docs/adr/0001-fragmented-mp4-muxing.md): fMP4 먹싱 (크래시 복구)
- [ADR-0002](docs/adr/0002-region-crop-gpu-pipeline.md): 부분 영역 GPU 크롭 파이프라인

## 기술 스택

Kotlin · Jetpack Compose + Material 3 · Hilt · Coroutines/Flow · MediaProjection + VirtualDisplay ·
MediaCodec + Media3 FragmentedMp4Muxer · AudioRecord + AudioPlaybackCapture · MediaStore ·
Media3 Transformer + WorkManager(압축) · ExoPlayer · DataStore · coil3(썸네일)

- minSdk 34 / targetSdk 36 / compileSdk 37
- **빌드 JDK: Temurin 21 필수** (JDK 25는 Gradle 미지원)

## 빌드

```bash
export JAVA_HOME=/path/to/temurin-21

./gradlew ktlintFormat                 # 포매팅
./gradlew ktlintCheck detekt           # 정적 분석
./gradlew test                         # JVM 단위 테스트
./gradlew :domain:koverVerify          # domain 커버리지 게이트(90%)
./gradlew connectedAndroidTest         # 실기기 계측 테스트 (fps 등)
./gradlew assembleDebug                # 디버그 APK
./gradlew assembleRelease              # release (R8 minify)

adb install -r app/build/outputs/apk/debug/app-debug.apk
```

모든 커밋 전 `ktlintCheck detekt test`가 통과해야 한다.

## 개발 방식

- **TDD**: 실패하는 테스트 먼저 → 최소 구현 → 리팩터. 커밋에 `[RED]`/`[GREEN]`/`[REFACTOR]` 표기.
- **Root cause 원칙**: 증상을 try-catch/sleep로 감추지 않고 원인을 규명해 수정한다.
- 기능 동작은 [`기능명세서.md`](기능명세서.md)의 [결정] 사항을 단일 진실 공급원으로 따른다.
- 규칙 전문은 [`CLAUDE.md`](CLAUDE.md).

## 성능·보안

- 성능 검증 결과: [`docs/performance-report.md`](docs/performance-report.md) (fps 계측, 일시정지 보정, 안정성)
- 보안 체크리스트: [`docs/security-checklist.md`](docs/security-checklist.md)

## 라이선스

미정 (내부 프로젝트). Media3, AndroidX 등 오픈소스 라이브러리를 사용한다.
