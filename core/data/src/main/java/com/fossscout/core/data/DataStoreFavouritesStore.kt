package com.fossscout.core.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.fossscout.core.model.Favourite
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class DataStoreFavouritesStore(
    private val dataStore: DataStore<Preferences>,
    private val key: Preferences.Key<String> = stringPreferencesKey("favourites"),
    private val codec: FavouritesCodec = FavouritesCodec(),
) : FavouritesStore {
    override val favourites: Flow<List<Favourite>> =
        dataStore.data.map { preferences ->
            val encoded = preferences[key] ?: return@map emptyList()
            codec.decode(encoded)
        }

    override suspend fun save(favourites: List<Favourite>) {
        dataStore.edit { preferences ->
            preferences[key] = codec.encode(favourites)
        }
    }
}
