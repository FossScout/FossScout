package com.fossscout.core.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.fossscout.core.model.AppKey
import com.fossscout.core.model.Favourite
import com.fossscout.core.model.StoreId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Test

class DataStoreFavouritesStoreTest {
    private val key = stringPreferencesKey("favourites")

    @Test
    fun `fresh store has no favourites`() = runTest {
        val store = DataStoreFavouritesStore(FakeFavouritesPreferencesDataStore(emptyPreferences()))

        assertEquals(emptyList<Favourite>(), store.favourites.first())
    }

    @Test
    fun `tags and preferred store survive a new store instance`() = runTest {
        val dataStore = FakeFavouritesPreferencesDataStore(emptyPreferences())
        val favourite = Favourite(
            appKey = AppKey("org.example"),
            displayName = "Example",
            note = "keep this",
            tags = setOf("privacy", "tools"),
            preferredStoreId = StoreId("fdroid"),
            createdAtEpochMillis = 1L,
            updatedAtEpochMillis = 2L,
        )

        DataStoreFavouritesStore(dataStore).save(listOf(favourite))

        val recreated = DataStoreFavouritesStore(dataStore)
        assertEquals(listOf(favourite), recreated.favourites.first())
        assertNotNull(dataStore.data.first()[key])
    }

    @Test
    fun `malformed stored data raises instead of looking empty`() {
        val dataStore = FakeFavouritesPreferencesDataStore(
            emptyPreferences().toMutablePreferences().apply {
                this[key] = """{"schemaVersion":1,"favourites":["""
            },
        )
        val store = DataStoreFavouritesStore(dataStore)

        assertThrows(FavouritesFormatException::class.java) {
            runBlocking { store.favourites.first() }
        }
    }
}

private class FakeFavouritesPreferencesDataStore(
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
