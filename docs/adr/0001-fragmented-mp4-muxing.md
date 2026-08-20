# ADR-0001: 먹싱 전략 — Media3 FragmentedMp4Muxer 채택

- 상태: **개정됨 (2026-08-20, 사용자 확정)** — 결정 D → C. 원본 결정과 개정 사유는 아래 "개정" 절 참조.
- 최초 승인: 2026-08-19
- 관련 요구사항: prompt.md 1-5절 "크래시, 강제 종료 시에도 이미 기록된 구간이 재생 가능하도록 먹싱 전략을 설계한다", 기능명세서 6.1절 크래시 복구

## 문제

플랫폼 `MediaMuxer`의 표준 MP4 출력은 `stop()` 시점에 moov 박스(인덱스)를 기록한다.
녹화 중 크래시·강제 종료가 발생하면 moov가 없어 **파일 전체가 재생 불가**가 된다.
장시간 녹화 앱에서는 수 시간 분량을 통째로 잃을 수 있는 치명적 손실 경로다.

## 검토한 대안

| 대안 | 평가 |
|---|---|
| A. 표준 MediaMuxer + 복구 포기 | 구현 최소. 크래시 시 데이터 전손 — 요구사항 위반으로 탈락 |
| B. 주기적 파일 교체(세그먼트 분할) | 세그먼트 경계에서 프레임 유실·A/V 불연속 위험, 결과물이 다중 파일 — 명세(단일 MP4 세션)와 충돌 |
| C. fMP4 녹화 후 정상 종료 시 일반 MP4로 remux | 호환성 최상. 종료 시 재처리 시간·저장 공간 2배 점유 |
| D. **Media3 `FragmentedMp4Muxer`로 fMP4 기록, 그대로 저장** | 채택 |

## 결정 (개정 후)

`FragmentedMp4Muxer`로 **기록**하되, 발행 시점에 **표준 MP4로 remux**한다 (대안 C).

- 녹화 중: fMP4 임시 파일 (크래시 안전성 유지)
- 발행 시(정상 종료 + 크래시 복구 모두): `MediaExtractor` + `MediaMuxer`로 재인코딩 없이 컨테이너만 바꿔
  MediaStore 출력 스트림에 바로 쓴다. 표준 moov(`stss`/`stco`/`mvhd` duration)가 생겨 시크와 재생시간이 정상 동작한다.
- MediaStore FD에 직접 쓰므로 중간 파일이 없다 — 저장 공간 2배 점유는 발생하지 않는다.

### 최초 결정 (2026-08-19, 개정 전)

`FragmentedMp4Muxer`로 기록한 fMP4를 **그대로** 저장한다 (대안 D).

- fragment 길이 약 2초: 크래시 시 손실은 최대 마지막 fragment 1개로 한정되고, 이미 기록된 fragment는 그대로 재생 가능하다.
- 정상 종료 시 재처리 없이 임시 파일을 MediaStore(`Movies/ScreenRecorder`)로 이동한다.
- fMP4는 ISO BMFF 표준이며 ExoPlayer·주요 플레이어에서 재생된다.

## 검증 결과 (2026-08-20, Lenovo TB710FU / Android 16 실기기)

1. ✅ 갤러리 기본 플레이어에서 재생 확인, ffprobe 파싱 정상 (H.264 1080p).
2. ✅ 녹화 중 `am force-stop` 후 임시 파일이 moof/mdat fragment 단위로 남아 21.5초 재생 가능.
3. 발견/수정한 root cause 2건:
   - fragment flush는 "다음 키프레임 + 2초 경과"에서만 발생 → 정적 화면에서 프레임/키프레임이
     없으면 전부 메모리에 잔류. 인코더에 `KEY_REPEAT_PREVIOUS_FRAME_AFTER`(0.1초)를 설정해
     최소 프레임 공급과 키프레임 주기를 보장.
   - `setSampleCopyingEnabled(true)` 필수: 먹서가 fragment 완성까지 샘플 버퍼 참조를 유지하는데
     어댑터는 쓰기 직후 코덱 버퍼를 반환하므로 복사가 없으면 데이터 오염.
4. 참고: fMP4 특성상 MediaStore duration 컬럼은 0으로 기록된다. 라이브러리 화면(Stage 7)에서
   MediaMetadataRetriever 기반으로 duration을 읽는다. → **이 가정은 틀렸다. 개정 절 참조.**

---

## 개정 (2026-08-20): 결정 D → C

### 문제

실기기에서 두 가지 결함이 확인됐고, 원인이 같았다.

1. **목록 재생시간이 항상 00:00** — `MediaStore.Video.Media.DURATION`이 0이다. 최초 검증에서 예상했던
   "MediaMetadataRetriever 폴백"도 **동작하지 않는다**. fMP4는 `mvhd`에 duration이 없고 fragment를 전수
   스캔해야 길이를 알 수 있는데, MediaMetadataRetriever는 그 스캔을 하지 않는다 (ffprobe는 한다).
2. **플레이어 시크가 항상 0으로 간다** — 로그로 확인: `seekBack`/`seekForward` 계산은 정확한데
   (`inc=10000`, 4678→14678) 시크 직후 위치가 0 근처로 되돌아온다.

파일 박스 구조를 뜯어 보니 원인이 분명했다:

```
top-level: ftyp, moov, moof×6, mdat×6
sidx  MISSING     ← 세그먼트 인덱스 없음
mfra  MISSING     ← fragment random access 없음
```

`FragmentedMp4Muxer`는 **seek 인덱스를 쓰지 않는다.** 인덱스 없는 fMP4는 임의 위치 탐색이 불가능해
ExoPlayer가 0으로 폴백한다. 기능명세서 10절(시크바, ±10초 점프)과 정면으로 충돌한다.

### 개정 사유

최초 결정 D는 "fMP4는 표준이고 재생된다"까지만 검증했고 **탐색 가능성(seekability)을 검증하지 않았다.**
재생만 되면 충분하다는 가정이 틀렸다. 대안 C는 최초 검토에서 이미 "호환성 최상"으로 평가했고,
비용으로 본 "저장 공간 2배"는 MediaStore FD에 직접 remux하면 발생하지 않는다.

### 남는 비용

- 중지 후 파일 크기에 비례하는 remux 시간 (재인코딩 없는 컨테이너 복사).
- remux 실패 시 원본 fMP4를 그대로 발행하는 폴백을 둔다 — 시크는 안 되더라도 녹화물을 잃지 않는 쪽이 낫다.

## 결과

- data 계층의 `MuxerWriter` 인터페이스 뒤로 격리하므로, 대안 C 전환 시에도 구현체 교체로 끝난다.
- 크래시 복구 UX(기능명세서 6.1): 앱 시작 시 캐시의 미완료 임시 파일을 감지해 "복구/삭제"를 제안한다.
