package com.fossscout.core.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.fossscout.core.model.AppKey
import com.fossscout.core.model.MatchOverride
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Test

class DataStoreMatchOverridesStoreTest {
    private val key = stringPreferencesKey("match_overrides")
    private val override = MatchOverride(
        installedAppKey = AppKey("org.example.app"),
        note = "private",
        createdAtEpochMillis = 1,
        updatedAtEpochMillis = 2,
    )

    @Test
    fun `fresh store has no overrides`() = runTest {
        val store = DataStoreMatchOverridesStore(
            FakeMatchOverridesPreferencesDataStore(emptyPreferences()),
        )

        assertEquals(emptyList<MatchOverride>(), store.overrides.first())
    }

    @Test
    fun `overrides survive a new store instance`() = runTest {
        val dataStore = FakeMatchOverridesPreferencesDataStore(emptyPreferences())
        DataStoreMatchOverridesStore(dataStore).save(listOf(override))

        val recreated = DataStoreMatchOverridesStore(dataStore)

        assertEquals(listOf(override), recreated.overrides.first())
        assertNotNull(dataStore.data.first()[key])
    }

    @Test
    fun `malformed stored data raises instead of looking empty`() {
        val dataStore = FakeMatchOverridesPreferencesDataStore(
            emptyPreferences().toMutablePreferences().apply {
                this[key] = """{"schemaVersion":1,"overrides":["""
            },
        )
        val store = DataStoreMatchOverridesStore(dataStore)

        assertThrows(MatchOverridesFormatException::class.java) {
            runBlocking { store.overrides.first() }
        }
    }
}

private class FakeMatchOverridesPreferencesDataStore(
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
