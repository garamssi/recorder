package io.rami.screenrecorder.data.audio

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import io.rami.screenrecorder.domain.model.MicrophoneDevice

/**
 * [AudioRecord] 기반 [PcmSource] 공통 구현.
 *
 * RECORD_AUDIO 권한이 없으면 생성 시점에 SecurityException이 발생하고,
 * 세션 시작 실패 경로에서 안전하게 정리된다.
 */
private class AudioRecordPcmSource(
    private val audioRecord: AudioRecord,
) : PcmSource {
    override fun start() {
        check(audioRecord.state == AudioRecord.STATE_INITIALIZED) { "AudioRecord 초기화 실패" }
        audioRecord.startRecording()
    }

    override fun read(buffer: ShortArray) {
        var offset = 0
        while (offset < buffer.size) {
            val read = audioRecord.read(buffer, offset, buffer.size - offset)
            if (read <= 0) return
            offset += read
        }
    }

    override fun release() {
        audioRecord.stop()
        audioRecord.release()
    }
}

/** 내부 재생 오디오 캡처 소스 (기능명세서 4.2절: USAGE_MEDIA/GAME/UNKNOWN). */
@SuppressLint("MissingPermission") // RECORD_AUDIO는 세션 시작 전 UI 플로에서 확보한다.
fun createPlaybackCapturePcmSource(projection: MediaProjection): PcmSource {
    val captureConfig =
        AudioPlaybackCaptureConfiguration
            .Builder(projection)
            .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
            .addMatchingUsage(AudioAttributes.USAGE_GAME)
            .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
            .build()
    val audioRecord =
        AudioRecord
            .Builder()
            .setAudioPlaybackCaptureConfig(captureConfig)
            .setAudioFormat(pcmFormat(AudioFormat.CHANNEL_IN_STEREO))
            .setBufferSizeInBytes(bufferSizeBytes(AudioFormat.CHANNEL_IN_STEREO))
            .build()
    return AudioRecordPcmSource(audioRecord)
}

/** 마이크 캡처 소스. [preferredDevice] 미연결 시 자동으로 폴백한다 (기능명세서 4.2절). */
@SuppressLint("MissingPermission")
fun createMicrophonePcmSource(
    context: Context,
    preferredDevice: MicrophoneDevice,
): PcmSource {
    val audioRecord =
        AudioRecord
            .Builder()
            .setAudioSource(MediaRecorder.AudioSource.MIC)
            .setAudioFormat(pcmFormat(AudioFormat.CHANNEL_IN_MONO))
            .setBufferSizeInBytes(bufferSizeBytes(AudioFormat.CHANNEL_IN_MONO))
            .build()
    resolvePreferredDevice(context, preferredDevice)?.let(audioRecord::setPreferredDevice)
    return AudioRecordPcmSource(audioRecord)
}

private fun pcmFormat(channelMask: Int): AudioFormat =
    AudioFormat
        .Builder()
        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
        .setSampleRate(AacAudioRecorder.SAMPLE_RATE_HZ)
        .setChannelMask(channelMask)
        .build()

private fun bufferSizeBytes(channelMask: Int): Int =
    AudioRecord.getMinBufferSize(
        AacAudioRecorder.SAMPLE_RATE_HZ,
        channelMask,
        AudioFormat.ENCODING_PCM_16BIT,
    ) * BUFFER_SIZE_MULTIPLIER

private fun resolvePreferredDevice(
    context: Context,
    preferred: MicrophoneDevice,
): AudioDeviceInfo? {
    val wantedTypes =
        when (preferred) {
            MicrophoneDevice.AUTO -> return null
            MicrophoneDevice.BUILT_IN -> intArrayOf(AudioDeviceInfo.TYPE_BUILTIN_MIC)
            MicrophoneDevice.BLUETOOTH ->
                intArrayOf(AudioDeviceInfo.TYPE_BLUETOOTH_SCO, AudioDeviceInfo.TYPE_BLE_HEADSET)

            MicrophoneDevice.WIRED ->
                intArrayOf(
                    AudioDeviceInfo.TYPE_WIRED_HEADSET,
                    AudioDeviceInfo.TYPE_USB_DEVICE,
                    AudioDeviceInfo.TYPE_USB_HEADSET,
                )
        }
    return context
        .getSystemService(AudioManager::class.java)
        .getDevices(AudioManager.GET_DEVICES_INPUTS)
        .firstOrNull { it.type in wantedTypes }
}

private const val BUFFER_SIZE_MULTIPLIER = 4
