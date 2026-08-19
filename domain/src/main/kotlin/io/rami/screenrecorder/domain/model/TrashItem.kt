package io.rami.screenrecorder.domain.model

/** 휴지통 항목 (기능명세서 9절: "N일 후 삭제" 표시). */
data class TrashItem(
    val recording: Recording,
    val daysUntilDeletion: Int,
)
