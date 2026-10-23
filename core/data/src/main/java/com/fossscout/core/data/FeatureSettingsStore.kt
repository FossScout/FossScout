package com.fossscout.core.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.fossscout.core.feature.FeatureSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

interface FeatureSettingsStore {
    val settings: Flow<FeatureSettings>

    suspend fun save(settings: FeatureSettings)
}

class DataStoreFeatureSettingsStore(
    private val dataStore: DataStore<Preferences>,
    private val key: Preferences.Key<String> = stringPreferencesKey("feature_settings"),
    private val legacyDisabledKey: Preferences.Key<Set<String>> =
        stringSetPreferencesKey("disabled_features"),
    private val codec: FeatureSettingsCodec = FeatureSettingsCodec(),
) : FeatureSettingsStore {
    override val settings: Flow<FeatureSettings> = flow {
        migrateLegacyIfNeeded()
        emitAll(dataStore.data.map(::decode))
    }

    override suspend fun save(settings: FeatureSettings) {
        dataStore.edit { preferences ->
            preferences[key] = codec.encode(settings)
            preferences.remove(legacyDisabledKey)
        }
    }

    private suspend fun migrateLegacyIfNeeded() {
        if (dataStore.data.first()[key] != null) return

        dataStore.edit { preferences ->
            if (preferences[key] == null) {
                preferences[key] = codec.encode(
                    migrateLegacyDisabledFeatures(
                        preferences[legacyDisabledKey].orEmpty(),
                    ),
                )
                preferences.remove(legacyDisabledKey)
            }
        }
    }

    private fun decode(preferences: Preferences): FeatureSettings {
        val encoded = preferences[key]
            ?: return migrateLegacyDisabledFeatures(
                preferences[legacyDisabledKey].orEmpty(),
            )
        return codec.decode(encoded)
    }
}
