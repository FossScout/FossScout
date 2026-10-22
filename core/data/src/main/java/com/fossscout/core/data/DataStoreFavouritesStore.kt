package com.fossscout.core.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.fossscout.core.model.Favourite
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class DataStoreFavouritesStore(
    private val dataStore: DataStore<Preferences>,
    private val key: Preferences.Key<String> = stringPreferencesKey("favourites"),
) : FavouritesStore {
    private val json = Json { ignoreUnknownKeys = true }

    override val favourites: Flow<List<Favourite>> =
        dataStore.data.map { preferences ->
            val encoded = preferences[key] ?: return@map emptyList()
            runCatching { json.decodeFromString<List<Favourite>>(encoded) }
                .getOrDefault(emptyList())
        }

    override suspend fun save(favourites: List<Favourite>) {
        dataStore.edit { preferences ->
            preferences[key] = json.encodeToString(favourites)
        }
    }
}
