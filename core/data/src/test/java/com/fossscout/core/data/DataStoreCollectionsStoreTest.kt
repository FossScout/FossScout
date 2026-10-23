package com.fossscout.core.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.fossscout.core.model.AppCollection
import com.fossscout.core.model.AppKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Test

class DataStoreCollectionsStoreTest {
    private val key = stringPreferencesKey("collections")

    @Test
    fun `fresh store has no collections`() = runTest {
        val store = DataStoreCollectionsStore(FakeCollectionsPreferencesDataStore(emptyPreferences()))

        assertEquals(emptyList<AppCollection>(), store.collections.first())
    }

    @Test
    fun `ordered items survive a new store instance`() = runTest {
        val dataStore = FakeCollectionsPreferencesDataStore(emptyPreferences())
        val collection = AppCollection(
            id = "tools",
            name = "Tools",
            description = "Useful apps",
            itemKeys = listOf(AppKey("b"), AppKey("a")),
            createdAtEpochMillis = 1L,
            updatedAtEpochMillis = 2L,
        )

        DataStoreCollectionsStore(dataStore).save(listOf(collection))

        val recreated = DataStoreCollectionsStore(dataStore)
        assertEquals(listOf(collection), recreated.collections.first())
        assertEquals(
            listOf("b", "a"),
            recreated.collections.first().single().itemKeys.map(AppKey::value),
        )
        assertNotNull(dataStore.data.first()[key])
    }

    @Test
    fun `malformed stored data raises instead of looking empty`() {
        val dataStore = FakeCollectionsPreferencesDataStore(
            emptyPreferences().toMutablePreferences().apply {
                this[key] = """{"schemaVersion":1,"collections":["""
            },
        )
        val store = DataStoreCollectionsStore(dataStore)

        assertThrows(CollectionsFormatException::class.java) {
            runBlocking { store.collections.first() }
        }
    }
}

private class FakeCollectionsPreferencesDataStore(
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
