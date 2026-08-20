package io.rami.screenrecorder.domain.model

/** 정규화 텍스처 사각형 (좌상단 기준, 0..1). */
data class NormalizedRect(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
)

/** 출력 서피스 안의 렌더 뷰포트 (픽셀). */
data class Viewport(
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
)

/**
 * GPU 크롭/레터박스 기하 계산 (기능명세서 5절).
 *
 * 인코더 해상도는 세션 내 고정이므로, 소스(디스플레이) 크기 변화나 부분 영역 크롭은
 * 렌더 단계에서 [sourceRect] 텍스처 좌표와 [destViewport] 뷰포트로 흡수한다.
 */
data class CropGeometry(
    val sourceRect: NormalizedRect,
    val destViewport: Viewport,
) {
    companion object {
        fun compute(
            sourceSize: Resolution,
            cropRegion: CaptureRegion?,
            outputSize: Resolution,
        ): CropGeometry {
            TODO("RED: 구현 전")
        }
    }
}
