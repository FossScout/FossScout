package com.fossscout.core.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

interface FeatureSettingsStore {
    val disabled: Flow<Set<String>>

    suspend fun save(disabled: Set<String>)
}

class DataStoreFeatureSettingsStore(
    private val dataStore: DataStore<Preferences>,
    private val key: Preferences.Key<Set<String>> = stringSetPreferencesKey("disabled_features"),
) : FeatureSettingsStore {
    override val disabled: Flow<Set<String>> =
        dataStore.data.map { preferences -> preferences[key].orEmpty() }

    override suspend fun save(disabled: Set<String>) {
        dataStore.edit { preferences ->
            preferences[key] = disabled
        }
    }
}
