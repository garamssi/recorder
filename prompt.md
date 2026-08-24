# prompt.md - Android 16 태블릿용 화면 녹화 앱 개발 프롬프트

아래 프롬프트를 Claude Code(또는 다른 AI 개발 도구)에 그대로 입력하여 개발을 시작한다.
프로젝트 루트의 CLAUDE.md와 함께 사용하는 것을 전제로 한다.

---

## 프롬프트 본문

Android 16(API 36) 태블릿에서 동작하는 화면 녹화 앱을 Kotlin으로 개발해줘.
개발 환경은 macOS 또는 Windows(Git Bash) + Android Studio이고, 빌드한 APK를 Android 16 태블릿에 설치해서 실기기 테스트한다.
프로젝트 루트의 CLAUDE.md 규칙을 모든 작업에서 최우선으로 준수해야 한다.
UI, 저장 정책, 파일명, 휴지통, 압축, 오디오 장치 선택, 회전, 테마, 언어, 플레이어 등 상세 기능 동작은 기능명세서.md의 [결정] 사항을 따른다. 명세와 이 프롬프트가 충돌하면 기능명세서.md가 우선하며, 명세에 없는 동작은 임의로 구현하지 말고 질문해라.

### 1. 핵심 기능 요구사항

1. 전체 화면 녹화
   - MediaProjection + VirtualDisplay로 디스플레이 전체를 캡처한다.
   - FHD(1920x1080) 이상 해상도, 60fps 이상을 목표로 MediaCodec(H.264) + MediaMuxer로 인코딩한다.
   - MediaRecorder는 사용하지 않는다 (fps, 크롭 제어 한계 때문).

2. 부분 화면 녹화 (두 가지 모드 모두 구현)
   - 모드 A - 단일 앱 녹화: Android 14+의 앱 화면 공유(app screen sharing)를 사용한다. 시스템 동의 다이얼로그에서 사용자가 "단일 앱"을 선택하면 해당 앱만 캡처되고 시스템 UI, 알림은 제외된다. onCapturedContentResize 콜백으로 크기 변화에 대응한다.
   - 모드 B - 사용자 지정 영역 녹화: 플랫폼에 임의 사각형 영역 캡처 공식 API는 없으므로, 전체 화면을 캡처한 뒤 GPU 파이프라인(SurfaceTexture + OpenGL ES)에서 지정 영역만 크롭하여 인코더 서피스에 렌더링한다. 영역 선택 UI는 드래그 가능한 오버레이(SYSTEM_ALERT_WINDOW)로 구현한다. CPU 픽셀 복사 방식은 60fps 병목이므로 금지한다.

3. 백그라운드 녹화
   - foregroundServiceType="mediaProjection"인 Foreground Service로 녹화를 유지하여, 사용자가 다른 앱으로 이동해도 녹화가 계속된다.
   - 알림에 녹화 시간 표시, 일시정지/재개/중지 액션 버튼을 제공한다.
   - Android 14+에서는 MediaProjection 동의를 매 세션마다 새로 받아야 하고 토큰 재사용이 불가함을 반영한다.
   - 시스템(상태 바 칩, 잠금 화면)에 의해 녹화가 중단될 수 있으므로 MediaProjection.Callback.onStop에서 리소스를 정리하고 파일을 안전하게 마무리(finalize)한다.

4. 오디오 녹음
   - 내부 오디오: AudioPlaybackCaptureConfiguration으로 다른 앱의 재생 사운드를 캡처한다 (USAGE_MEDIA, USAGE_GAME, USAGE_UNKNOWN). 앱이 캡처를 거부(allowedCapturePolicy)한 경우 무음이 될 수 있음을 UI에 안내한다.
   - 마이크: AudioRecord(MIC 소스)로 녹음한다.
   - 사용자가 "내부만, 마이크만, 둘 다(믹싱), 무음" 중 선택할 수 있어야 한다. 둘 다 선택 시 PCM 레벨에서 믹싱 후 AAC로 인코딩하여 비디오 트랙과 먹싱한다.
   - RECORD_AUDIO 런타임 권한 처리를 포함한다.

5. 저장 및 결과물
   - 녹화 중에는 앱 전용 디렉터리에 임시 저장하고, 완료 시 MediaStore(Movies/ScreenRecorder)로 이동한다. Scoped Storage를 준수하고 WRITE_EXTERNAL_STORAGE는 선언하지 않는다.
   - 크래시, 강제 종료 시에도 이미 기록된 구간이 재생 가능하도록 먹싱 전략을 설계한다 (주기적 flush 또는 fragmented MP4 검토, 선택 근거를 문서화).

### 2. 품질 및 개발 방식 (절대 규칙)

- TDD를 엄격히 따른다. 모든 프로덕션 코드는 실패하는 테스트를 먼저 작성하고 실패를 확인한 후에 작성한다. Red-Green-Refactor 사이클을 지키고 커밋 메시지에 [RED]/[GREEN]/[REFACTOR]를 표기한다.
- Clean Architecture를 준수한다. domain 모듈은 순수 Kotlin으로 유지하고 Android SDK 의존을 금지한다. MediaProjection, MediaCodec 같은 플랫폼 API는 data 계층의 어댑터 뒤로 격리하여 domain과 presentation이 테스트 가능하도록 한다.
- Clean Code를 준수한다. 매직 넘버 금지, `!!` 금지, 의도가 드러나는 이름, 작은 함수. ktlint와 detekt를 설정하고 통과시킨다.
- 오류, 장애, 테스트 실패가 발생하면 반드시 root cause를 규명하고 수정한다. try-catch로 증상 숨기기, sleep으로 레이스 컨디션 회피, 테스트 완화/삭제/스킵 같은 우회는 절대 금지한다. 원인을 모르면 추측 수정하지 말고 분석 상황을 보고해라.
- 프레임 드롭 등 성능 문제도 동일하게 root cause(인코더 큐 포화, 백프레셔, GC 등)를 Perfetto 등으로 분석해서 해결한다. 해상도나 fps를 몰래 낮추는 우회는 금지한다.

### 3. 보안 요구사항

- MediaProjection 동의 토큰을 디스크나 로그에 남기지 않는다.
- INTERNET 권한을 선언하지 않는다 (오프라인 전용 앱).
- FLAG_SECURE 화면이 검게 나오는 것은 정상 동작이며 이를 우회하는 코드를 절대 작성하지 않는다.
- android:allowBackup="false", 최소 권한 원칙, release 빌드에서 debug 로그 제거를 적용한다.
- .gitignore에 서명 키, keystore, 테스트 녹화물(*.mp4)을 포함한다.

### 4. 성능 목표 및 검증

- FHD 1920x1080, 60fps, 비트레이트 12-20Mbps(H.264)를 기본 프리셋으로 한다.
- 실제 fps를 측정하는 계측 테스트를 작성한다: 인코딩된 프레임의 presentationTimeUs 간격으로 평균/최저 fps를 산출하고, 평균 58fps 미만이면 실패로 간주하고 원인을 분석한다.
- 기기 인코더 성능 편차가 있으므로 MediaCodecInfo로 지원 해상도/fps를 사전 조회하고, 미지원 시 사용자에게 명확히 알린다 (몰래 다운그레이드 금지).

### 5. 작업 순서 (이 순서대로 단계별로 진행하고, 각 단계 완료 시 보고해줘)

1. 프로젝트 스캐폴딩: 멀티모듈 구조(app, core, domain, data, presentation, service), Hilt, ktlint/detekt, JUnit5/MockK/Turbine 설정
2. domain 계층: RecordingConfig, RecordingState, CaptureMode(FullScreen/SingleApp/Region) 모델과 UseCase를 TDD로 구현
3. data 계층 - 비디오 파이프라인: MediaProjection 어댑터, VirtualDisplay, MediaCodec 인코더, MediaMuxer를 인터페이스 뒤로 격리하여 TDD로 구현
4. data 계층 - 오디오 파이프라인: 내부 오디오 캡처, 마이크, PCM 믹싱, AAC 인코딩
5. Foreground Service: 알림, 세션 수명주기, onStop 콜백 처리
6. presentation - 홈/설정: 홈 화면(모드 선택, 프리셋 요약, 최근 녹화, 저장 공간), 설정 화면(비디오/오디오/저장/테마/언어), 카운트다운 오버레이, 권한 플로우 (기능명세서 2~5절)
7. presentation - 라이브러리: 녹화 목록(리스트/그리드, 정렬, 검색, 다중 선택), 이름 변경, 상세 정보, 공유, 휴지통 화면(복원/영구 삭제), 내장 플레이어(Media3 ExoPlayer) (기능명세서 6, 7, 9, 10절)
8. 부분 영역 크롭 GPU 파이프라인 (모드 B) + 압축(트랜스코딩) 기능 (기능명세서 8절)
9. 실기기 성능 검증: fps 측정 계측 테스트, 장시간 녹화 안정성(발열, 메모리), 문제 발견 시 root cause 분석 및 수정
10. 마감: 보안 점검 체크리스트 수행, release 빌드 설정, 문서화

각 단계에서 설계 결정(예: fragmented MP4 채택 여부, 크롭 파이프라인 구조)은 근거와 함께 설명하고, 불확실한 플랫폼 동작은 추측하지 말고 공식 문서 확인 또는 실기기 검증 후 확정해라.
