package com.fossscout.core.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.fossscout.core.model.AppCollection
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class DataStoreCollectionsStore(
    private val dataStore: DataStore<Preferences>,
    private val key: Preferences.Key<String> = stringPreferencesKey("collections"),
    private val codec: CollectionsCodec = CollectionsCodec(),
) : CollectionsStore {
    override val collections: Flow<List<AppCollection>> =
        dataStore.data.map { preferences ->
            val encoded = preferences[key] ?: return@map emptyList()
            codec.decode(encoded)
        }

    override suspend fun save(collections: List<AppCollection>) {
        dataStore.edit { preferences ->
            preferences[key] = codec.encode(collections)
        }
    }
}
