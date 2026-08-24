package io.rami.screenrecorder.domain.repository

import io.rami.screenrecorder.domain.model.MediaVolume
import kotlinx.coroutines.flow.Flow

/**
 * 시스템 미디어 볼륨 경계 (기능명세서 10절).
 *
 * data 계층이 AudioManager로 구현한다. 플레이어는 자체 볼륨을 두지 않고 이 스트림에 연동해,
 * 하드웨어 볼륨 키와 화면의 슬라이더가 항상 같은 값을 가리키게 한다.
 */
interface MediaVolumeRepository {
    /** 현재 미디어 볼륨 스트림. 하드웨어 키로 바뀌어도 갱신된다. */
    fun observeVolume(): Flow<MediaVolume>

    /** 볼륨 단계를 설정한다. */
    suspend fun setLevel(level: Int)

    /** 음소거 상태를 설정한다. */
    suspend fun setMuted(muted: Boolean)

    /** 음소거를 토글한다. */
    suspend fun toggleMute()
}
