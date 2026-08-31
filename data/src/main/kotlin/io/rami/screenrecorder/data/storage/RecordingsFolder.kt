package io.rami.screenrecorder.data.storage

/**
 * 녹화본이 저장되는 MediaStore 상대 경로 (기능명세서 6.1절).
 *
 * 발행·조회·압축이 모두 이 값을 써야 한다. 각자 사본을 들면 폴더를 옮기는 순간
 * 발행 위치와 조회 위치가 갈라지고, 그 어긋남은 조용히 "녹화본이 안 보인다"로만 드러난다.
 */
internal const val RECORDINGS_RELATIVE_PATH = "Movies/ScreenRecorder"
