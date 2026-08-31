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
- 오디오 160,623 프레임 = 3426.6초, 비디오 3428.1초 (1.5초 차)

즉 "107초가 유실됐다"는 최초 판단은 **틀렸다.** 그 107초는 remux 소요 시간이었다.
녹화 종료 시각과 MediaStore insert 시각(14:56:48)이 일치하는 것이 그 증거다.

발행 소요 실측: 433MB → 121초, 1.09GB → 161초.

### 왜 하필 발행 중에 죽었나 (가설, 미검증)

`RecordingCoordinator.kt` 의 `finalizeSession()` 은 발행 **전에** `capture.stop()` 으로
MediaProjection 을 해제한다. 그런데 `RecordingForegroundService` 는
`foregroundServiceType="mediaProjection|microphone"` 으로 startForeground 한 상태 그대로
2~4분을 더 산다. Android 14+ 는 프로젝션이 끝난 mediaProjection 타입 FGS 를 유효하지
않은 것으로 보므로, 시스템이 서비스를 강등하면 프로세스가 foreground importance 를 잃고
OEM 메모리 클리너의 표적이 된다. 109초째 SIGKILL 이 이 그림에 맞는다.

**실기기 검증이 선행되어야 확정할 수 있다** — Stopping 진입 후 `dumpsys activity processes`
의 oom_adj/procstate 확인, logcat 의 서비스 강등 로그 확인.

### 배제한 원인 (모두 실측으로 기각)

| 가설 | 기각 근거 |
|---|---|
| fMP4 fragment 꼬리 유실 | 키프레임이 평균 1.97초 간격으로 끝까지 정상 |
| PresentationTimeCorrector 의 단조 가드가 샘플을 버림 | PTS 최대 갭 0.100초, 드롭 흔적 없음 |
| 딥슬립으로 CLOCK_MONOTONIC 정지 | 13:59~14:59 `-screen`/`+running`/`device_idle` 이벤트 0건 |
| 일시정지 | 강제 키프레임·화면 내용 점프 없음 |
| 자동 중지(시간 제한/저장 공간) | 셋 다 finalizeSession 을 거치고 임시 파일을 지운다. 임시 파일이 남은 것 자체가 반증 |
| Mp4Remuxer 의 조기 종료 | 손상된 마지막 fragment 를 만나도 유실 상한이 fragment 1개(약 2초) |

커널 페이지 캐시는 SIGKILL 로 사라지지 않는다(`FileChannel.write` 완료 시점에 커널 소유).
따라서 유실이 있었다면 반드시 앱 프로세스 메모리 안이어야 하는데, 위 실측이 그것도 기각한다.

## 근본 원인 2 — 복구 다이얼로그가 진행 상태를 보여주지 않아 중복 발행을 유발했다

`RecoveryDialog` 는 "복구" 버튼에 진행 표시도 비활성화도 없었고, `HomeViewModel` 에
중복 실행 가드도 없었다. 433MB 를 remux 하는 2분 동안 화면이 그대로여서 사용자가
연타했고, 누른 횟수만큼 `publish()` 가 동시에 돌아 사본 10개가 만들어졌다.

**수정 완료** (커밋 `ddbf0f4` RED / `88ac354` GREEN):
- `HomeViewModel.recoveringId: StateFlow<String?>` — 진행 중이면 다른 요청을 무시
- `RecoveryDialog` 를 별도 파일로 분리하고 `isRecovering` 파라미터 추가 — 회전 표시와
  "복구하는 중…" 을 띄우고 두 버튼을 모두 잠근다
- 기능명세서 6.1절에 [결정]으로 명시

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

### 2. publish() 가 실패하면 임시 파일까지 지운다

`MediaStoreRecordingFileStore.publish()` 의 `finally { tempFile.delete() }` 는 발행이
실패해도 임시 파일을 삭제한다. remux 폴백까지 실패하는 경우(저장 공간 부족이 대표적)
원본도 사본도 남지 않는다. SIGKILL 에서는 `finally` 가 돌지 않으므로 이번 사고와는
무관하지만, 실재하는 소실 경로다.

### 3. publish() 가 파일을 두 번 훑고, 그 결과가 틀렸다

`readMetadata()` 의 `MediaMetadataRetriever` 는 fMP4 에 `mvhd` duration 이 없어
재생 시간을 알아내려면 모든 `moof` 를 훑는다. 그렇게 얻은 값 중:

- `codec = VideoCodec.H264` — 하드코딩. HEVC 로 녹화해도 H264 로 기록된다
- `frameRate = METADATA_KEY_CAPTURE_FRAMERATE` — 카메라 전용 키. 사실상 0
- `sizeBytes = file.length()` — remux 전 임시 파일 크기

`Mp4Remuxer.remux()` 가 이미 `RemuxResult.durationUs` 를 반환하는데 아무도 쓰지 않는다.
이 첫 번째 패스를 없애면 무방비 구간이 줄고 위 세 필드도 정확해진다.

### 4. 발행 중 상태가 어디에도 표시되지 않는다

`RecordingState.Stopping` 을 `RecordingForegroundService.observeStateForNotification()` 이
`else ->` 로 흘려보내, 발행 2~4분 동안 알림이 "녹화 중 00:57:08" 문구 그대로 남는다.
플로팅 버블은 `bubbleStateFor` 가 `Stopping` 을 `BubbleState.Idle` 로 떨어뜨려
**"녹화 시작" 버튼을 보여준다** — 누르면 MediaProjection 동의만 소비하고 조용히 무시된다.

또 자동 중지 시 "녹화 완료" 알림이 `AutoStopped` 이벤트 시점에 뜨는데, 이는 발행이
시작도 되기 전이다.

### 5. publish() 에 테스트가 하나도 없다

`data` 모듈에 Robolectric 이 없고 `MediaMetadataRetriever`/`MediaMuxer` 는 섀도잉되지
않는다. CLAUDE.md 3절이 요구하는 "플랫폼 API 는 얇은 어댑터 뒤로" 가 이 경로에는
적용되지 않았고, 그래서 위 2·3번 버그가 살아남았다. 발행 정책(성공 시에만 삭제 등)을
순수 오케스트레이션으로 분리해야 TDD 가 가능해진다.

## 사용자 환경 조치 (2026-08-31 적용)

- AOSP 배터리 최적화 예외: `adb shell dumpsys deviceidle whitelist +io.rami.screenrecorder`
- 최근 앱 잠금: 사용자가 직접 설정

`ZuiMemoryCleaner` 는 `com.android.server.am.ZuiMemoryCleaner` 로 `services.jar` 안에
있다. 앱도 아니고 컴포넌트도 아니어서 끌 수 없다. ZUIPMS 절전 화이트리스트는
`framework-res.apk` 에 박힌 ROM 고정 목록이라 추가도 불가능하다.
따라서 **앱이 죽는 것을 전제로 설계해야 한다.**
