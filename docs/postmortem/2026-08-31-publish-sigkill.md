# 1시간 녹화가 "저장 안 됨 → 복구하시겠습니까?"로 끝나던 문제

작성일: 2026-08-31
기기: Lenovo TB710FU, Android 16 (ZUI)
관련 명세: 기능명세서 6.1절(저장 위치·크래시 복구), 11.1절(안전 중지)

## 증상

1시간 이상 화면 녹화를 하면 자동 저장이 되지 않고, 다음 실행에서 "복구하시겠습니까?"
다이얼로그가 떴다. 그 다이얼로그를 눌러도 화면이 그대로여서 사용자가 여러 번 눌렀고,
MediaStore 에 똑같은 녹화본이 10개(각 433MB, 합계 4.3GB) 쌓였다.

## 확정된 타임라인 (2026-08-31)

```
13:59:00.516  am_proc_start   pid 2367
13:59:41      녹화 시작 (파일명 Rec_20260831_135941)
14:56:48      finalizeSession() → publish() → MediaStore insert (is_pending=1)
14:56:49.134  녹화 내용 끝 (3428.134초)
              ↓ Mp4Remuxer.remux() 실행 중
14:58:36.665  am_kill [0,2367,io.rami.screenrecorder,50,ZuiMemoryCleaner_Recents,310424]
14:58:38.080  am_proc_start   pid 14249
14:58:43      복구 발행 시작
15:00:44      복구 발행 완료 (121초 소요)
```

## 근본 원인 1 — 발행(remux)이 2~4분 걸리고, 그 구간에 프로세스가 죽었다

녹화 자체는 정상이었다. `finalizeSession()` 이 끝까지 돌아 `muxer.close()` 로 마지막
fragment 까지 썼고, 그 다음 `publish()` 안에서 죽었다.

**녹화 내용은 하나도 잃지 않았다.** 복구된 파일을 실측한 결과:

- 비디오 패킷 104,285개, **PTS 최대 갭 0.100초** (= `KEY_REPEAT_PREVIOUS_FRAME_AFTER` 값)
- 57분 내내 **30.3 fps 일정** (5분 구간별 편차 ±0.5fps)
- 키프레임 1,739개, 평균 간격 1.97초, 최대 2.67초
  (설정 `I_FRAME_INTERVAL_SECONDS = 1` 의 약 2배다. 인코더가 요청 간격을 정확히 지키지 않는 것은
  흔하지만, 이 어긋남 자체는 따로 확인하지 않았다. 유실 판정에는 영향이 없다 — 끊김 없이 이어졌다는
  사실만 쓰기 때문이다.)
- 오디오 160,623 프레임 = 3426.6초, 비디오 3428.1초 (1.5초 차)

즉 "107초가 유실됐다"는 최초 판단은 **틀렸다.** 그 107초는 remux 소요 시간이었다.
녹화 종료 시각과 MediaStore insert 시각(14:56:48)이 일치하는 것이 그 증거다.

발행 소요 실측: 433MB → 121초, 1.09GB → 161초.

### 왜 하필 발행 중에 죽었나 (가설, 미검증)

`RecordingCoordinator.kt:265` 의 `finalizeSession()` 은 발행 **전에** `capture.stop()` 으로
MediaProjection 을 해제한다. 그런데 `RecordingForegroundService` 는 발행이 끝날 때까지
2~4분을 더 산다(`:287` 에서 Idle 이 되어야 `stopSelf`). 그 사이 서비스가 선언한 FGS 타입은
`foregroundServiceTypes(config.audioSource)`(`RecordingForegroundService.kt:175-185`)가 정한 값
그대로다 — **마이크를 쓰지 않는 세션이면 `MEDIA_PROJECTION` 단독**이므로, 프로젝션이 끝난
시점에 유효한 타입이 하나도 안 남는다.

여기서 "그래서 시스템이 서비스를 강등했고 프로세스가 표적이 됐다"고 잇고 싶어지지만,
**그 연결은 근거가 없다.** 확인된 Android 14+ 요건은 "`getMediaProjection` 전에 mediaProjection
타입으로 `startForeground` 해야 한다"는 **시작 시점** 제약이고, 프로젝션 종료 시 이미 떠 있는
FGS 를 시스템이 강등한다는 동작은 확인하지 못했다.

오히려 kill 로그가 이 가설과 어긋난다:

```
am_kill: [0, 2367, io.rami.screenrecorder, 50, ZuiMemoryCleaner_Recents, 310424]
                                           └─ oom_adj
```

AOSP 상수 기준 50은 `PERCEPTIBLE_RECENT_FOREGROUND_APP_ADJ` 로, 평범한 백그라운드
FGS(`PERCEPTIBLE_APP_ADJ` = 200)보다 **더 보호받는** 값이다. 실제로 사고 이후 같은 앱의
FGS 를 재봤을 때 `curProcState=4`(FOREGROUND_SERVICE), `oom cur=200` 이었다. 즉 죽는 순간
프로세스는 강등되기는커녕 평상시보다 중요도가 높았다. kill 사유의 `_Recents` 가 시사하듯
**ZUI 클리너가 oom_adj 를 보지 않고 최근 작업 목록 기준으로 죽였다**는 쪽이 데이터에 맞는다.
(ZUI 가 상수를 바꿨을 가능성은 배제하지 못한다.)

따라서 **FGS 타입 재선언은 "사고 재발 방지"가 아니라 "Android 14+ 규약 준수"로 다룬다.**
프로젝션이 끝났는데 그 타입을 유지하는 것은 그 자체로 옳지 않고, remux 는 말 그대로
media processing 이다. 필요한 권한(`FOREGROUND_SERVICE_MEDIA_PROCESSING`)은 이미
`data/src/main/AndroidManifest.xml:4` 에 있다.

### 실기기 검증 결과 (2026-08-31, Lenovo TB710FU / Android 16) — **가설 기각**

사고와 같은 조건(앱 백그라운드, `types=0x00000020` MEDIA_PROJECTION 단독)에서 4분 녹화를
중지하고 발행 구간을 2초 간격으로 관측했다.

```
17:13:12  fgs=2  curProcState=4  oom cur=200   ← 발행 시작 (readMetadata)
17:13:14  fgs=2  curProcState=4  oom cur=200
17:13:17  fgs=2  curProcState=4  oom cur=200
17:13:19  fgs=1  curProcState=4  oom cur=200   ← 발행 완료, 녹화 FGS 정상 종료
```

**발행 내내 procState 4 / adj 200 이 유지됐다. 강등이 없다.** logcat 에 FGS 관련 시스템
경고도 0건이고, `destroyService` 는 발행이 끝난 뒤에 나온다. 즉 "프로젝션이 끝나면 시스템이
mediaProjection 타입 FGS 를 강등한다"는 가설은 **사실이 아니다.**

덤으로 `adj=50` 의 정체도 확인됐다. 백그라운드 녹화를 막 시작한 직후에는 50(최근 포그라운드
보너스)이고, 약 2분 뒤 **200 으로 안착**한다. 사고 당시 50 이었던 것은 직전 14:58:08~23 에
사용자가 앱을 열었기 때문이다.

**따라서 FGS 타입 재선언은 사고 대책이 아니다.** 프로젝션이 끝났는데 그 타입을 유지하는
것이 규약상 옳지 않다는 이유만 남는다. 우선순위는 낮다.

### 배제한 원인 (모두 실측으로 기각)

| 가설 | 기각 근거 |
|---|---|
| fMP4 fragment 꼬리 유실 | 키프레임이 평균 1.97초 간격으로 끝까지 정상 |
| PresentationTimeCorrector 의 단조 가드가 샘플을 버림 | PTS 최대 갭 0.100초, 드롭 흔적 없음 |
| 딥슬립으로 CLOCK_MONOTONIC 정지 | 13:59~14:59 `-screen`/`+running`/`device_idle` 이벤트 0건 |
| 일시정지 | 강제 키프레임·화면 내용 점프 없음 |
| 자동 중지(시간 제한/저장 공간/일시정지 방치) | 셋 다 `AutoStopped` 이벤트로 완료 알림을 띄우는데(`RecordingForegroundService.kt:240`) 그 알림이 없었다. 시간 제한 설정도 없었다 |
| Mp4Remuxer 의 조기 종료 | 손상된 마지막 fragment 를 만나도 유실 상한이 fragment 1개(약 2초) |

처음에는 자동 중지를 "임시 파일이 남은 것 자체가 반증"이라고 기각했는데, 이는 무효한 논거였다.
임시 파일을 지우는 것은 `publish()` 의 `finally` 이고 SIGKILL 에서는 그것이 돌지 않는다. 자동
중지였더라도 같은 지점에서 죽으면 임시 파일은 똑같이 남는다.

커널 페이지 캐시는 SIGKILL 로 사라지지 않는다(`FileChannel.write` 완료 시점에 커널 소유).
따라서 유실이 있었다면 반드시 앱 프로세스 메모리 안이어야 하는데, 위 실측이 그것도 기각한다.

## 근본 원인 2 — 복구 다이얼로그가 진행 상태를 보여주지 않아 중복 발행을 유발했다

`RecoveryDialog` 는 "복구" 버튼에 진행 표시도 비활성화도 없었고, `HomeViewModel` 에
중복 실행 가드도 없었다. 433MB 를 remux 하는 2분 동안 화면이 그대로여서 사용자가
연타했고, 누른 횟수만큼 `publish()` 가 동시에 돌아 사본 10개가 만들어졌다.

**수정 완료** (`ddbf0f4` RED — 다이얼로그 분리와 `isRecovering` 파라미터 포함 / `88ac354` GREEN — 중복 실행 가드):
- `HomeViewModel.recoveringId: StateFlow<String?>` — 진행 중이면 다른 요청을 무시
- `RecoveryDialog` 를 별도 파일로 분리하고 `isRecovering` 파라미터 추가 — 회전 표시와
  "복구하는 중…" 을 띄우고 두 버튼을 모두 잠근다
- 기능명세서 6.1절에 [결정]으로 명시

## 근본 원인 3 — 복구 발행에는 포그라운드 서비스가 아예 없다

정상 발행은 그나마 보호받는다. `RecordingCoordinator` 는 `@Singleton` 에 자체
`CoroutineScope(SupervisorJob() + Dispatchers.Default)` 를 갖고(`DataModule.kt:118-122`),
`finalizeSession` 이 `withContext(NonCancellable)`(`RecordingCoordinator.kt:259`)로 감싸므로
서비스가 파괴돼도 발행은 이어진다. 게다가 서비스가 발행 끝까지 포그라운드로 살아 있다.

복구 발행은 다르다:

```
HomeViewModel.runRecoveryAction → viewModelScope.launch
  → RecoverRecordingUseCase → FileStoreRecordingRecoveryRepository.recover
    → fileStore.publish()
```

**서비스도, FGS 도, NonCancellable 도 없다.** 사용자가 화면을 벗어나면 그대로 취소되고,
같은 클리너가 죽이면 임시 파일은 남되 고아 IS_PENDING 레코드만 하나 더 늘어난다.
이번 사고에서 121초 걸린 그 복구 발행이 바로 이 상태로 돌았다.

즉 **근본 원인 1의 대책을 정상 발행에만 적용하면 더 취약한 쪽에 구멍이 그대로 남는다.**
발행은 어느 경로로 들어오든 같은 보호를 받아야 한다.

## 부수 발견 (미수정, 후속 과제)

### 1. 버려진 IS_PENDING 레코드가 수백 MB 를 점유한다

발행이 중단되면 MediaStore 에 고아 레코드가 남는다. 기기에서 실물 확인:

```
_display_name = Rec_20260831_135941.mp4
_data         = /storage/emulated/0/Movies/ScreenRecorder/.pending-1788760608-Rec_20260831_135941.mp4
is_pending=1, _size=NULL, 디스크 실물 334,964,341 바이트 (319MB)
date_added=2026-08-31 14:56:48, date_expires=2026-09-07 14:56:48
```

MediaStore 가 7일 뒤 자동 회수하므로 영구 누수는 아니지만, 그동안 사용자에게 보이지
않는 채로 저장 공간을 먹는다. 앱이 스스로 치워야 한다.

**주의**: `TranscodeWorker` 가 같은 폴더(`Movies/ScreenRecorder`)에 같은 방식으로
IS_PENDING 레코드를 만들고, WorkManager 잡이라 `RecordingState` 와 무관하게 돈다.
"우리 폴더의 pending 을 지운다"는 단순한 정리는 **진행 중인 압축 결과를 지운다.**
정리는 반드시 "이 프로세스가 지금 쓰고 있지 않은 것"만 대상으로 해야 한다.

그리고 `TranscodeWorker` 쪽이 고아를 더 잘 만든다. `TranscodeWorker.kt:166-191` 에는
`MediaStoreRecordingFileStore` 가 가진 `catch { resolver.delete(uri) }` 에 해당하는 정리가
**아예 없어서**, 복사 도중 실패하면 pending 레코드가 그냥 남는다. 덤으로 `:182` 의
`resolver.openOutputStream(uri)?.use { ... }` 는 null 이면 복사를 조용히 건너뛰고 `:185` 가
`IS_PENDING=0` 을 걸어 **0바이트 파일을 성공으로 발행**한다. CLAUDE.md 6절이 금지하는
증상 은폐다.

### 2. publish() 가 실패하면 임시 파일까지 지운다

`MediaStoreRecordingFileStore.publish()` 의 `finally { tempFile.delete() }` 는 발행이
실패해도 임시 파일을 삭제한다. remux 폴백까지 실패하는 경우(저장 공간 부족이 대표적)
원본도 사본도 남지 않는다. SIGKILL 에서는 `finally` 가 돌지 않으므로 이번 사고와는
무관하지만, 실재하는 소실 경로다.

### 3. publish() 가 파일을 두 번 훑고, 그 결과가 틀렸다

**실측 결과 이 항목의 성능 근거는 기각됐다.** 발행 단계를 갈라 재 보니:

| 녹화 | readMetadata | write(remux) |
|---|---|---|
| 4분 (22.3MB) | **5 ms** | 5,930 ms |
| 8분 (64.7MB) | **5 ms** | 26,298 ms |

`MediaMetadataRetriever` 는 전체를 훑지 않는다 — 파일 크기와 무관하게 5ms 다. "발행이 파일을
두 번 훑는다"는 서술은 틀렸고, `readMetadata` 를 없애도 무방비 구간은 0.02% 줄어들 뿐이다.

남는 것은 **정확성 문제뿐**이다. 그렇게 얻은 값 중:

- `codec = VideoCodec.H264` — 하드코딩. HEVC 로 녹화해도 H264 로 기록된다
- `frameRate = METADATA_KEY_CAPTURE_FRAMERATE` — 카메라 전용 키. 사실상 0
- `sizeBytes = file.length()` — remux 전 임시 파일 크기

`Mp4Remuxer.remux()` 가 이미 `RemuxResult.durationUs` 를 반환하는데 아무도 쓰지 않는다.
이 첫 번째 패스를 없애면 위 세 필드가 정확해진다(무방비 구간 단축 효과는 없다).

### 3-1. 진짜 병목은 remux 이고, 비용이 초선형으로 보인다

| 녹화 | 크기 | remux |
|---|---|---|
| 4분 | 22.3MB | 5.9초 |
| 8분 | 64.7MB | 26.3초 |

크기 2.9배에 시간 4.5배다. 데이터 2점이라 단정할 수 없지만, 무방비 구간을 실제로 줄이려면
`readMetadata` 가 아니라 여기를 봐야 한다. ADR-0001 개정 안건으로 분리한다.

### 4. 발행 중 상태가 어디에도 표시되지 않는다 — **대응 완료**

`RecordingState.Stopping` 을 `RecordingForegroundService.observeStateForNotification()` 이
`else ->` 로 흘려보내, 발행 2~4분 동안 알림이 "녹화 중 00:57:08" 문구 그대로 남는다.
플로팅 버블은 `bubbleStateFor` 가 `Stopping` 을 `BubbleState.Idle` 로 떨어뜨려
**"녹화 시작" 버튼을 보여준다** — 누르면 MediaProjection 동의만 소비하고 조용히 무시된다.

또 자동 중지 시 "녹화 완료" 알림이 `AutoStopped` 이벤트 시점에 뜨는데, 이는 발행이
시작도 되기 전이다. 반대로 **수동 중지에는 완료 알림이 아예 없다** — `showCompleted` 호출부는
`AutoStopped` 와 `QuickCaptureRunner` 뿐이고, `completedRecordings` 를 구독하는 곳은
`HomeScreen.kt:138` 하나다. 다른 앱을 쓰다가 중지한 사용자에게는 발행이 끝났다는 신호가
어디에도 없다.

### 5. remux 폴백은 무방비 구간을 두 배로 늘린다

`MediaStorePublishTarget.write()` 는 remux 가 실패하면 원본 fMP4 를 처음부터 전량 복사한다.
즉 최악의 경우 "remux 전량 시도 + 전량 복사" 로 노출 시간이 두 배 가까이 된다. 그리고
`Mp4Remuxer.kt` 의 `SAMPLE_BUFFER_BYTES = 4MB` 를 넘는 샘플이 하나라도 있으면
`readSampleData` 가 던져 곧장 이 폴백으로 빠진다.

### 6. publish() 에 테스트가 하나도 없다

`data` 모듈에 Robolectric 이 없고 `MediaMetadataRetriever`/`MediaMuxer` 는 섀도잉되지
않는다. CLAUDE.md 3절이 요구하는 "플랫폼 API 는 얇은 어댑터 뒤로" 가 이 경로에는
적용되지 않았고, 그래서 위 2·3번 버그가 살아남았다. 발행 정책(성공 시에만 삭제 등)을
순수 오케스트레이션으로 분리해야 TDD 가 가능해진다.

**대응 완료** (`0c7661e`): 발행 정책을 순수 JVM `RecordingPublisher` 로, 플랫폼 호출을
`PublishTarget`/`RecordingMetadataReader` 뒤로 분리하고 현재 정책을 고정하는 테스트 7개를
넣었다. 위 2·3번 버그는 아직 그대로이며, 이 seam 위에서 TDD 로 고친다.

## 사용자 환경 조치 (2026-08-31 적용)

- AOSP 배터리 최적화 예외: `adb shell dumpsys deviceidle whitelist +io.rami.screenrecorder`
- 최근 앱 잠금: 사용자가 직접 설정

`ZuiMemoryCleaner` 는 `com.android.server.am.ZuiMemoryCleaner` 로 `services.jar` 안에
있다. 앱도 아니고 컴포넌트도 아니어서 끌 수 없다. ZUIPMS 절전 화이트리스트는
`framework-res.apk` 에 박힌 ROM 고정 목록이라 추가도 불가능하다.
따라서 **앱이 죽는 것을 전제로 설계해야 한다.**

## 대응 현황 (2026-08-31 기준)

| 항목 | 상태 | 커밋 |
|---|---|---|
| 근본 원인 1 — 발행 중 프로세스 사망 | **열림** | 무방비 구간 2~4분은 그대로. remux 비용을 줄여야 실질 단축 |
| 근본 원인 2 — 복구 다이얼로그 중복 발행 | 닫힘 | `ddbf0f4` / `88ac354` |
| 근본 원인 3 — 복구 발행에 보호 없음 | 부분 | `2f6f97e` 로 NonCancellable 확보. FGS 는 미적용(실측상 이 기기 클리너엔 효과 불확실) |
| 부수 1 — 고아 IS_PENDING | 닫힘 | `a083405`, `92e8eba`, `6038358`. 압축 결과도 `4789529` 로 id 추적에 포함 |
| 부수 2 — 실패 시 임시 파일 삭제 | 닫힘 | `c28cef3` / `596a90d`, 판독 실패 경로는 `b370a47` |
| 부수 3 — readMetadata 부정확 | 부분 | 코덱·fps 는 실제 트랙에서 읽는다. `sizeBytes` 가 remux 전 값인 것은 남음 |
| 부수 3-1 — remux 초선형 비용 | **열림** | 크기 2.9배에 시간 4.5배. ADR-0001 개정 안건 |
| 부수 4 — 발행 중 상태 미표시 | 닫힘 | `fec2390`(알림·버블), `cf603cd`(완료 알림 시점·수동 중지) |
| 부수 5 — remux 폴백 2배 | **열림** | |
| 부수 6 — publish 테스트 부재 | 닫힘 | `0c7661e`, `5910b78` |
| TranscodeWorker 0바이트 성공 발행 | 닫힘 | `4789529` — 발행 경로 통일로 구조적으로 사라짐 |
| 카운트다운 구간 무동작 조작 | 닫힘 | `6d7035a`, `2e33837` |
| MediaProjection 토큰 누수 | **열림** | 보관소가 소유권을 표현하지 못한다. 무조건 비우면 진행 중 세션을 죽인다 |

### 이 조사에서 기각된 가설

전부 실측으로 기각했다. 기록해 두는 이유는, 그럴듯하지만 틀린 설명이 다시 나오지 않게 하기 위해서다.

| 가설 | 기각 근거 |
|---|---|
| fMP4 fragment 꼬리 유실 | 키프레임이 평균 1.97초 간격으로 끝까지 정상 |
| PresentationTimeCorrector 가 샘플을 버림 | PTS 최대 갭 0.100초 |
| 딥슬립으로 CLOCK_MONOTONIC 정지 | 해당 구간 화면·서스펜드 이벤트 0건 |
| 일시정지 | 강제 키프레임·화면 점프 없음 |
| readMetadata 가 파일 전체를 훑는다 | 계측 결과 파일 크기와 무관하게 5ms |
| 프로젝션 종료 후 시스템이 FGS 를 강등한다 | 발행 내내 procState 4 / adj 200 유지, 경고 0건 |
