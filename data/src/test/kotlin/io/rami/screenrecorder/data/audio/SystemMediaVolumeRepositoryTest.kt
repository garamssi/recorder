package io.rami.screenrecorder.data.audio

import app.cash.turbine.test
import io.rami.screenrecorder.domain.model.MediaVolume
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * 시스템 미디어 볼륨 저장소 (기능명세서 10절).
 *
 * 음소거 상태는 Settings에 기록되지 않아 외부 변경 알림이 오지 않는다.
 * 앱이 직접 바꾼 경우에는 저장소가 스스로 다시 읽어 UI가 낡은 상태로 남지 않게 한다.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SystemMediaVolumeRepositoryTest {
    private val gateway = FakeSystemVolumeGateway()
    private val repository = SystemMediaVolumeRepository(gateway)

    @Test
    fun `구독 시 현재 볼륨을 즉시 알린다`() =
        runTest {
            gateway.current = MediaVolume(level = 4, max = 15, isMuted = false)

            repository.observeVolume().test {
                assertEquals(MediaVolume(4, 15, false), awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `외부 변경 알림이 오면 다시 읽어 알린다`() =
        runTest {
            gateway.current = MediaVolume(level = 4, max = 15, isMuted = false)

            repository.observeVolume().test {
                awaitItem()
                gateway.current = MediaVolume(level = 9, max = 15, isMuted = false)
                gateway.emitExternalChange()

                assertEquals(MediaVolume(9, 15, false), awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `음소거를 토글하면 알림 없이도 다시 읽어 알린다`() =
        runTest {
            gateway.current = MediaVolume(level = 4, max = 15, isMuted = false)

            repository.observeVolume().test {
                awaitItem()
                repository.toggleMute()

                assertEquals(MediaVolume(4, 15, true), awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `단계를 바꾸면 다시 읽어 알린다`() =
        runTest {
            gateway.current = MediaVolume(level = 4, max = 15, isMuted = false)

            repository.observeVolume().test {
                awaitItem()
                repository.setLevel(11)

                assertEquals(MediaVolume(11, 15, false), awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `음소거 해제를 요청하면 다시 읽어 알린다`() =
        runTest {
            gateway.current = MediaVolume(level = 4, max = 15, isMuted = true)

            repository.observeVolume().test {
                awaitItem()
                repository.setMuted(false)

                assertEquals(MediaVolume(4, 15, false), awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }
}

private class FakeSystemVolumeGateway : SystemVolumeGateway {
    var current = MediaVolume(level = 0, max = 15, isMuted = false)
    private val external = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    suspend fun emitExternalChange() = external.emit(Unit)

    override fun read(): MediaVolume = current

    override fun setLevel(level: Int) {
        current = MediaVolume(level = level, max = current.max, isMuted = current.isMuted)
    }

    override fun setMuted(muted: Boolean) {
        current = MediaVolume(level = current.level, max = current.max, isMuted = muted)
    }

    override fun toggleMute() = setMuted(!current.isMuted)

    override fun changes(): Flow<Unit> = external
}
