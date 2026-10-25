package com.fossscout.core.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.fossscout.core.handoff.HandoffPreferences
import com.fossscout.core.handoff.HandoffPreferencesStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class DataStoreHandoffPreferencesStore(
    private val dataStore: DataStore<Preferences>,
    private val key: Preferences.Key<String> = stringPreferencesKey("handoff_preferences"),
    private val codec: HandoffPreferencesCodec = HandoffPreferencesCodec(),
) : HandoffPreferencesStore {
    override val preferences: Flow<HandoffPreferences> =
        dataStore.data.map { preferences ->
            preferences[key]?.let(codec::decode) ?: HandoffPreferences()
        }

    override suspend fun save(preferences: HandoffPreferences) {
        dataStore.edit { current ->
            current[key] = codec.encode(preferences)
        }
    }
}
