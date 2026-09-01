# 오버레이 창 붙이기가 무방비다 (미해결)

- 발견: 2026-09-01, 저장 완료 배너 코드 리뷰 중
- 상태: **미해결** — `CountdownOverlayWindow` 에 남아 있다

## 증상

`WindowManager.addView` 가 던지는 예외를 아무도 받지 않는다. Handler 콜백 안에서 던진 예외는
잡히는 곳이 없어 프로세스가 죽는다.

## 원인

`Settings.canDrawOverlays()` 검사와 실제 `addView` 는 스레드가 다르다. 검사는 호출 스레드에서
하고 창 조작은 메인 스레드로 미룬다. 그 사이에 사용자가 "다른 앱 위에 표시" 권한을 끄면
`BadTokenException` 이 난다. 일부 OEM 은 검사가 통과해도 창을 거부한다.

`removeView` 도 대칭이다. 시스템이 권한 회수와 함께 창을 먼저 떼어 가면 내부 참조는 남아 있고
`IllegalArgumentException("View not attached to window manager")` 이 난다.

## 대응

- `SaveCompleteOverlayWindow`: 해결했다. 창 조작을 `OverlayWindows` 경계로 빼고
  `SystemOverlayWindows` 가 두 예외를 받아 표시를 포기한다. 거부는 권한 없음과 같은 상황이므로
  토스트 폴백으로 넘어간다.
- `CountdownOverlayWindow.kt:44,60`: **아직 그대로다.** 같은 패턴이고 같은 방식으로 고칠 수
  있지만, 저장 완료 배너 작업의 범위를 넘어 손대지 않았다. 카운트다운은 녹화 시작 직전에
  뜨므로 여기서 죽으면 녹화 자체가 시작되지 못한다 — 배너보다 영향이 크다.

## 재현

권한을 켠 상태로 녹화를 시작하고, 카운트다운이 뜨기 직전에 설정에서 "다른 앱 위에 표시" 를
끈다. 타이밍이 좁아 안정적인 재현 절차는 아직 없다.
