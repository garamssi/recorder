package io.rami.screenrecorder.data.recorder

import android.media.MediaFormat
import android.view.Surface
import io.rami.screenrecorder.domain.model.Resolution
import io.rami.screenrecorder.domain.model.VideoCodec
import java.io.File
import java.nio.ByteBuffer

/**
 * 플랫폼 API(MediaProjection, MediaCodec, Muxer)를 격리하는 어댑터 경계 (CLAUDE.md 3절).
 *
 * RecordingCoordinator는 이 인터페이스들에만 의존하므로 JVM에서 페이크로 테스트 가능하다.
 * 실물 어댑터는 실기기 계측 테스트로 검증한다.
 */

/** 비디오 인코더 설정 (세션 내 고정, 기능명세서 5절). */
data class VideoEncoderConfig(
    val resolution: Resolution,
    val frameRateFps: Int,
    val bitrateBps: Int,
    val codec: VideoCodec,
)

/** 인코딩된 출력 샘플 한 개. */
data class EncodedSample(
    val buffer: ByteBuffer,
    val presentationTimeUs: Long,
    val isKeyFrame: Boolean,
)

/** MediaCodec 비디오 인코더 어댑터. 세션마다 새로 생성한다. */
interface VideoEncoder {
    /** 인코더를 구성하고 캡처가 그릴 입력 [Surface]를 반환한다. */
    fun prepare(config: VideoEncoderConfig, listener: Listener): Surface

    /** 인코딩을 시작한다. */
    fun start()

    /** 일시정지: 입력 Surface 프레임 공급을 중단/재개한다 (기능명세서 11.2절). */
    fun setSuspended(suspended: Boolean)

    /** 다음 프레임을 키프레임으로 강제한다 (기능명세서 11.3절 재개 화질). */
    fun requestKeyFrame()

    /** 인코더를 중지하고 자원을 해제한다. 남은 출력은 리스너로 모두 전달된 후다. */
    fun stopAndRelease()

    /** 인코더 출력 콜백. 인코더 스레드에서 호출된다. */
    interface Listener {
        /** 출력 포맷 확정 (muxer 트랙 추가 시점). */
        fun onOutputFormatReady(format: MediaFormat)

        /** 인코딩된 샘플 출력. */
        fun onSample(sample: EncodedSample)

        /** 복구 불가 인코더 오류. */
        fun onError(error: Throwable)
    }
}

/** MediaProjection + VirtualDisplay 캡처 어댑터. 세션마다 새로 생성한다 (Android 14+ 동의 1회성). */
interface ScreenCaptureSource {
    /** [encoderSurface]로 화면을 캡처하기 시작한다. */
    fun start(encoderSurface: Surface, resolution: Resolution, listener: Listener)

    /** 캡처를 중단하고 프로젝션을 해제한다. */
    fun stop()

    /** 캡처 수명주기 콜백. */
    interface Listener {
        /** 시스템(상태 바 칩, 잠금 화면 등)에 의해 캡처가 중단됨 — 안전 마무리 필요. */
        fun onStoppedBySystem()

        /** 캡처 대상 크기 변화 (회전, 단일 앱 리사이즈). */
        fun onContentResize(width: Int, height: Int)
    }
}

/** fMP4 먹서 어댑터 (ADR-0001). 세션마다 새로 생성한다. */
interface MuxerWriter {
    /** [outputFile]에 쓰기를 시작한다. */
    fun open(outputFile: File)

    /** 비디오 트랙을 추가하고 트랙 ID를 반환한다. */
    fun addVideoTrack(format: MediaFormat): Int

    /** 오디오 트랙을 추가하고 트랙 ID를 반환한다. */
    fun addAudioTrack(format: MediaFormat): Int

    /** 보정된 타임스탬프의 샘플을 기록한다. */
    fun writeSample(trackId: Int, sample: EncodedSample)

    /** 파일을 마무리하고 닫는다. */
    fun close()
}

/** 세션별 어댑터 생성 팩토리 (MediaCodec/MediaProjection은 세션 간 재사용 불가). */
interface RecorderSessionFactory {
    /** 새 비디오 인코더를 만든다. */
    fun createVideoEncoder(): VideoEncoder

    /** 새 캡처 소스를 만든다. 동의 토큰이 없으면 [IllegalStateException]. */
    fun createCaptureSource(): ScreenCaptureSource

    /** 새 먹서를 만든다. */
    fun createMuxer(): MuxerWriter
}

/** 녹화 파일 저장 경계 (임시 파일 + MediaStore 이동, 기능명세서 6.1절). */
interface RecordingFileStore {
    /** 앱 전용 캐시에 [fileName] 임시 파일을 만든다. */
    fun createTempFile(fileName: String): File

    /** 사용 중인 최종 파일명 집합 (충돌 순번용). */
    suspend fun existingFileNames(): Set<String>

    /** [tempFile]을 MediaStore로 이동(IS_PENDING insert→해제)하고 저장된 녹화본을 반환한다. */
    suspend fun publish(tempFile: File, fileName: String): io.rami.screenrecorder.domain.model.Recording
}

/** 디스플레이 정보 제공자 (기기 최대 해상도 해석용). */
fun interface DisplayInfoProvider {
    /** 현재 디스플레이 해상도. */
    fun currentResolution(): Resolution
}

/** 세션 파일명 결정자 (설정 접두어 + 타임스탬프, 기능명세서 6.2절). */
fun interface FileNameProvider {
    /** 충돌하지 않는 새 파일명을 반환한다. */
    suspend fun nextFileName(): String
}
