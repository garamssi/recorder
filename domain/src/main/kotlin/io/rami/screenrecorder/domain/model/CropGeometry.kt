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
            val sourceRect =
                if (cropRegion == null) {
                    NormalizedRect(0f, 0f, 1f, 1f)
                } else {
                    NormalizedRect(
                        left = cropRegion.x.toFloat() / sourceSize.width,
                        top = cropRegion.y.toFloat() / sourceSize.height,
                        right = cropRegion.right.toFloat() / sourceSize.width,
                        bottom = cropRegion.bottom.toFloat() / sourceSize.height,
                    )
                }
            val contentWidth = cropRegion?.width ?: sourceSize.width
            val contentHeight = cropRegion?.height ?: sourceSize.height
            return CropGeometry(sourceRect, letterbox(contentWidth, contentHeight, outputSize))
        }

        /** 콘텐츠 비율을 유지한 채 출력 안에 중앙 배치한다 (명세 5절: 레터박스). */
        private fun letterbox(
            contentWidth: Int,
            contentHeight: Int,
            outputSize: Resolution,
        ): Viewport {
            // 어느 축을 출력에 꽉 채울지: 콘텐츠가 출력보다 상대적으로 넓으면 너비 기준.
            val fillWidth =
                contentWidth.toLong() * outputSize.height >= contentHeight.toLong() * outputSize.width
            val width: Int
            val height: Int
            if (fillWidth) {
                width = outputSize.width
                height = evenDown(outputSize.width.toLong() * contentHeight / contentWidth)
            } else {
                height = outputSize.height
                width = evenDown(outputSize.height.toLong() * contentWidth / contentHeight)
            }
            return Viewport(
                x = (outputSize.width - width) / 2,
                y = (outputSize.height - height) / 2,
                width = width,
                height = height,
            )
        }

        /** GL 뷰포트/색차 정렬을 위해 짝수로 내림한다. */
        private fun evenDown(value: Long): Int = (value - (value % 2)).toInt()
    }
}
