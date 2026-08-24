package io.rami.screenrecorder.domain.usecase

import io.rami.screenrecorder.domain.model.MediaVolume
import io.rami.screenrecorder.domain.repository.MediaVolumeRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/** 시스템 미디어 볼륨 스트림 (기능명세서 10절). */
class ObserveMediaVolumeUseCase
    @Inject
    constructor(
        private val volumeRepository: MediaVolumeRepository,
    ) {
        /** 현재 볼륨 스트림을 반환한다. */
        operator fun invoke(): Flow<MediaVolume> = volumeRepository.observeVolume()
    }

/** 슬라이더 비율로 미디어 볼륨을 설정한다 (기능명세서 10절). */
class SetMediaVolumeUseCase
    @Inject
    constructor(
        private val volumeRepository: MediaVolumeRepository,
    ) {
        /**
         * [fraction](0f..1f)을 현재 최대값 기준 단계로 바꿔 설정한다.
         *
         * 0보다 큰 값으로 올리면 음소거를 자동으로 해제한다 — 슬라이더를 올렸는데도
         * 음소거가 남아 있으면 "소리가 안 난다"는 혼란을 부른다.
         */
        suspend operator fun invoke(fraction: Float) {
            val current = volumeRepository.observeVolume().first()
            val level = current.levelFor(fraction)
            if (level > 0 && current.isMuted) volumeRepository.setMuted(false)
            volumeRepository.setLevel(level)
        }
    }

/** 음소거를 토글한다 (기능명세서 10절). */
class ToggleMuteUseCase
    @Inject
    constructor(
        private val volumeRepository: MediaVolumeRepository,
    ) {
        /** 현재 음소거 상태를 뒤집는다. */
        suspend operator fun invoke() = volumeRepository.toggleMute()
    }

/**
 * 볼륨 관련 유스케이스 묶음.
 *
 * 세 개를 개별 인자로 받으면 ViewModel 생성자가 지나치게 길어지므로 하나로 묶어 주입한다.
 */
class MediaVolumeUseCases
    @Inject
    constructor(
        /** 현재 볼륨 관찰. */
        val observe: ObserveMediaVolumeUseCase,
        /** 슬라이더 비율로 설정. */
        val set: SetMediaVolumeUseCase,
        /** 음소거 토글. */
        val toggleMute: ToggleMuteUseCase,
    )
