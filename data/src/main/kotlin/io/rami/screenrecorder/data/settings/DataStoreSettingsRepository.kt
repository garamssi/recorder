package io.rami.screenrecorder.data.settings

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import io.rami.screenrecorder.domain.model.AppSettings
import io.rami.screenrecorder.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.settingsDataStore by preferencesDataStore(name = "settings")

/** [SettingsRepository]의 DataStore(Preferences) 구현 (기능명세서 4절: 즉시 반영). */
@Singleton
class DataStoreSettingsRepository
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : SettingsRepository {
        override val settings: Flow<AppSettings> =
            context.settingsDataStore.data.map(AppSettingsMapper::fromPreferences)

        override suspend fun update(transform: (AppSettings) -> AppSettings) {
            context.settingsDataStore.edit { preferences ->
                val updated = transform(AppSettingsMapper.fromPreferences(preferences))
                preferences.clear()
                preferences.plusAssign(AppSettingsMapper.toPreferences(updated))
            }
        }
    }
