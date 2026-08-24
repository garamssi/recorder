package io.rami.screenrecorder.domain.usecase

import io.rami.screenrecorder.domain.model.MediaVolume
import io.rami.screenrecorder.domain.repository.MediaVolumeRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** 플레이어 볼륨 유스케이스 (기능명세서 10절). */
class MediaVolumeUseCaseTest {
    private val repository = FakeMediaVolumeRepository()

    @Test
    fun `관찰 유스케이스는 저장소 스트림을 그대로 노출한다`() =
        runTest {
            repository.volume.value = MediaVolume(level = 3, max = 14, isMuted = false)

            val observed = ObserveMediaVolumeUseCase(repository)().first()

            assertEquals(MediaVolume(3, 14, false), observed)
        }

    @Test
    fun `비율을 현재 최대값 기준 단계로 바꿔 설정한다`() =
        runTest {
            repository.volume.value = MediaVolume(level = 0, max = 14, isMuted = false)

            SetMediaVolumeUseCase(repository)(fraction = 0.5f)

            assertEquals(7, repository.lastLevel)
        }

    @Test
    fun `0보다 큰 볼륨을 설정하면 음소거를 자동 해제한다`() =
        runTest {
            repository.volume.value = MediaVolume(level = 0, max = 14, isMuted = true)

            SetMediaVolumeUseCase(repository)(fraction = 0.5f)

            assertTrue(repository.unmuted) { "슬라이더를 올렸는데 음소거가 남아 있으면 소리가 안 난다" }
        }

    @Test
    fun `0으로 내릴 때는 음소거를 건드리지 않는다`() =
        runTest {
            repository.volume.value = MediaVolume(level = 7, max = 14, isMuted = false)

            SetMediaVolumeUseCase(repository)(fraction = 0f)

            assertEquals(0, repository.lastLevel)
            assertTrue(!repository.unmuted)
        }

    @Test
    fun `음소거 토글은 저장소에 위임한다`() =
        runTest {
            ToggleMuteUseCase(repository)()

            assertEquals(1, repository.toggleCount)
        }
}

private class FakeMediaVolumeRepository : MediaVolumeRepository {
    val volume = MutableStateFlow(MediaVolume(level = 0, max = 14, isMuted = false))
    var lastLevel: Int? = null
    var unmuted = false
    var toggleCount = 0

    override fun observeVolume(): Flow<MediaVolume> = volume

    override suspend fun setLevel(level: Int) {
        lastLevel = level
    }

    override suspend fun setMuted(muted: Boolean) {
        if (!muted) unmuted = true
    }

    override suspend fun toggleMute() {
        toggleCount++
    }
}
