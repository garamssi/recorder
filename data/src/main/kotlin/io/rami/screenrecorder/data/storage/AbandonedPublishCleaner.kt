package io.rami.screenrecorder.data.storage

import android.util.Log

/**
 * 버려진 미완성 레코드를 회수한다 (기능명세서 6.1절 [결정]).
 *
 * 판정 기준은 "이 프로세스가 시작되기 전에 만들어졌는가" 하나다. 이 프로세스가 만든 레코드는
 * 발행 중이거나 이미 끝난 것이고, 그보다 앞선 미완성 레코드는 그것을 만든 프로세스가 이미 없다.
 *
 * 폴더와 `is_pending` 값만으로 판정하면 **진행 중인 압축 결과를 지운다** — 압축(명세 8절)은
 * 녹화본과 같은 폴더에 같은 방식으로 발행하고 WorkManager 잡이라 녹화 상태와 무관하게 돈다.
 * 프로세스 시작 시각 기준은 압축도 같은 규칙으로 덮으므로 별도 예외가 필요 없다.
 *
 * @param processStartedAtEpochSeconds 프로세스가 뜰 때 **한 번 재어 둔** 값이어야 한다.
 *   정리할 때마다 다시 재면 부팅 뒤 시계 보정으로 기준선이 앞으로 밀려 진행 중인 발행을 지운다.
 */
internal class AbandonedPublishCleaner(
    private val target: PublishTarget,
    private val processStartedAtEpochSeconds: Long,
) {
    /** @return 회수한 레코드 수. */
    fun discardAbandoned(): Int {
        val abandoned = target.listPending().filter { it.isAbandoned() }
        val discarded = abandoned.count { discardQuietly(it) }
        if (discarded > 0) Log.i(LOG_TAG, "버려진 발행 $discarded 건을 회수했다")
        return discarded
    }

    /**
     * 이 프로세스가 만들지 않았고, 이 프로세스보다 먼저 만들어졌는가.
     *
     * id 가 1차 판정이다. 시각 비교는 다른 프로세스의 잔여물에만 쓰는 폴백으로, 시계가
     * 보정되면 기준선이 밀리므로 그것만 믿으면 진행 중인 발행을 지운다 (기능명세서 6.1절 [결정]).
     */
    private fun PendingPublish.isAbandoned(): Boolean =
        !target.wasCreatedByThisProcess(slot) && createdAtEpochSeconds < processStartedAtEpochSeconds

    /**
     * 한 건이 실패해도 나머지는 계속 지운다 (기능명세서 6.1절 [결정]).
     *
     * 조기 회수일 뿐이라 한 건 때문에 전부 포기할 이유가 없다. 원인은 로그로 남긴다.
     */
    @Suppress("TooGenericExceptionCaught") // ContentResolver 는 보안·인자·DB 오류를 각기 다른 타입으로 던진다.
    private fun discardQuietly(entry: PendingPublish): Boolean =
        try {
            target.discard(entry.slot)
            true
        } catch (failure: Exception) {
            Log.w(LOG_TAG, "버려진 발행을 회수하지 못했다: ${entry.slot.uri}", failure)
            false
        }

    private companion object {
        const val LOG_TAG = "RecordingPublish"
    }
}

/**
 * 프로세스가 시작된 벽시계 시각(초).
 *
 * MediaStore 의 `DATE_ADDED` 가 벽시계 초 단위라 같은 축으로 환산해야 견줄 수 있다.
 * 세 시계를 섞고 초로 절삭하는 계산이라 실제로 틀리기 쉬운 곳이 여기다 — 그래서 순수 함수로 둔다.
 *
 * 절삭은 버림이므로 판정이 "덜 지우는" 쪽으로만 기운다.
 */
internal fun processStartEpochSeconds(
    nowEpochMillis: Long,
    elapsedRealtimeMillis: Long,
    processStartElapsedRealtimeMillis: Long,
): Long {
    val processAgeMillis = elapsedRealtimeMillis - processStartElapsedRealtimeMillis
    return (nowEpochMillis - processAgeMillis) / MILLIS_PER_SECOND
}

private const val MILLIS_PER_SECOND = 1_000L
