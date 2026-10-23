package com.fossscout.app

import android.content.Context
import androidx.datastore.preferences.preferencesDataStore
import com.fossscout.app.profile.ProfileFeatures
import com.fossscout.core.data.DataStoreFeatureSettingsStore
import com.fossscout.core.data.DataStoreFavouritesStore
import com.fossscout.core.data.DataStoreStoreRegistryStore
import com.fossscout.core.data.FavouritesRepository
import com.fossscout.core.data.FeatureSettingsRepository
import com.fossscout.core.data.StoreRegistryRepository
import com.fossscout.core.feature.FeatureCatalog

private val Context.favouritesDataStore by preferencesDataStore(name = "favourites")
private val Context.featureSettingsDataStore by preferencesDataStore(name = "feature_settings")
private val Context.storeRegistryDataStore by preferencesDataStore(name = "store_registry")

/**
 * Composition root for app-wide dependencies. Android framework objects are
 * provisioned here and passed into view models by the UI layer.
 */
class AppContainer(context: Context) {
    val favouritesRepository = FavouritesRepository(
        DataStoreFavouritesStore(context.favouritesDataStore),
    )

    val featureSettingsRepository = FeatureSettingsRepository(
        store = DataStoreFeatureSettingsStore(context.featureSettingsDataStore),
        profile = ProfileFeatures.profile,
        descriptors = FeatureCatalog.all,
    )

    val storeRegistryRepository = StoreRegistryRepository(
        DataStoreStoreRegistryStore(context.storeRegistryDataStore),
    )
}
