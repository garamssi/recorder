package io.rami.screenrecorder.data.recorder

import android.content.Intent
import javax.inject.Inject
import javax.inject.Singleton

/**
 * MediaProjection 동의 토큰의 메모리 전용 보관소 (CLAUDE.md 7절).
 *
 * Android 14+에서는 세션마다 동의를 새로 받아야 하므로 [consume]은 1회성이다.
 * 디스크·로그에 절대 기록하지 않는다.
 */
@Singleton
class MediaProjectionTokenHolder
    @Inject
    constructor() {
        private var token: Pair<Int, Intent>? = null

        /** 동의 결과를 보관한다. */
        fun store(
            resultCode: Int,
            data: Intent,
        ) {
            token = resultCode to data
        }

        /** 토큰을 꺼내고 비운다. 없으면 [IllegalStateException]. */
        fun consume(): Pair<Int, Intent> {
            val current = checkNotNull(token) { "MediaProjection 동의 토큰이 없다" }
            token = null
            return current
        }
    }
