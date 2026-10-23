package com.fossscout.core.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.fossscout.core.index.IndexState
import com.fossscout.core.index.SnapshotMetadataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

class DataStoreSnapshotMetadataStore(
    private val dataStore: DataStore<Preferences>,
    private val key: Preferences.Key<String> = stringPreferencesKey("index_state"),
    private val codec: IndexStateCodec = IndexStateCodec(),
    private val defaultState: () -> IndexState = { IndexState() },
) : SnapshotMetadataStore {
    override val state: Flow<IndexState> = flow {
        initializeIfNeeded()
        emitAll(
            dataStore.data.map { preferences ->
                preferences[key]?.let(codec::decode) ?: defaultState()
            },
        )
    }

    override suspend fun save(state: IndexState) {
        dataStore.edit { preferences ->
            preferences[key] = codec.encode(state)
        }
    }

    private suspend fun initializeIfNeeded() {
        if (dataStore.data.first()[key] != null) return
        dataStore.edit { preferences ->
            if (preferences[key] == null) {
                preferences[key] = codec.encode(defaultState())
            }
        }
    }
}
