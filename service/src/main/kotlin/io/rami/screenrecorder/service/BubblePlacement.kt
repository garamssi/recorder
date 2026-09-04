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
 * 배치할 창의 실측 크기.
 *
 * @param baseHeight 기준 요소(접힘 "+" 버튼 또는 녹화 중 pill)의 높이.
 *   나머지가 펼침 메뉴이며, 메뉴는 기준 요소의 위나 아래에 붙는다.
 */
internal data class BubbleLayout(
    val width: Int,
    val height: Int,
    val baseHeight: Int,
) {
    /** 메뉴가 없어 창이 곧 기준 요소인 상태. 펼칠 것이 없으니 방향을 정할 일도 없다. */
    val isBaseOnly: Boolean get() = height <= baseHeight
}

/**
 * 배치 결과 — 창 좌표, 이어서 배치할 때 넘길 기준선, 메뉴를 그릴 방향.
 *
 * [anchorBottom]은 들어온 값 그대로다. 이 계산은 기준선을 옮기지 않는다 ([placeBubble] 참고).
 */
internal data class BubblePlacement(
    val x: Int,
    val y: Int,
    val anchorBottom: Int,
    val menuBelowBase: Boolean,
)

/**
 * 버블 창이 화면 안에 들어오도록 좌표와 펼침 방향을 정한다 (기능명세서 11.1절).
 *
 * 세로 기준은 기준 요소의 아래 변([anchorBottom])이다. 사용자가 놓아둔 버튼은 펼치고 접어도
 * 움직이지 않아야 하므로, 메뉴는 그 위로 펼치는 것이 기본이고 위쪽이 모자라면 아래로 펼친다.
 * 양쪽 어디에도 담기지 않을 때만 창 전체를 화면 안으로 밀어 넣는다.
 *
 * 상태 바·제스처 바(시스템 바 인셋)는 피한다. 그 영역에 겹치면 그려지기는 해도
 * 터치를 SystemUI가 가져가 버블을 눌러도 반응하지 않는다.
 *
 * 화면 안으로 밀어 넣는 것은 그릴 때뿐이고 [anchorBottom]은 건드리지 않는다 (기능명세서 11.1절
 * [결정]). 기준을 옮기는 것은 사용자의 드래그뿐이다 — 밀어 올린 만큼을 기억해 버리면 화면이
 * 다시 넓어져도 원래 자리로 돌아오지 못한다. 세로에서 아래쪽에 놓아둔 버블을 가로로 돌렸다
 * 되돌리면 화면 중간에 남는다.
 */
internal fun placeBubble(
    anchorBottom: Int,
    snappedToRight: Boolean,
    layout: BubbleLayout,
    screen: BubbleScreen,
    margin: Int,
): BubblePlacement {
    val minY = screen.insetTop + margin
    val maxBottom = screen.height - screen.insetBottom - margin
    val topWithMenuAbove = anchorBottom - layout.height
    val topWithMenuBelow = anchorBottom - layout.baseHeight
    val menuBelowBase =
        !layout.isBaseOnly &&
            topWithMenuAbove < minY &&
            topWithMenuBelow + layout.height <= maxBottom
    val maxY = (maxBottom - layout.height).coerceAtLeast(minY)
    val y = (if (menuBelowBase) topWithMenuBelow else topWithMenuAbove).coerceIn(minY, maxY)
    val x =
        if (snappedToRight) {
            screen.width - screen.insetRight - layout.width - margin
        } else {
            screen.insetLeft + margin
        }
    return BubblePlacement(
        x = x,
        y = y,
        anchorBottom = anchorBottom,
        menuBelowBase = menuBelowBase,
    )
}
