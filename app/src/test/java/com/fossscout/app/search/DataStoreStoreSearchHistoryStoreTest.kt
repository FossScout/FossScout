package com.fossscout.app.search

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.fossscout.core.matching.StoreSearchHistory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DataStoreStoreSearchHistoryStoreTest {
    private val enabledKey = booleanPreferencesKey("enabled")
    private val queriesKey = stringPreferencesKey("queries")

    @Test
    fun `saved history round trips`() = runBlocking {
        val dataStore = FakeSearchHistoryDataStore(emptyPreferences())
        val store = DataStoreStoreSearchHistoryStore(dataStore)

        store.save(
            StoreSearchHistory(
                enabled = false,
                queries = listOf("first", "second"),
            ),
        )

        assertEquals(
            StoreSearchHistory(
                enabled = false,
                queries = listOf("first", "second"),
            ),
            store.history.first(),
        )
    }

    @Test
    fun `clearing queries keeps the enabled preference`() = runBlocking {
        val dataStore = FakeSearchHistoryDataStore(emptyPreferences())
        val store = DataStoreStoreSearchHistoryStore(dataStore)
        store.save(StoreSearchHistory(enabled = true, queries = listOf("first")))

        store.clearQueries()

        val history = store.history.first()
        assertTrue(history.enabled)
        assertEquals(emptyList<String>(), history.queries)
        assertNull(dataStore.data.first()[queriesKey])
    }

    @Test
    fun `malformed stored queries are ignored`() = runBlocking {
        val dataStore = FakeSearchHistoryDataStore(
            emptyPreferences().toMutablePreferences().apply {
                this[enabledKey] = false
                this[queriesKey] = "not json"
            },
        )

        assertEquals(
            StoreSearchHistory(enabled = false, queries = emptyList()),
            DataStoreStoreSearchHistoryStore(dataStore).history.first(),
        )
    }
}

private class FakeSearchHistoryDataStore(
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
