package io.rami.screenrecorder.data.audio

import android.content.Context
import android.database.ContentObserver
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import dagger.hilt.android.qualifiers.ApplicationContext
import io.rami.screenrecorder.domain.model.MediaVolume
import io.rami.screenrecorder.domain.repository.MediaVolumeRepository
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onStart
import javax.inject.Inject
import javax.inject.Singleton

/** 시스템 미디어 볼륨 읽기·쓰기 경계. [AudioManager]를 감싸 테스트에서 페이크로 대체한다. */
interface SystemVolumeGateway {
    /** 현재 미디어 스트림 볼륨. */
    fun read(): MediaVolume

    /** 볼륨 단계를 설정한다. */
    fun setLevel(level: Int)

    /** 음소거 여부를 설정한다. */
    fun setMuted(muted: Boolean)

    /** 음소거 상태를 뒤집는다. */
    fun toggleMute()

    /** 앱 밖에서 볼륨이 바뀐 시점 알림 (하드웨어 볼륨 키 등). */
    fun changes(): Flow<Unit>
}

/**
 * [SystemVolumeGateway] 기반 미디어 볼륨 저장소 (기능명세서 10절).
 *
 * 플레이어 자체 볼륨을 두지 않고 STREAM_MUSIC 에 연동하므로 하드웨어 볼륨 키로 바꾼 값도
 * 그대로 반영된다. 음소거 상태는 Settings 에 기록되지 않아 외부 변경 알림이 오지 않으므로,
 * 앱이 직접 바꾼 경우에는 스스로 다시 읽어 UI 가 낡은 상태로 남지 않게 한다.
 */
@Singleton
class SystemMediaVolumeRepository
    @Inject
    constructor(
        private val gateway: SystemVolumeGateway,
    ) : MediaVolumeRepository {
        private val selfChanges = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

        override fun observeVolume(): Flow<MediaVolume> =
            merge(gateway.changes(), selfChanges)
                .onStart { emit(Unit) }
                .map { gateway.read() }
                .conflate()
                .distinctUntilChanged()

        override suspend fun setLevel(level: Int) {
            gateway.setLevel(level)
            selfChanges.emit(Unit)
        }

        override suspend fun setMuted(muted: Boolean) {
            gateway.setMuted(muted)
            selfChanges.emit(Unit)
        }

        override suspend fun toggleMute() {
            gateway.toggleMute()
            selfChanges.emit(Unit)
        }
    }

/**
 * [AudioManager] 기반 [SystemVolumeGateway].
 *
 * 볼륨 변경 방송은 공개 API가 아니므로 Settings 변경을 관찰해 다시 읽는다.
 */
@Singleton
class AudioManagerVolumeGateway
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : SystemVolumeGateway {
        private val audioManager = context.getSystemService(AudioManager::class.java)

        override fun read(): MediaVolume =
            MediaVolume(
                level = audioManager.getStreamVolume(STREAM),
                max = audioManager.getStreamMaxVolume(STREAM),
                isMuted = audioManager.isStreamMute(STREAM),
            )

        override fun setLevel(level: Int) {
            audioManager.setStreamVolume(STREAM, level, 0)
        }

        override fun setMuted(muted: Boolean) {
            val direction = if (muted) AudioManager.ADJUST_MUTE else AudioManager.ADJUST_UNMUTE
            audioManager.adjustStreamVolume(STREAM, direction, 0)
        }

        override fun toggleMute() {
            audioManager.adjustStreamVolume(STREAM, AudioManager.ADJUST_TOGGLE_MUTE, 0)
        }

        override fun changes(): Flow<Unit> =
            callbackFlow {
                val observer =
                    object : ContentObserver(Handler(Looper.getMainLooper())) {
                        override fun onChange(selfChange: Boolean) {
                            trySend(Unit)
                        }
                    }
                context.contentResolver.registerContentObserver(
                    Settings.System.CONTENT_URI,
                    true,
                    observer,
                )
                awaitClose { context.contentResolver.unregisterContentObserver(observer) }
            }

        private companion object {
            /** 플레이어 재생음은 미디어 스트림이다. */
            const val STREAM = AudioManager.STREAM_MUSIC
        }
    }
