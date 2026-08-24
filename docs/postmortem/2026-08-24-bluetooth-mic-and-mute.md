# 블루투스 마이크가 녹음되지 않던 문제와 음소거 상태가 UI에 반영되지 않던 문제

작성일: 2026-08-24
관련 명세: 기능명세서 4.2절(오디오), 10절(플레이어), 13절(음성만 녹음)

## 1. 블루투스 마이크 (근본 원인)

### 증상
설정에서 "마이크 입력 장치 = 블루투스 헤드셋"을 선택해도 실제로는 내장 마이크로 녹음됐다.

### 근본 원인
`AudioRecord.setPreferredDevice(TYPE_BLUETOOTH_SCO)` 는 **선호도만 표현**한다.
블루투스 헤드셋 마이크는 SCO/LE 링크가 열려 있어야 입력 장치로 존재하는데,
A2DP만 연결된 상태에서는 SCO 입력 장치가 열거되지 않으므로 선호 장치 지정이 아무 효과가 없었다.

실기기 확인 (Lenovo TB710FU, Android 16):
- 수정 전: `Bluetooth SCO on, requested: false, applied: false`, `mScoAudioState: SCO_STATE_INACTIVE`
- 수정 후: `Applied Preferred communication device: bt_sco`, `SCO on requested/applied: true`, `SCO_STATE_ACTIVE_INTERNAL`

### 수정
`AudioManager.setCommunicationDevice()` (API 31+)로 통신 장치를 활성화하고,
`OnCommunicationDeviceChangedListener` 로 실제 전환을 확인한 뒤(최대 3초) `AudioRecord` 를 만든다.
세션 종료 시 `clearCommunicationDevice()` 로 원상 복구한다.

- `data/audio/MicrophoneRouting.kt` — `MicrophoneRouter` + `CommunicationDeviceController` 경계
- `data/audio/AndroidCommunicationDeviceController.kt` — AudioManager 구현 (전환 확인 포함)
- `data/audio/RoutedAudioRecorder.kt` — 레코더 해제 시 통신 경로까지 해제하도록 소유권을 묶었다
- `MODIFY_AUDIO_SETTINGS` 권한을 `data` 모듈 매니페스트에 선언 (오디오 라우팅 전용)

블루투스를 명시적으로 선택하면 출력도 SCO 로 전환되어 A2DP 음악 음질이 떨어진다.
플랫폼 제약이며 기본값이 "자동"이므로 사용자가 직접 고를 때만 발생한다 (기능명세서 4.2절에 명시).

### 폴백 안내
명세 4.2절이 약속한 "선택 장치 미연결 시 안내"가 구현되어 있지 않았다.
`RecordingSessionEvent.MicrophoneFellBack`(화면 녹화)과
`VoiceRecordingRepository.observeMicrophoneFallbacks()`(음성 녹음)로 알리고,
포그라운드 서비스가 토스트를 띄운다. 플로팅 버튼으로 다른 앱 위에서 시작할 수 있어
앱 내 스낵바로는 보이지 않고, 진행 알림 문구는 경과 시간 갱신으로 곧 덮이기 때문이다.

## 2. 음소거 상태가 UI에 반영되지 않던 문제 (근본 원인)

### 증상
플레이어 음소거 버튼을 누르면 시스템 음소거는 실제로 걸리는데(로그 확인),
아이콘과 슬라이더는 이전 값을 그대로 보여줬다.

### 근본 원인
볼륨 관찰을 `Settings.System` ContentObserver 하나에만 의존했다.
볼륨 **단계**는 Settings 에 기록되므로 알림이 오지만, **음소거 상태는 기록되지 않는다.**
그래서 앱이 스스로 건 음소거는 다시 읽을 계기가 없었다.

### 수정
`SystemMediaVolumeRepository` 가 외부 변경 스트림과 **자체 변경 신호**를 merge 하도록 바꿨다.
`setLevel`/`setMuted`/`toggleMute` 는 게이트웨이 호출 후 자체 신호를 emit 해 즉시 다시 읽는다.
`SystemVolumeGateway` 인터페이스로 AudioManager 를 격리해 이 동작을 JVM 단위 테스트로 고정했다
(`data/src/test/.../SystemMediaVolumeRepositoryTest.kt`).

## 3. 검증

실기기(Lenovo TB710FU, Android 16, 블루투스 A2DP 연결 상태):
- 슬라이더: 4 → 13 단계 변경 확인, 하드웨어 볼륨 키 변경도 UI에 반영
- 음소거: 4 → 음소거(0) → 해제(4 복원), 아이콘·content-desc 전환 확인
- 블루투스 마이크 음성 녹음 30초: SCO 활성 → 평균 -57.7dB / 최대 -20.2dB (무음 기준선 -91dB)
- 정지 후 `Applied Preferred communication device: null`, `SCO_STATE_INACTIVE` 로 복구
- 유선 마이크 선택(미연결): 폴백 토스트 표시 + 기본 마이크로 녹음(평균 -37.3dB)
- 내부 오디오(전체 화면 녹화): 평균 -16.1dB / 최대 -2.2dB

재현·검증 스크립트: `scripts/verify-recording-audio.sh`
