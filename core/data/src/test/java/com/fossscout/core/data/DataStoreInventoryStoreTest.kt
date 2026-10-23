package com.fossscout.core.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.fossscout.core.inventory.InventorySnapshot
import com.fossscout.core.inventory.InventoryState
import com.fossscout.core.inventory.ScanMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class DataStoreInventoryStoreTest {
    private val key = stringPreferencesKey("inventory")

    @Test
    fun `fresh store has no snapshot`() = runTest {
        val store = DataStoreInventoryStore(FakeInventoryPreferencesDataStore(emptyPreferences()))

        assertEquals(InventoryState(), store.state.first())
    }

    @Test
    fun `save persists the snapshot and hide list in the versioned envelope`() = runTest {
        val dataStore = FakeInventoryPreferencesDataStore(emptyPreferences())
        val store = DataStoreInventoryStore(dataStore)
        val state = InventoryState(
            snapshot = InventorySnapshot(
                scanMode = ScanMode.MANUAL,
                capturedAtEpochMillis = 77L,
            ),
            hiddenPackages = setOf("org.example.hidden"),
        )

        store.save(state)

        assertEquals(state, store.state.first())
        val encoded = dataStore.data.first()[key]
        assertNotNull(encoded)
        assertEquals(state, InventoryCodec().decode(encoded!!))
    }

    @Test
    fun `malformed stored data raises instead of looking empty`() {
        val dataStore = FakeInventoryPreferencesDataStore(
            emptyPreferences().toMutablePreferences().apply {
                this[key] = """{"schemaVersion":1,"state":["""
            },
        )
        val store = DataStoreInventoryStore(dataStore)

        assertThrows(InventoryFormatException::class.java) {
            runBlocking { store.state.first() }
        }
    }

    @Test
    fun `saved snapshot survives a new store instance`() = runTest {
        val dataStore = FakeInventoryPreferencesDataStore(emptyPreferences())
        val first = DataStoreInventoryStore(dataStore)
        first.save(
            InventoryState(
                snapshot = InventorySnapshot(
                    scanMode = ScanMode.FULL,
                    capturedAtEpochMillis = 100L,
                ),
            ),
        )

        val second = DataStoreInventoryStore(dataStore)

        assertEquals(100L, second.state.first().snapshot?.capturedAtEpochMillis)
        assertNull(second.state.first().snapshot?.entries?.firstOrNull())
    }
}

private class FakeInventoryPreferencesDataStore(
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
