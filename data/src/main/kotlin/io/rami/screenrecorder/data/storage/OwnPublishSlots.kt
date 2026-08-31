package io.rami.screenrecorder.data.storage

import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 이 프로세스가 만든 발행 자리의 id (기능명세서 6.1절 [결정]).
 *
 * 버려진 발행 정리의 1차 판정이다. 시각 비교는 다른 프로세스의 잔여물에만 쓰는 폴백이다.
 *
 * 기억을 [MediaStorePublishTarget] 인스턴스 필드로 두면 그 바인딩의 스코프에 정확성이 매달린다.
 * 실제로 `@Singleton` 이 빠져 있어 인스턴스가 매번 새로 만들어졌고, 발행기·정리기·압축 워커가
 * 각자 다른 기억을 들고 있어 판정이 항상 false 였다. 기억을 따로 두면 그 실수가 재발해도
 * 판정은 살아 있다.
 *
 * 발행 스레드와 정리 스레드가 함께 본다. 지우지 않는다 — 프로세스 수명 동안 만든 발행 수만큼만
 * 늘고, 지우면 확정에 실패해 남은 자리를 시각만으로 판정하게 된다.
 */
@Singleton
class OwnPublishSlots
    @Inject
    constructor() {
        private val ids = ConcurrentHashMap.newKeySet<Long>()

        fun remember(id: Long) {
            ids += id
        }

        fun contains(id: Long): Boolean = id in ids
    }
