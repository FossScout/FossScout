package com.fossscout.app

import android.content.Context
import android.os.Build
import androidx.datastore.preferences.preferencesDataStore
import com.fossscout.app.handoff.InventoryCompanionStoreDetector
import com.fossscout.app.inventory.PackageManagerInstalledAppSource
import com.fossscout.app.profile.ProfileFeatures
import com.fossscout.core.contribution.ContributionEnvironment
import com.fossscout.core.contribution.ContributionReportBuilder
import com.fossscout.core.data.DataStoreFeatureSettingsStore
import com.fossscout.core.data.DataStoreCollectionsStore
import com.fossscout.core.data.DataStoreFavouritesStore
import com.fossscout.core.data.DataStoreHandoffPreferencesStore
import com.fossscout.core.data.DataStoreInventoryStore
import com.fossscout.core.data.DataStoreMatchOverridesStore
import com.fossscout.core.data.DataStoreStoreRegistryStore
import com.fossscout.core.data.DataStoreSnapshotMetadataStore
import com.fossscout.core.data.CollectionsRepository
import com.fossscout.core.data.DefaultBackupService
import com.fossscout.core.data.FavouritesRepository
import com.fossscout.core.data.FeatureSettingsRepository
import com.fossscout.core.data.MatchOverridesRepository
import com.fossscout.core.data.ShareExportFactory
import com.fossscout.core.data.ShareImportService
import com.fossscout.core.data.StoreRegistryRepository
import com.fossscout.core.feature.Capability
import com.fossscout.core.feature.FeatureCatalog
import com.fossscout.core.feature.FeatureId
import com.fossscout.core.feature.FeatureSettingValue
import com.fossscout.core.feature.IndexCacheSettings
import com.fossscout.core.handoff.HandoffPreferencesRepository
import com.fossscout.core.index.FileSnapshotPayloadStore
import com.fossscout.core.index.IndexSyncRepository
import com.fossscout.core.index.UrlConnectionHttpFetcher
import com.fossscout.core.inventory.InstalledAppStateProvider
import com.fossscout.core.inventory.InventoryRepository
import com.fossscout.core.matching.MatchingRepository
import com.fossscout.core.matching.SnapshotCatalogSource
import java.io.File
import kotlinx.coroutines.flow.first

private val Context.favouritesDataStore by preferencesDataStore(name = "favourites")
private val Context.collectionsDataStore by preferencesDataStore(name = "collections")
private val Context.featureSettingsDataStore by preferencesDataStore(name = "feature_settings")
private val Context.storeRegistryDataStore by preferencesDataStore(name = "store_registry")
private val Context.inventoryDataStore by preferencesDataStore(name = "inventory")
private val Context.indexStateDataStore by preferencesDataStore(name = "index_state")
private val Context.handoffDataStore by preferencesDataStore(name = "handoff")
private val Context.matchOverridesDataStore by preferencesDataStore(name = "match_overrides")

/**
 * Composition root for app-wide dependencies. Android framework objects are
 * provisioned here and passed into view models by the UI layer.
 */
class AppContainer(context: Context) {
    private val packageInfo = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0)
    }.getOrNull()

    val appVersionName: String =
        packageInfo?.versionName?.takeIf(String::isNotBlank) ?: "unknown"

    @Suppress("DEPRECATION")
    private val legacyVersionCode: Long? = packageInfo?.versionCode?.toLong()

    val appVersionCode: Long? = (
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            packageInfo?.longVersionCode
        } else {
            legacyVersionCode
        }
    )?.takeIf { it >= 0 }

    val contributionEnvironment = ContributionEnvironment(
        appVersionName = appVersionName,
        appVersionCode = appVersionCode,
        profile = ProfileFeatures.profile.name,
    )

    val contributionReportBuilder = ContributionReportBuilder()

    val favouritesRepository = FavouritesRepository(
        DataStoreFavouritesStore(context.favouritesDataStore),
    )

    val collectionsRepository = CollectionsRepository(
        DataStoreCollectionsStore(context.collectionsDataStore),
    )

    val matchOverridesRepository = MatchOverridesRepository(
        DataStoreMatchOverridesStore(context.matchOverridesDataStore),
    )

    val shareExportFactory = ShareExportFactory()

    val shareImportService = ShareImportService(
        favouritesRepository = favouritesRepository,
        collectionsRepository = collectionsRepository,
    )

    val featureSettingsRepository = FeatureSettingsRepository(
        store = DataStoreFeatureSettingsStore(context.featureSettingsDataStore),
        profile = ProfileFeatures.profile,
        descriptors = FeatureCatalog.all,
    )

    val storeRegistryRepository = StoreRegistryRepository(
        DataStoreStoreRegistryStore(context.storeRegistryDataStore),
    )

    val handoffPreferencesRepository = HandoffPreferencesRepository(
        DataStoreHandoffPreferencesStore(context.handoffDataStore),
    )

    val backupService = DefaultBackupService(
        favouritesRepository = favouritesRepository,
        collectionsRepository = collectionsRepository,
        storeRegistryRepository = storeRegistryRepository,
        featureSettingsRepository = featureSettingsRepository,
        handoffPreferencesRepository = handoffPreferencesRepository,
        matchOverridesRepository = matchOverridesRepository,
        appVersion = { appVersionName },
    )

    private val snapshotPayloadStore = FileSnapshotPayloadStore(
        File(context.filesDir, "index-snapshots"),
    )

    val indexSyncRepository = IndexSyncRepository(
        metadataStore = DataStoreSnapshotMetadataStore(context.indexStateDataStore),
        payloadStore = snapshotPayloadStore,
        httpFetcher = UrlConnectionHttpFetcher(),
        tempDir = File(context.cacheDir, "index-sync"),
        storageBudgetBytes = {
            val settings = featureSettingsRepository.snapshot.first()
            val descriptor = FeatureCatalog.all
                .first { it.id == FeatureId.INDEX_CACHE }
            val setting = descriptor.settings
                .first { it.key == IndexCacheSettings.STORAGE_BUDGET_MB }
            val value = settings.valueFor(FeatureId.INDEX_CACHE, setting)
            val megabytes = (value as? FeatureSettingValue.IntegerValue)?.value
                ?: IndexCacheSettings.DEFAULT_STORAGE_BUDGET_MB
            megabytes.toLong() * 1024L * 1024L
        },
    )

    private val broadPackageVisibility =
        Capability.BROAD_PACKAGE_VISIBILITY in ProfileFeatures.profile.capabilities

    val installedAppSource = PackageManagerInstalledAppSource(
        context = context.applicationContext,
        supportsFullScan = broadPackageVisibility,
    )

    val companionStoreDetector = InventoryCompanionStoreDetector(installedAppSource)

    val installedStateProvider = InstalledAppStateProvider(installedAppSource)

    val matchingRepository = MatchingRepository(
        storeEntries = storeRegistryRepository.entries,
        snapshots = indexSyncRepository.snapshots,
        catalog = SnapshotCatalogSource(snapshotPayloadStore),
        overrides = matchOverridesRepository.overrides,
    )

    val inventoryRepository = InventoryRepository(
        store = DataStoreInventoryStore(context.inventoryDataStore),
        source = installedAppSource,
        classifier = matchingRepository,
        allowFullScan = broadPackageVisibility,
    )
}
