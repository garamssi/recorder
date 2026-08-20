package io.rami.screenrecorder.domain.model

/** 언어 설정 (기능명세서 4.5절 [결정]: 기본 한국어). */
enum class LanguageSetting {
    KOREAN,
    ENGLISH,
    SYSTEM,
}

/**
 * 홈 화면 모드 세그먼트의 선택값 (기능명세서 2.1절: 마지막 선택값 유지).
 *
 * 부분 영역의 실제 좌표는 세션마다 오버레이에서 새로 지정하므로(2.2절) 종류만 저장한다.
 */
enum class CaptureModeKind {
    FULL_SCREEN,
    SINGLE_APP,
    REGION,
}

/** 저장 위치 (기능명세서 4.3, 6.1절). */
sealed interface StorageLocation {
    /** 기본: MediaStore Movies/ScreenRecorder. */
    data object MediaStoreDefault : StorageLocation

    /** SAF 사용자 지정 폴더 (시스템 휴지통 사용 불가 안내 필요). */
    data class CustomTree(
        val treeUri: String,
    ) : StorageLocation
}

/**
 * 앱 전체 설정 (기능명세서 4절). DataStore에 저장하고 즉시 반영한다.
 *
 * 녹화 옵션 시트/모드 세그먼트의 "마지막 선택값 유지"(2.1절)도
 * [recording]을 갱신하는 방식으로 일원화한다.
 */
data class AppSettings(
    val recording: RecordingConfig,
    val selectedCaptureMode: CaptureModeKind,
    val fileNamePrefix: FileNamePrefix,
    val storageLocation: StorageLocation,
    val language: LanguageSetting,
    val showFloatingBubble: Boolean,
    val showTouches: Boolean,
) {
    companion object {
        /** 기능명세서 4절 기본값 조합. */
        val DEFAULT =
            AppSettings(
                recording = RecordingConfig.DEFAULT,
                selectedCaptureMode = CaptureModeKind.FULL_SCREEN,
                fileNamePrefix = FileNamePrefix.DEFAULT,
                storageLocation = StorageLocation.MediaStoreDefault,
                language = LanguageSetting.KOREAN,
                showFloatingBubble = false,
                showTouches = false,
            )
    }
}
