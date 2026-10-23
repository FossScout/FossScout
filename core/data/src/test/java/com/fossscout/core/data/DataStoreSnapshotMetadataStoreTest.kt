package com.fossscout.core.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.fossscout.core.index.IndexState
import com.fossscout.core.index.SignatureStatus
import com.fossscout.core.index.StoreSnapshot
import com.fossscout.core.index.SyncJob
import com.fossscout.core.index.SyncState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DataStoreSnapshotMetadataStoreTest {
    private val key = stringPreferencesKey("index_state")

    @Test
    fun `fresh metadata is initialized with an empty versioned state`() = runTest {
        val dataStore = FakeSnapshotPreferencesDataStore(emptyPreferences())
        val store = DataStoreSnapshotMetadataStore(dataStore)

        val state = store.state.first()

        assertEquals(IndexState(), state)
        assertNotNull(dataStore.data.first()[key])
    }

    @Test
    fun `saved snapshots and jobs are read back`() = runTest {
        val dataStore = FakeSnapshotPreferencesDataStore(emptyPreferences())
        val store = DataStoreSnapshotMetadataStore(dataStore)
        val snapshot = StoreSnapshot(
            storeId = "store",
            fetchedAtEpochMillis = 1_000,
            indexTimestamp = 900,
            indexSha256 = "a".repeat(64),
            packageCount = 3,
            payloadPath = "store-900.ndjson",
            payloadSizeBytes = 256,
            signatureStatus = SignatureStatus.UNVERIFIED,
            sourceUrl = "https://repo.example/repo/index-v2.json",
        )
        val job = SyncJob(storeId = "store", state = SyncState.SUCCEEDED)

        store.save(IndexState(snapshots = mapOf("store" to snapshot), jobs = mapOf("store" to job)))

        val state = store.state.first()
        assertEquals(snapshot, state.snapshots["store"])
        assertEquals(job, state.jobs["store"])
    }

    @Test
    fun `malformed stored metadata is surfaced and not overwritten`() = runTest {
        val malformed = """{"schemaVersion":1,"snapshots":{"""
        val dataStore = FakeSnapshotPreferencesDataStore(
            emptyPreferences().toMutablePreferences().apply { this[key] = malformed },
        )
        val store = DataStoreSnapshotMetadataStore(dataStore)

        val result = runCatching { store.state.first() }

        assertTrue(result.exceptionOrNull() is IndexStateFormatException)
        assertEquals(malformed, dataStore.data.first()[key])
    }

    @Test
    fun `future metadata schema is surfaced and not overwritten`() = runTest {
        val future = """{"schemaVersion":99,"snapshots":{}}"""
        val dataStore = FakeSnapshotPreferencesDataStore(
            emptyPreferences().toMutablePreferences().apply { this[key] = future },
        )
        val store = DataStoreSnapshotMetadataStore(dataStore)

        val result = runCatching { store.state.first() }

        assertTrue(result.exceptionOrNull() is IndexStateFormatException)
        assertEquals(future, dataStore.data.first()[key])
    }
}

private class FakeSnapshotPreferencesDataStore(
    initial: Preferences,
) : DataStore<Preferences> {
    private val state = MutableStateFlow(initial)

    override val data: Flow<Preferences> = state

    override suspend fun updateData(
        transform: suspend (t: Preferences) -> Preferences,
    ): Preferences {
        val updated = transform(state.value)
        state.value = updated
        return updated
    }
}
