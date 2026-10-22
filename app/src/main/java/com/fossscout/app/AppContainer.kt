package com.fossscout.app

import android.content.Context
import androidx.datastore.preferences.preferencesDataStore
import com.fossscout.core.data.DataStoreFeatureSettingsStore
import com.fossscout.core.data.DataStoreFavouritesStore
import com.fossscout.core.data.FavouritesRepository
import com.fossscout.core.data.FeatureSettingsRepository

private val Context.favouritesDataStore by preferencesDataStore(name = "favourites")
private val Context.featureSettingsDataStore by preferencesDataStore(name = "feature_settings")

/**
 * Composition root for app-wide dependencies. Android framework objects are
 * provisioned here and passed into view models by the UI layer.
 */
class AppContainer(context: Context) {
    val favouritesRepository = FavouritesRepository(
        DataStoreFavouritesStore(context.favouritesDataStore),
    )

    val dataStoreFeatureSettingsStore =
        DataStoreFeatureSettingsStore(context.featureSettingsDataStore)

    val featureSettingsRepository = FeatureSettingsRepository(dataStoreFeatureSettingsStore)
}
