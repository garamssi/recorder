package io.rami.screenrecorder.data.recorder

import android.media.MediaFormat

/**
 * fMP4 트랙 게이트: 기대하는 모든 트랙이 등록될 때까지 샘플을 보류한다.
 *
 * FragmentedMp4Muxer는 첫 writeSampleData 시점에 moov(트랙 구성)를 확정하므로,
 * 이후 추가된 트랙은 파일에 반영되지 않는다. 비디오/오디오 콜백 스레드가 다르므로 동기화한다.
 */
class MuxerTrackGate(
    private val muxer: MuxerWriter,
    private val expectedTrackCount: Int,
) {
    private var videoTrackId: Int? = null
    private var audioTrackId: Int? = null
    private var muxingStarted = false
    private val pendingVideo = ArrayDeque<EncodedSample>()
    private val pendingAudio = ArrayDeque<EncodedSample>()

    /** 비디오 트랙을 등록한다. 모든 트랙이 준비되면 보류분을 기록한다. */
    @Synchronized
    fun registerVideoTrack(format: MediaFormat) {
        videoTrackId = muxer.addVideoTrack(format)
        startMuxingIfReady()
    }

    /** 오디오 트랙을 등록한다. */
    @Synchronized
    fun registerAudioTrack(format: MediaFormat) {
        audioTrackId = muxer.addAudioTrack(format)
        startMuxingIfReady()
    }

    /** 비디오 샘플을 기록하거나 게이트가 닫혀 있으면 보류한다. */
    @Synchronized
    fun writeVideo(sample: EncodedSample) {
        writeOrQueue(videoTrackId, sample, pendingVideo)
    }

    /** 오디오 샘플을 기록하거나 게이트가 닫혀 있으면 보류한다. */
    @Synchronized
    fun writeAudio(sample: EncodedSample) {
        writeOrQueue(audioTrackId, sample, pendingAudio)
    }

    private fun writeOrQueue(
        trackId: Int?,
        sample: EncodedSample,
        pending: ArrayDeque<EncodedSample>,
    ) {
        if (muxingStarted && trackId != null) {
            muxer.writeSample(trackId, sample)
        } else {
            pending += sample
        }
    }

    private fun startMuxingIfReady() {
        val registeredCount = listOfNotNull(videoTrackId, audioTrackId).size
        if (registeredCount < expectedTrackCount) return
        muxingStarted = true
        videoTrackId?.let { trackId -> pendingVideo.forEach { muxer.writeSample(trackId, it) } }
        audioTrackId?.let { trackId -> pendingAudio.forEach { muxer.writeSample(trackId, it) } }
        pendingVideo.clear()
        pendingAudio.clear()
    }
}
