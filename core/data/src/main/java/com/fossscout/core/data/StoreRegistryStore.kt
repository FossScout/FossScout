package com.fossscout.core.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.fossscout.core.store.BuiltInStores
import com.fossscout.core.store.StoreRegistry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

interface StoreRegistryStore {
    val registry: Flow<StoreRegistry>

    suspend fun save(registry: StoreRegistry)
}

class DataStoreStoreRegistryStore(
    private val dataStore: DataStore<Preferences>,
    private val key: Preferences.Key<String> = stringPreferencesKey("store_registry"),
    private val codec: StoreRegistryCodec = StoreRegistryCodec(),
    private val defaultRegistry: () -> StoreRegistry = BuiltInStores::registry,
) : StoreRegistryStore {
    override val registry: Flow<StoreRegistry> = flow {
        initializeIfNeeded()
        emitAll(
            dataStore.data.map { preferences ->
                preferences[key]?.let(codec::decodeRegistry) ?: defaultRegistry()
            },
        )
    }

    override suspend fun save(registry: StoreRegistry) {
        dataStore.edit { preferences ->
            preferences[key] = codec.encodeRegistry(registry)
        }
    }

    private suspend fun initializeIfNeeded() {
        if (dataStore.data.first()[key] != null) return

        dataStore.edit { preferences ->
            if (preferences[key] == null) {
                preferences[key] = codec.encodeRegistry(defaultRegistry())
            }
        }
    }
}
