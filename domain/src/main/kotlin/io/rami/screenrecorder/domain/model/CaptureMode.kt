package io.rami.screenrecorder.domain.model

/** 녹화 캡처 모드 (기능명세서 2.1절, prompt.md 1-1~1-2절). */
sealed interface CaptureMode {
    /** 디스플레이 전체를 캡처한다. */
    data object FullScreen : CaptureMode

    /** Android 14+ 앱 화면 공유로 단일 앱만 캡처한다. 시스템 UI·알림은 제외된다. */
    data object SingleApp : CaptureMode

    /** 전체 화면을 캡처한 뒤 GPU 파이프라인에서 [region]만 크롭한다. */
    data class Region(val region: CaptureRegion) : CaptureMode
}

/**
 * 부분 영역 녹화의 캡처 영역 (기능명세서 2.2절).
 *
 * 좌표는 화면 좌상단 기준 픽셀 단위이며, 최소 크기는 320x240이다.
 */
data class CaptureRegion(
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
) {
    /** 영역의 우측 끝 X 좌표. */
    val right: Int get() = TODO()

    /** 영역의 하단 끝 Y 좌표. */
    val bottom: Int get() = TODO()
}
