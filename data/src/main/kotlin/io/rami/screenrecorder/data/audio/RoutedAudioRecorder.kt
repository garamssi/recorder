package io.rami.screenrecorder.data.audio

import io.rami.screenrecorder.data.recorder.AudioRecorder
import io.rami.screenrecorder.data.recorder.PauseOffsetTracker

/**
 * 세션 종료 시 마이크 통신 경로까지 함께 해제하는 [AudioRecorder] 데코레이터.
 *
 * 경로를 잡은 주체가 해제까지 책임지게 묶어, 녹화 실패 경로에서도 다른 앱의
 * 오디오 라우팅이 SCO에 고정된 채 남지 않게 한다.
 */
class RoutedAudioRecorder(
    private val delegate: AudioRecorder,
    private val router: MicrophoneRouter,
) : AudioRecorder {
    override fun start(
        listener: AudioRecorder.Listener,
        pauseOffset: PauseOffsetTracker,
    ) = delegate.start(listener, pauseOffset)

    override fun setSuspended(suspended: Boolean) = delegate.setSuspended(suspended)

    override fun stopAndRelease() {
        try {
            delegate.stopAndRelease()
        } finally {
            router.release()
        }
    }
}
