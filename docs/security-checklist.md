# 보안 체크리스트 (Stage 10, CLAUDE.md 7절)

검증 기준: release 병합 매니페스트(`app/build/intermediates/merged_manifests/release/`)와
코드 검사. 일자: 2026-08-20.

| # | 항목 | 상태 | 근거 |
|---|---|---|---|
| 1 | INTERNET 미선언 (오프라인 전용) | ✅ | release 병합 매니페스트에 없음 |
| 2 | `allowBackup="false"` | ✅ | app 매니페스트 + 병합 결과 확인 |
| 3 | exported 최소화 | ✅ | release에서 exported=true는 런처 MainActivity뿐 |
| 4 | 권한 최소 선언 | ✅ | FGS(mediaProjection/microphone/mediaProcessing), RECORD_AUDIO, POST_NOTIFICATIONS, SYSTEM_ALERT_WINDOW(부분 영역). WRITE_EXTERNAL_STORAGE 없음. WorkManager의 RECEIVE_BOOT_COMPLETED는 tools:node="remove" |
| 5 | MediaProjection 토큰 메모리 전용 | ✅ | `MediaProjectionTokenHolder` — 인메모리 보관, 세션 소비 시 폐기, 디스크/로그 기록 없음. 매 세션 동의 재요청 |
| 6 | 녹화 중 임시 파일은 앱 전용 캐시 | ✅ | `context.cacheDir/recordings` → 완료 시 MediaStore(IS_PENDING) 이동 |
| 7 | 로그에 사용자 데이터/프레임 미출력 | ✅ | 프로덕션 코드에 Log/Timber 호출 자체가 없음 (grep 검증) |
| 8 | FLAG_SECURE 우회 코드 없음 | ✅ | 시스템 기본 동작(검은 화면) 그대로. 관련 API 미사용 |
| 9 | keystore/signing.properties/*.mp4 gitignore | ✅ | `.gitignore` 확인 |
| 10 | release R8 minify | ✅ | `isMinifyEnabled = true` + proguard-android-optimize, assembleRelease 성공 |

## 비고

- `WAKE_LOCK`/`ACCESS_NETWORK_STATE`/`BIND_JOB_SERVICE`는 WorkManager(압축 백그라운드 작업)
  동작에 필요한 라이브러리 병합 권한으로 유지한다.
- profileinstaller receiver의 `android:permission="android.permission.DUMP"`는 권한 요청이
  아니라 수신자 보호 속성(shell 전용 접근)이다.
- release 서명 키는 저장소 외부에서 관리한다 (`signing.properties`는 gitignore).
