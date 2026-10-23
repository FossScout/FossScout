package com.fossscout.core.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.fossscout.core.inventory.InventoryState
import com.fossscout.core.inventory.InventoryStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class DataStoreInventoryStore(
    private val dataStore: DataStore<Preferences>,
    private val key: Preferences.Key<String> = stringPreferencesKey("inventory"),
    private val codec: InventoryCodec = InventoryCodec(),
) : InventoryStore {
    override val state: Flow<InventoryState> =
        dataStore.data.map { preferences ->
            val encoded = preferences[key] ?: return@map InventoryState()
            codec.decode(encoded)
        }

    override suspend fun save(state: InventoryState) {
        dataStore.edit { preferences ->
            preferences[key] = codec.encode(state)
        }
    }
}
