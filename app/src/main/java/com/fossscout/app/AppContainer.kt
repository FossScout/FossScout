package com.fossscout.app

import android.content.Context
import androidx.datastore.preferences.preferencesDataStore
import com.fossscout.core.data.DataStoreFavouritesStore
import com.fossscout.core.data.FavouritesRepository

private val Context.favouritesDataStore by preferencesDataStore(name = "favourites")

/**
 * Composition root for app-wide dependencies. Android framework objects are
 * provisioned here and passed into view models by the UI layer.
 */
class AppContainer(context: Context) {
    val favouritesRepository = FavouritesRepository(
        DataStoreFavouritesStore(context.favouritesDataStore),
    )
}
