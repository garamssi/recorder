package io.rami.screenrecorder.service

// 플로팅 버블 창의 좌표 계산. 플랫폼 타입에 닿지 않는 순수 함수라 JVM 단위 테스트로 검증한다.

/** 배치에 필요한 화면 정보 — 창 크기와 시스템 바 인셋. */
internal data class BubbleScreen(
    val width: Int,
    val height: Int,
    val insetLeft: Int,
    val insetTop: Int,
    val insetRight: Int,
    val insetBottom: Int,
)

/**
 * 배치할 창의 실측 크기와 성격.
 *
 * @param isAnchorLayout 이 창이 기준선을 정의하는 접힘(기본) 레이아웃인지.
 *   펼침 레이아웃은 일시적이라, 화면에 맞추느라 밀려난 만큼을 기준선에 반영하면
 *   접을 때 돌아갈 자리를 잃는다.
 */
internal data class BubbleLayout(
    val width: Int,
    val height: Int,
    val isAnchorLayout: Boolean,
)

/** 배치 결과 — 창 좌표와 다음 계산에 쓸 기준선. */
internal data class BubblePlacement(
    val x: Int,
    val y: Int,
    val anchorBottom: Int,
)

/**
 * 버블 창이 화면 안에 완전히 들어오도록 좌표를 계산한다.
 *
 * 세로 기준은 창의 아래 변([anchorBottom])이다. 펼치면 메뉴가 위로 자라므로,
 * 아래 변을 고정해야 사용자가 놓아둔 토글 버튼이 제자리에 머문다.
 *
 * 상태 바·제스처 바(시스템 바 인셋)는 피한다. 그 영역에 겹치면 그려지기는 해도
 * 터치를 SystemUI가 가져가 버블을 눌러도 반응하지 않는다.
 */
internal fun placeBubble(
    anchorBottom: Int,
    snappedToRight: Boolean,
    layout: BubbleLayout,
    screen: BubbleScreen,
    margin: Int,
): BubblePlacement {
    val minY = screen.insetTop + margin
    val maxY = (screen.height - screen.insetBottom - layout.height - margin).coerceAtLeast(minY)
    val x =
        if (snappedToRight) {
            screen.width - screen.insetRight - layout.width - margin
        } else {
            screen.insetLeft + margin
        }
    val y = (anchorBottom - layout.height).coerceIn(minY, maxY)
    val nextAnchorBottom = if (layout.isAnchorLayout) y + layout.height else anchorBottom
    return BubblePlacement(x = x, y = y, anchorBottom = nextAnchorBottom)
}
