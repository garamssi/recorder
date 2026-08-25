package io.rami.screenrecorder.service

import android.view.View
import android.widget.EditText
import android.widget.TextView
import io.rami.screenrecorder.domain.model.TimeLimitField

/**
 * 한 칸(시/분/초)을 이루는 뷰들 — 증가 버튼, 입력 칸, 감소 버튼.
 *
 * @param root 세 뷰와 단위 라벨을 세로로 담은 열.
 */
internal class TimeLimitFieldViews(
    val field: TimeLimitField,
    val input: EditText,
    val increase: View,
    val decrease: View,
    val root: View,
)

/**
 * 시간 제한 입력 창을 만들고 나온 참조들.
 *
 * 오버레이는 Compose를 쓰지 않아 상태를 되물을 곳이 없으므로, 검증 테스트가 값을 넣고
 * 결과를 확인할 수 있도록 조작 대상 뷰를 그대로 노출한다 ([PillViews]와 같은 이유).
 */
internal class TimeLimitInputViews(
    val root: View,
    val columns: List<TimeLimitFieldViews>,
    val error: TextView,
    val buttons: TimeLimitInputButtons,
) {
    /** 시 입력 칸. */
    val hours: EditText get() = inputOf(TimeLimitField.HOURS)

    /** 분 입력 칸. */
    val minutes: EditText get() = inputOf(TimeLimitField.MINUTES)

    /** 초 입력 칸. */
    val seconds: EditText get() = inputOf(TimeLimitField.SECONDS)

    /** 저장 버튼 — 입력이 유효할 때만 눌린다. */
    val confirm: TextView get() = buttons.confirm

    /** 제한 해제 버튼. */
    val clear: TextView get() = buttons.clear

    /** 취소 버튼 — 값을 바꾸지 않고 닫는다. */
    val cancel: TextView get() = buttons.cancel

    /** [field] 칸을 1 올리는 버튼. */
    fun stepUp(field: TimeLimitField): View = columnOf(field).increase

    /** [field] 칸을 1 내리는 버튼. */
    fun stepDown(field: TimeLimitField): View = columnOf(field).decrease

    private fun inputOf(field: TimeLimitField): EditText = columnOf(field).input

    private fun columnOf(field: TimeLimitField): TimeLimitFieldViews = columns.first { it.field == field }
}

/** 입력 창 하단의 버튼 세 개. */
internal class TimeLimitInputButtons(
    val clear: TextView,
    val cancel: TextView,
    val confirm: TextView,
) {
    /** 담긴 순서대로 — 제한 없음, 취소, 저장. */
    fun asList(): List<TextView> = listOf(clear, cancel, confirm)
}
