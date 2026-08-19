package io.rami.screenrecorder.domain.repository

import kotlinx.coroutines.flow.Flow

/** 저장 공간 조회 경계 (기능명세서 2.1, 11.1절). */
interface StorageRepository {
    /** 저장 위치의 사용 가능 바이트 스트림. */
    fun observeAvailableBytes(): Flow<Long>
}
