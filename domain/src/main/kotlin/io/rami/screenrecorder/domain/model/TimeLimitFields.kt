package io.rami.screenrecorder.domain.model

/** 시간 제한 직접 입력의 칸 종류 (기능명세서 11.4절). */
enum class TimeLimitField {
    HOURS,
    MINUTES,
    SECONDS,
}

/**
 * 시간 제한 직접 입력 칸의 값 (기능명세서 11.4절).
 *
 * 어떤 값을 넣어도 칸별 범위(시 0~12, 분·초 0~59)로 맞춰 담는다. 그래서 증감 버튼은
 * 범위를 따로 확인하지 않고 [stepped]만 부르면 되고, 끝에 닿으면 그 자리에 머문다.
 * 자리 넘김은 하지 않는다 — 한 번 눌러 총 시간이 크게 뛰면 무엇이 바뀌었는지 알기 어렵다.
 *
 * 칸별 범위와 총합 검증([validate])은 별개다. 12시 30분은 칸별로는 넣을 수 있지만
 * 총합이 12시간을 넘어 저장이 막힌다.
 *
 * 홈 옵션 시트와 플로팅 버블이 같은 규칙을 써야 해서 domain에 둔다.
 */
class TimeLimitFields(
    hours: Int,
    minutes: Int,
    seconds: Int,
) {
    /** 시 칸 (0~12). */
    val hours: Int = hours.coerceIn(0, MAX_HOURS)

    /** 분 칸 (0~59). */
    val minutes: Int = minutes.coerceIn(0, MAX_MINUTES)

    /** 초 칸 (0~59). */
    val seconds: Int = seconds.coerceIn(0, MAX_SECONDS)

    /** [field]를 [delta]만큼 옮긴 새 값. 칸별 범위를 벗어나면 그 자리에 머문다. */
    fun stepped(
        field: TimeLimitField,
        delta: Int,
    ): TimeLimitFields =
        when (field) {
            TimeLimitField.HOURS -> TimeLimitFields(hours + delta, minutes, seconds)
            TimeLimitField.MINUTES -> TimeLimitFields(hours, minutes + delta, seconds)
            TimeLimitField.SECONDS -> TimeLimitFields(hours, minutes, seconds + delta)
        }

    /** [field] 칸의 현재 값. */
    fun valueOf(field: TimeLimitField): Int =
        when (field) {
            TimeLimitField.HOURS -> hours
            TimeLimitField.MINUTES -> minutes
            TimeLimitField.SECONDS -> seconds
        }

    /** [field] 칸만 [value]로 바꾼 새 값 (키보드 입력용). */
    fun withValue(
        field: TimeLimitField,
        value: Int,
    ): TimeLimitFields =
        when (field) {
            TimeLimitField.HOURS -> TimeLimitFields(value, minutes, seconds)
            TimeLimitField.MINUTES -> TimeLimitFields(hours, value, seconds)
            TimeLimitField.SECONDS -> TimeLimitFields(hours, minutes, value)
        }

    /** 총합이 허용 범위(10초~12시간) 안인지 검증한다. */
    fun validate(): TimeLimitInput = TimeLimit.fromHoursMinutesSeconds(hours, minutes, seconds)

    override fun equals(other: Any?): Boolean =
        other is TimeLimitFields &&
            hours == other.hours &&
            minutes == other.minutes &&
            seconds == other.seconds

    override fun hashCode(): Int = (hours * HASH_FACTOR + minutes) * HASH_FACTOR + seconds

    override fun toString(): String = "TimeLimitFields($hours, $minutes, $seconds)"

    companion object {
        /** 현재 설정값으로 칸을 채운다. 제한이 없으면 0에서 시작한다. */
        fun of(timeLimit: TimeLimit): TimeLimitFields =
            when (timeLimit) {
                is TimeLimit.None -> TimeLimitFields(0, 0, 0)
                is TimeLimit.Limited ->
                    timeLimit.duration.toComponents { hours, minutes, seconds, _ ->
                        TimeLimitFields(hours.toInt(), minutes, seconds)
                    }
            }

        /** 시 칸의 상한 — 총합 최대값과 같은 시간 수다. */
        val MAX_HOURS: Int = TimeLimit.MAX_DURATION.inWholeHours.toInt()

        /** 분 칸의 상한. 자리 넘김이 없으므로 60이 아니라 59에서 멈춘다. */
        const val MAX_MINUTES = 59

        /** 초 칸의 상한. */
        const val MAX_SECONDS = 59

        private const val HASH_FACTOR = 31
    }
}
