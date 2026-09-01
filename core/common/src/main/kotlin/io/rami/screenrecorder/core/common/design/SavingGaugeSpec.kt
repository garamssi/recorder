package io.rami.screenrecorder.core.common.design

/**
 * 저장 중·저장 완료 링 게이지의 수치 (DESIGN_GUIDE.md 4절).
 *
 * 같은 게이지를 두 곳이 그린다 — 홈은 Compose(`presentation/home/SavingGauge.kt`), 다른 앱 위
 * 오버레이는 플랫폼 뷰(`service/SavingGaugeView.kt`)다. 그리는 수단이 달라도 그림은 하나여야
 * 하므로 숫자를 여기 모은다. 각자 상수를 들면 한쪽만 고쳐져 같은 국면이 표시면마다 다르게
 * 보인다 — 실제로 진행률 보간과 완료 시 후광이 그렇게 갈라졌다.
 *
 * 링 지름·굵기·알파·맥동 주기는 **대기 상태의 녹화 버튼 링**도 함께 쓴다. 저장 게이지가 대기
 * 링과 같은 궤도를 쓰는 것이 설계이므로(상태가 바뀔 때 링이 움직이면 안 된다) 그쪽도 여기서
 * 읽는다.
 *
 * dp·sp·밀리초 같은 단위 없는 수치만 둔다. Compose 의 `.dp` 도, 플랫폼 뷰의 density 곱셈도
 * 쓰는 쪽이 한다.
 */
object SavingGaugeSpec {
    /** 링 지름(dp). 대기 상태의 녹화 버튼 링과 같은 자리를 쓴다. */
    const val RING_DP = 160f

    /** 트랙·역회전 원호 굵기(dp). */
    const val RING_WIDTH_DP = 2f

    /** 진행 원호 굵기(dp). 트랙보다 굵어야 진행이 읽힌다. */
    const val ARC_WIDTH_DP = 4f

    /** 트랙 링 불투명도. */
    const val RING_ALPHA = 0.3f

    /** 역회전 흐린 원호의 불투명도·스윕(도)·한 바퀴 주기(ms). */
    const val TRAIL_ALPHA = 0.25f
    const val TRAIL_SWEEP_DEGREES = 70f
    const val TRAIL_MILLIS = 3_000

    /** 후광 맥동 범위와 주기(ms). 대기 상태 링의 맥동과 같은 주기를 쓴다. */
    const val GLOW_MIN_ALPHA = 0.10f
    const val GLOW_MAX_ALPHA = 0.28f
    const val PULSE_MILLIS = 900

    /** 후광이 링 밖으로 번지도록 링보다 크게 그린다. */
    const val GLOW_RADIUS_SCALE = 1.35f

    /** 이 반경까지는 투명하다 — 링 안쪽을 비워 중앙 텍스트를 살린다. 링은 1/1.35 = 0.74 지점이다. */
    const val GLOW_HOLLOW_STOP = 0.62f

    /** 후광이 가장 진한 지점. 링 테두리 바로 밖이다. */
    const val GLOW_PEAK_STOP = 0.78f

    /** 12시에서 시작해 시계 방향으로 돈다. Canvas 의 0도는 3시 방향이다. */
    const val TOP_ANGLE_DEGREES = -90f
    const val FULL_TURN_DEGREES = 360f

    /** 링 가운데 경과 시간 글자 크기(sp). 녹화 중(56sp)보다 작은 것이 "이미 끝난 값" 을 뜻한다. */
    const val ELAPSED_SP = 40f

    /** "HH:MM:SS" 여덟 자를 링 안에 글꼴 배율 여유까지 두고 담는 크기(sp). */
    const val ELAPSED_LONG_SP = 28f

    /** "MM:SS" 다섯 자. 이보다 길면 시간 자리가 붙은 것이라 글자를 줄인다. */
    const val MINUTES_ONLY_CHARS = 5

    /** 발행이 확정됐을 때 링 가운데 체크 크기(dp). */
    const val CHECK_DP = 44f

    /**
     * 진행률이 바뀔 때 원호가 따라가는 시간(ms).
     *
     * 진행률은 0.5% 단위로 올라온다. 그대로 그리면 원호가 계단으로 튄다.
     */
    const val SWEEP_TWEEN_MILLIS = 150
}
