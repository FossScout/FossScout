package com.fossscout.core.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.fossscout.core.store.BuiltInStores
import com.fossscout.core.store.StoreRegistry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DataStoreStoreRegistryStoreTest {
    private val registryKey = stringPreferencesKey("store_registry")

    @Test
    fun `fresh registry is initialized with the predefined stores`() = runTest {
        val dataStore = FakeStorePreferencesDataStore(emptyPreferences())
        val store = DataStoreStoreRegistryStore(dataStore)

        val registry = store.registry.first()

        assertEquals(BuiltInStores.registry(), registry)
        assertNotNull(dataStore.data.first()[registryKey])
    }

    @Test
    fun `saved registry is read back`() = runTest {
        val dataStore = FakeStorePreferencesDataStore(emptyPreferences())
        val store = DataStoreStoreRegistryStore(dataStore)
        val custom = BuiltInStores.fdroid.copy(
            id = com.fossscout.core.model.StoreId("custom"),
            name = "Custom",
            priority = 2,
        )

        store.save(StoreRegistry(stores = BuiltInStores.registry().stores + custom))

        val registry = store.registry.first()
        assertEquals(custom, registry.stores.last())
        assertEquals(2, registry.stores.last().priority)
    }

    @Test
    fun `malformed stored data is rejected and not overwritten`() = runTest {
        val malformed = """{"schemaVersion":1,"stores":[{"broken":"""
        val dataStore = FakeStorePreferencesDataStore(
            emptyPreferences()
                .toMutablePreferences()
                .apply { this[registryKey] = malformed },
        )
        val store = DataStoreStoreRegistryStore(dataStore)

        val result = runCatching { store.registry.first() }

        assertTrue(result.exceptionOrNull() is StoreRegistryFormatException)
        assertEquals(malformed, dataStore.data.first()[registryKey])
    }

    @Test
    fun `future registry schema is rejected and not overwritten`() = runTest {
        val future = """{"schemaVersion":99,"stores":[]}"""
        val dataStore = FakeStorePreferencesDataStore(
            emptyPreferences()
                .toMutablePreferences()
                .apply { this[registryKey] = future },
        )
        val store = DataStoreStoreRegistryStore(dataStore)

        val result = runCatching { store.registry.first() }

        assertTrue(result.exceptionOrNull() is StoreRegistryFormatException)
        assertEquals(future, dataStore.data.first()[registryKey])
    }
}

private class FakeStorePreferencesDataStore(
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
