package io.rami.screenrecorder.data.settings

import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import io.rami.screenrecorder.domain.model.AppSettings

/**
 * [AppSettings] <-> DataStore Preferences 매핑 (기능명세서 4절).
 *
 * 알 수 없는 값(손상/버전 변경)은 항목 단위로 기본값에 폴백한다.
 */
object AppSettingsMapper {
    /** 손상 값 폴백 테스트용으로 공개하는 키. */
    val KEY_THEME = stringPreferencesKey("theme")

    /** [preferences]를 설정으로 변환한다. 누락/손상 항목은 기본값. */
    fun fromPreferences(preferences: Preferences): AppSettings = TODO()

    /** [settings]를 Preferences로 직렬화한다. */
    fun toPreferences(settings: AppSettings): Preferences = TODO()
}
