package io.rami.screenrecorder.domain.model

import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds

/** 녹화 시작 카운트다운 선택지 (기능명세서 3절: 없음/3초/5초/10초, 기본 3초). */
enum class CountdownDuration(
    val seconds: Int,
) {
    NONE(0),
    THREE_SECONDS(3),
    FIVE_SECONDS(5),
    TEN_SECONDS(10),
}

/** 화면 회전 처리 정책 (기능명세서 5절). */
enum class OrientationPolicy {
    /** 회전 따라가기 (기본): 회전된 화면을 그대로 담고 레터박스로 처리. */
    FOLLOW_ROTATION,

    /** 시작 방향 고정: 녹화 시작 시점 방향 기준으로 담는다. */
    LOCK_START_ORIENTATION,
}

/** 타이머 녹화의 시간 제한 (기능명세서 11.4절: 최소 10초, 최대 12시간). */
sealed interface TimeLimit {
    /** 제한 없음 (기본). */
    data object None : TimeLimit

    /** [duration] 경과 시 자동 안전 중지한다. */
    data class Limited(
        val duration: Duration,
    ) : TimeLimit {
        init {
            require(duration in MIN_DURATION..MAX_DURATION) {
                "시간 제한은 $MIN_DURATION~$MAX_DURATION 범위여야 한다: $duration"
            }
        }
    }

    companion object {
        /** 직접 입력 최소값 (기능명세서 11.4절). */
        val MIN_DURATION: Duration = 10.seconds

        /** 직접 입력 최대값 (기능명세서 11.4절). */
        val MAX_DURATION: Duration = 12.hours
    }
}

/**
 * 한 녹화 세션의 전체 설정 (기능명세서 4절).
 *
 * [DEFAULT]는 명세의 기본값 조합이다.
 */
data class RecordingConfig(
    val resolution: ResolutionOption,
    val frameRate: FrameRate,
    val bitrate: BitrateOption,
    val codec: VideoCodec,
    val audioSource: AudioSource,
    val microphoneDevice: MicrophoneDevice,
    val microphoneVolume: VolumePercent,
    val internalVolume: VolumePercent,
    val countdown: CountdownDuration,
    val orientationPolicy: OrientationPolicy,
    val timeLimit: TimeLimit,
    val captureMode: CaptureMode,
) {
    companion object {
        /** 기능명세서 4절의 기본값 조합. */
        val DEFAULT =
            RecordingConfig(
                resolution = ResolutionOption.Fixed(Resolution.FHD),
                frameRate = FrameRate.FPS_60,
                bitrate = BitrateOption.Auto,
                codec = VideoCodec.H264,
                audioSource = AudioSource.INTERNAL,
                microphoneDevice = MicrophoneDevice.AUTO,
                microphoneVolume = VolumePercent(DEFAULT_VOLUME_PERCENT),
                internalVolume = VolumePercent(DEFAULT_VOLUME_PERCENT),
                countdown = CountdownDuration.THREE_SECONDS,
                orientationPolicy = OrientationPolicy.FOLLOW_ROTATION,
                timeLimit = TimeLimit.None,
                captureMode = CaptureMode.FullScreen,
            )

        private const val DEFAULT_VOLUME_PERCENT = 100
    }
}
