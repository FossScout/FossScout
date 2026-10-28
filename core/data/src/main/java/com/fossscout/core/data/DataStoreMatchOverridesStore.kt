package com.fossscout.core.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.fossscout.core.model.MatchOverride
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class DataStoreMatchOverridesStore(
    private val dataStore: DataStore<Preferences>,
    private val key: Preferences.Key<String> = stringPreferencesKey("match_overrides"),
    private val codec: MatchOverridesCodec = MatchOverridesCodec(),
) : MatchOverridesStore {
    override val overrides: Flow<List<MatchOverride>> =
        dataStore.data.map { preferences ->
            val encoded = preferences[key] ?: return@map emptyList()
            codec.decode(encoded)
        }

    override suspend fun save(overrides: List<MatchOverride>) {
        dataStore.edit { preferences ->
            preferences[key] = codec.encode(overrides)
        }
    }
}
