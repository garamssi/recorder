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

        /**
         * 쓰지 않기로 한 토큰을 버린다.
         *
         * 요청이 거절되면 동의만 소비되고 토큰이 남는다. 7절이 "메모리에서만 유지하고 세션마다
         * 새로 받는다" 고 했으므로 쓰이지 않은 것도 들고 있지 않는다.
         */
        fun clear() {
            token = null
        }

        /** 토큰을 꺼내고 비운다. 없으면 [IllegalStateException]. */
        fun consume(): Pair<Int, Intent> {
            val current = checkNotNull(token) { "MediaProjection 동의 토큰이 없다" }
            token = null
            return current
        }
    }
