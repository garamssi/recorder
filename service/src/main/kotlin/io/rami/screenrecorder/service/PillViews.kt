package io.rami.screenrecorder.service

import android.view.View
import android.widget.TextView

/**
 * pill을 만들고 나온 참조들.
 *
 * @param elapsed 경과 시간만 갱신할 때 쓰는 뷰.
 * @param handle 드래그 손잡이 겸 펼침 토글이 되는 영역 (REC 점 + 경과 시간).
 *   제어 버튼과 분리해야 버튼 탭과 드래그가 서로 방해하지 않는다.
 */
internal class PillViews(
    val elapsed: TextView,
    val handle: View,
)
