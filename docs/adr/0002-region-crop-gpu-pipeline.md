# ADR-0002: 부분 영역 크롭 — SurfaceTexture + GLES 2.0 렌더 패스

- 상태: 승인됨 (2026-08-20)
- 관련 요구사항: 기능명세서 2.2절(부분 영역, 최소 320x240), 5절(회전 [결정]), CLAUDE.md "CPU 픽셀 복사는 60fps에서 병목이 되므로 금지"

## 문제

MediaProjection의 VirtualDisplay는 디스플레이 전체(또는 시스템이 스케일한 미러)만 서피스에 그린다.
사용자 지정 사각형만 담으려면 캡처와 인코더 사이에서 크롭이 필요하다.

## 검토한 대안

| 대안 | 평가 |
|---|---|
| A. ImageReader로 픽셀 읽어 CPU 크롭 | 1080p60에서 프레임당 8MB 복사 — 명세가 명시적으로 금지 |
| B. VirtualDisplay 크기를 영역 크기로 생성 | 시스템 미러는 전체 화면을 레터박스로 축소해 넣을 뿐, 부분 크롭이 아님 — 요구 불충족 |
| C. **SurfaceTexture(OES) + GLES 2.0 렌더 패스** | 채택 |

## 결정

세션 팩토리가 `FrameProcessor` 경계(`GlFrameProcessor`)를 생성한다.

```
VirtualDisplay(디스플레이 전체) → SurfaceTexture(OES 텍스처) → GL 크롭 렌더 → 인코더 입력 Surface
```

- **기하 계산은 domain**: `CropGeometry.compute()`가 정규화 텍스처 사각형과 레터박스 뷰포트를
  결정한다 (JVM 단위 테스트 5케이스). GL 계층은 계산 결과만 소비한다.
- 인코더 해상도 = 짝수 정렬된 영역 크기 (H.264 색차 서브샘플링 정렬).
- 타임스탬프: `SurfaceTexture.timestamp`(CLOCK_MONOTONIC)를 `eglPresentationTimeANDROID`로
  인코더에 그대로 전달 — 직결 경로와 동일한 시계 도메인이라 A/V 동기 로직 변경 없음.
- EGL 설정에 `EGL_RECORDABLE_ANDROID` 필수 (MediaCodec 입력 서피스 호환).
- 렌더 전용 HandlerThread. 시작 실패 시 스레드까지 정리하고 원인 그대로 전파.

### 회전 (명세 5절 [결정])

- 전체 화면/단일 앱: VirtualDisplay 미러링이 스케일·레터박스를 흡수하므로 GL 패스 불필요 (직결 유지).
- 부분 영역: 회전 시 좌표가 무효화되므로 `onCapturedContentResize` → 자동 일시정지 +
  `RegionInvalidatedByRotation` 이벤트 → 알림 "화면이 회전되었습니다. 영역을 다시 지정하거나 중지하세요."

## 검증 결과 (2026-08-20, Lenovo TB710FU / Android 16 실기기)

- 1600x980 영역 지정 → 산출물 해상도 1600x980, 재생 시 선택 영역 콘텐츠만 담김.
- 부분 영역 녹화 중 강제 회전 → 자동 일시정지 + 알림 확인, 복귀 후 중지 → 정상 저장(1.3MB).
- 전체 화면 녹화 중 회전은 일시정지되지 않음 (JVM 테스트 + 실기기).
- 알려진 엣지: 파이프라인 시작 직후(첫 프레임 전) 즉시 일시정지 후 중지하면 fragment가
  하나도 flush되지 않아 0바이트 임시 파일이 남는다 — Stage 10에서 처리 완료(빈 파일은
  "저장할 내용 없음"으로 정리, 완료 이벤트 미발행; 크래시 잔여물은 복구/삭제 제안).
