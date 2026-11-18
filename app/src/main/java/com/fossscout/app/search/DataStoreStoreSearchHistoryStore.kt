package com.fossscout.app.search

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.fossscout.core.matching.StoreSearchHistory
import com.fossscout.core.matching.StoreSearchHistoryCodec
import com.fossscout.core.matching.StoreSearchHistoryFormatException
import com.fossscout.core.matching.StoreSearchHistoryStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Local search history. Android backup excludes this DataStore file so recent
 * queries stay on the device.
 */
class DataStoreStoreSearchHistoryStore(
    private val dataStore: DataStore<Preferences>,
    private val codec: StoreSearchHistoryCodec = StoreSearchHistoryCodec(),
) : StoreSearchHistoryStore {
    private val enabledKey = booleanPreferencesKey("enabled")
    private val queriesKey = stringPreferencesKey("queries")

    override val history: Flow<StoreSearchHistory> = dataStore.data.map { preferences ->
        val queries = preferences[queriesKey]?.let { encoded ->
            try {
                codec.decode(encoded)
            } catch (_: StoreSearchHistoryFormatException) {
                emptyList()
            }
        }.orEmpty()
        StoreSearchHistory(
            enabled = preferences[enabledKey] ?: true,
            queries = queries,
        )
    }

    override suspend fun save(history: StoreSearchHistory) {
        dataStore.edit { preferences ->
            preferences[enabledKey] = history.enabled
            if (history.queries.isEmpty()) {
                preferences.remove(queriesKey)
            } else {
                preferences[queriesKey] = codec.encode(history.queries)
            }
        }
    }

    override suspend fun clearQueries() {
        dataStore.edit { preferences ->
            preferences.remove(queriesKey)
        }
    }
}
