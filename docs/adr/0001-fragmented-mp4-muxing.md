# ADR-0001: 먹싱 전략 — Media3 FragmentedMp4Muxer 채택

- 상태: 승인됨 (2026-08-19, 사용자 확정)
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

## 결정

`androidx.media3:media3-muxer`의 `FragmentedMp4Muxer`를 사용해 fragmented MP4로 기록한다.

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
   MediaMetadataRetriever 기반으로 duration을 읽는다.

## 결과

- data 계층의 `MuxerWriter` 인터페이스 뒤로 격리하므로, 대안 C 전환 시에도 구현체 교체로 끝난다.
- 크래시 복구 UX(기능명세서 6.1): 앱 시작 시 캐시의 미완료 임시 파일을 감지해 "복구/삭제"를 제안한다.
