package io.rami.screenrecorder.service

/** 플로팅 버블 서비스에 보낼 명령 (기능명세서 11.1절). */
enum class FloatingBubbleCommand {
    /** 버블을 띄운다 ([FloatingCaptureService.startIntent]). 포그라운드 서비스로 시작해야 한다. */
    SHOW,

    /** 버블을 내린다 ([FloatingCaptureService.hideIntent]). 서비스가 스스로 멈춘다. */
    HIDE,
}

/**
 * 설정과 오버레이 권한을 보고 버블 서비스에 무엇을 보낼지 정한다 (기능명세서 11.1절 [결정]).
 *
 * 권한이 없으면 띄우지 않는다 — 권한 없이 시작하면 서비스가 스스로 멈춰 알림만 깜빡인다.
 * 권한 요청은 설정 화면이 담당한다.
 *
 * @param showFloatingBubble 저장된 설정값. **아직 읽지 못했으면 null.** 앱 화면은 DataStore 를
 *   읽기 전에 이미 한 번 그려지므로, 그 순간의 플레이스홀더 기본값을 저장된 값처럼 쓰면
 *   떠 있는 버블 서비스에 HIDE 를 보내 죽이게 된다.
 * @return 보낼 명령. 설정을 모르는 동안에는 null — 아무것도 보내지 않는다.
 */
fun floatingBubbleCommand(
    showFloatingBubble: Boolean?,
    canDrawOverlays: Boolean,
): FloatingBubbleCommand? =
    when {
        showFloatingBubble == null -> null
        showFloatingBubble && canDrawOverlays -> FloatingBubbleCommand.SHOW
        else -> FloatingBubbleCommand.HIDE
    }
