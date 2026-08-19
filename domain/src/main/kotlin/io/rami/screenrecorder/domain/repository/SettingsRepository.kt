package io.rami.screenrecorder.domain.repository

import io.rami.screenrecorder.domain.model.AppSettings
import kotlinx.coroutines.flow.Flow

/** 앱 설정 저장 경계 (기능명세서 4절: DataStore 저장, 즉시 반영). */
interface SettingsRepository {
    /** 현재 설정 스트림. 구독 즉시 최신 값을 방출한다. */
    val settings: Flow<AppSettings>

    /** [transform]으로 설정을 원자적으로 갱신한다. */
    suspend fun update(transform: (AppSettings) -> AppSettings)
}
