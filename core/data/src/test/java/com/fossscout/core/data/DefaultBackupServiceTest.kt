package com.fossscout.core.data

import com.fossscout.core.backup.BackupConflictMode
import com.fossscout.core.backup.BackupEncryptionException
import com.fossscout.core.backup.BackupFormatException
import com.fossscout.core.feature.BuildProfile
import com.fossscout.core.feature.FeatureCatalog
import com.fossscout.core.feature.FeatureId
import com.fossscout.core.feature.FeatureSettingValue
import com.fossscout.core.feature.FeatureSettings
import com.fossscout.core.feature.IndexCacheSettings
import com.fossscout.core.handoff.HandoffPreferences
import com.fossscout.core.handoff.HandoffPreferencesRepository
import com.fossscout.core.handoff.HandoffPreferencesStore
import com.fossscout.core.model.AppCollection
import com.fossscout.core.model.AppKey
import com.fossscout.core.model.Favourite
import com.fossscout.core.model.MatchOverride
import com.fossscout.core.model.MatchOverrideTarget
import com.fossscout.core.model.StoreId
import com.fossscout.core.store.AdapterType
import com.fossscout.core.store.BuiltInStores
import com.fossscout.core.store.StoreDescriptor
import com.fossscout.core.store.StoreRegistry
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultBackupServiceTest {
    private val favourite = Favourite(
        appKey = AppKey("org.example.app"),
        displayName = "Example",
        preferredStoreId = StoreId("custom-repo"),
        note = "private note",
        tags = setOf("tools"),
        createdAtEpochMillis = 10,
        updatedAtEpochMillis = 20,
    )
    private val collection = AppCollection(
        id = "collection-1",
        name = "Utilities",
        itemKeys = listOf(AppKey("org.example.app")),
        createdAtEpochMillis = 10,
        updatedAtEpochMillis = 20,
    )
    private val customStore = StoreDescriptor(
        id = StoreId("custom-repo"),
        name = "Custom repo",
        homepage = "https://example.org",
        adapterType = AdapterType.FDROID_V2,
        indexUrl = "https://example.org/repo",
        fingerprint = "a".repeat(64),
        trustNote = "Pinned",
        priority = 2,
    )
    private val settings = FeatureSettings(
        disabledFeatureIds = setOf(FeatureId.STANDALONE),
        values = mapOf(
            FeatureId.INDEX_CACHE to mapOf(
                IndexCacheSettings.STORAGE_BUDGET_MB to
                    FeatureSettingValue.IntegerValue(512),
            ),
        ),
    )
    private val handoff = HandoffPreferences(
        preferredStores = mapOf("org.example.app" to "fdroid"),
    )
    private val matchOverride = MatchOverride(
        installedAppKey = AppKey("org.example.app"),
        target = MatchOverrideTarget(
            appKey = AppKey("org.example.alt"),
            displayName = "Alternative",
            storeId = StoreId("custom-repo"),
            storeName = "Custom repo",
        ),
        note = "private correction",
        createdAtEpochMillis = 10,
        updatedAtEpochMillis = 20,
    )

    @Test
    fun `clean install restore reproduces the full local profile`() = runTest {
        val source = service(
            favourites = FakeFavouritesStore(listOf(favourite)),
            collections = FakeCollectionsStore(listOf(collection)),
            registry = FakeBackupStoreRegistryStore(registryWithCustomStore()),
            settings = FakeBackupFeatureSettingsStore(settings),
            handoff = FakeBackupHandoffStore(handoff),
            overrides = FakeMatchOverridesStore(listOf(matchOverride)),
        )
        val target = service()
        val envelope = source.service.prepareExport()
        val encoded = source.service.encodeExport(envelope, null)

        val decoded = target.service.decodeImport(encoded)
        val plan = target.service.preview(decoded, BackupConflictMode.REPLACE)
        target.service.apply(plan)

        assertEquals(listOf(favourite), target.favourites.favourites.first())
        assertEquals(listOf(collection), target.collections.collections.first())
        assertEquals(
            "a".repeat(64),
            target.stores.entries.first().last().descriptor.fingerprint,
        )
        assertEquals(settings, target.settings.snapshot.first())
        assertEquals(handoff, target.handoff.preferences.first())
        assertEquals(listOf(matchOverride), target.overrides.overrides.first())
        assertEquals(1, plan.preview.notesItemCount)
    }

    @Test
    fun `encrypted backup round trips through the service`() = runTest {
        val source = service(favourites = FakeFavouritesStore(listOf(favourite)))
        val target = service()
        val envelope = source.service.prepareExport()
        val encrypted = source.service.encodeExport(
            envelope,
            "correct horse".toCharArray(),
        )

        val decoded = target.service.decodeImport(
            encrypted,
            "correct horse".toCharArray(),
        )
        target.service.apply(
            target.service.preview(decoded, BackupConflictMode.REPLACE),
        )

        assertEquals(listOf(favourite), target.favourites.favourites.first())
    }

    @Test
    fun `wrong passphrase fails before any data changes`() = runTest {
        val source = service(favourites = FakeFavouritesStore(listOf(favourite)))
        val target = service()
        val envelope = source.service.prepareExport()
        val encrypted = source.service.encodeExport(
            envelope,
            "correct horse".toCharArray(),
        )

        val result = runCatching {
            target.service.decodeImport(encrypted, "wrong horse".toCharArray())
        }
        assertTrue(result.exceptionOrNull() is BackupEncryptionException)
        assertTrue(target.favourites.favourites.first().isEmpty())
    }

    @Test
    fun `invalid file fails before any data changes`() = runTest {
        val target = service()

        val result = runCatching { target.service.decodeImport("{not-json") }
        assertTrue(result.exceptionOrNull() is BackupFormatException)
        assertTrue(target.favourites.favourites.first().isEmpty())
        assertTrue(target.collections.collections.first().isEmpty())
    }

    @Test
    fun `failed apply rolls back earlier writes`() = runTest {
        val localFavourite = favourite.copy(
            appKey = AppKey("org.local.app"),
            displayName = "Local",
            note = "local note",
        )
        val source = service(
            favourites = FakeFavouritesStore(listOf(favourite)),
            collections = FakeCollectionsStore(listOf(collection)),
        )
        val target = service(
            favourites = FakeFavouritesStore(listOf(localFavourite)),
            collections = FailingCollectionsStore(
                listOf(collection.copy(id = "local-collection")),
            ),
        )
        val envelope = source.service.prepareExport()
        val decoded = target.service.decodeImport(
            source.service.encodeExport(envelope, null),
        )
        val plan = target.service.preview(decoded, BackupConflictMode.REPLACE)

        val result = runCatching { target.service.apply(plan) }
        val error = result.exceptionOrNull()

        assertTrue(error is BackupApplyException)
        assertTrue((error as BackupApplyException).cause is IOException)
        assertEquals(
            listOf(localFavourite),
            target.favourites.favourites.first(),
        )
    }

    private fun registryWithCustomStore(): StoreRegistry =
        BuiltInStores.registry().let { registry ->
            registry.copy(stores = registry.stores + customStore)
        }

    private fun service(
        favourites: FakeFavouritesStore = FakeFavouritesStore(),
        collections: CollectionsStore = FakeCollectionsStore(),
        registry: StoreRegistryStore = FakeBackupStoreRegistryStore(),
        settings: FeatureSettingsStore = FakeBackupFeatureSettingsStore(),
        handoff: HandoffPreferencesStore = FakeBackupHandoffStore(),
        overrides: MatchOverridesStore = FakeMatchOverridesStore(),
    ): ServiceBundle {
        val favouritesRepository = FavouritesRepository(store = favourites, now = { 100 })
        val collectionsRepository = CollectionsRepository(
            store = collections,
            now = { 100 },
        )
        val stores = StoreRegistryRepository(registry)
        val featureSettings = FeatureSettingsRepository(
            store = settings,
            profile = BuildProfile("test", emptySet()),
            descriptors = FeatureCatalog.all,
        )
        val handoffRepository = HandoffPreferencesRepository(handoff)
        val overridesRepository = MatchOverridesRepository(overrides)
        return ServiceBundle(
            favourites = favouritesRepository,
            collections = collectionsRepository,
            stores = stores,
            settings = featureSettings,
            handoff = handoffRepository,
            overrides = overridesRepository,
            service = DefaultBackupService(
                favouritesRepository = favouritesRepository,
                collectionsRepository = collectionsRepository,
                storeRegistryRepository = stores,
                featureSettingsRepository = featureSettings,
                handoffPreferencesRepository = handoffRepository,
                matchOverridesRepository = overridesRepository,
                appVersion = { "0.1.0" },
                now = { 1_000 },
            ),
        )
    }

    private data class ServiceBundle(
        val favourites: FavouritesRepository,
        val collections: CollectionsRepository,
        val stores: StoreRegistryRepository,
        val settings: FeatureSettingsRepository,
        val handoff: HandoffPreferencesRepository,
        val overrides: MatchOverridesRepository,
        val service: DefaultBackupService,
    )

    private class FakeBackupStoreRegistryStore(
        initial: StoreRegistry = BuiltInStores.registry(),
    ) : StoreRegistryStore {
        private val state = MutableStateFlow(initial)
        override val registry: Flow<StoreRegistry> = state

        override suspend fun save(registry: StoreRegistry) {
            state.value = registry
        }
    }

    private class FakeBackupFeatureSettingsStore(
        initial: FeatureSettings = FeatureSettings(),
    ) : FeatureSettingsStore {
        private val state = MutableStateFlow(initial)
        override val settings: Flow<FeatureSettings> = state

        override suspend fun save(settings: FeatureSettings) {
            state.value = settings
        }
    }

    private class FakeBackupHandoffStore(
        initial: HandoffPreferences = HandoffPreferences(),
    ) : HandoffPreferencesStore {
        private val state = MutableStateFlow(initial)
        override val preferences: Flow<HandoffPreferences> = state

        override suspend fun save(preferences: HandoffPreferences) {
            state.value = preferences
        }
    }

    private class FakeMatchOverridesStore(
        initial: List<MatchOverride> = emptyList(),
    ) : MatchOverridesStore {
        private val state = MutableStateFlow(initial)
        override val overrides: Flow<List<MatchOverride>> = state

        override suspend fun save(overrides: List<MatchOverride>) {
            state.value = overrides
        }
    }

    private class FailingCollectionsStore(
        initial: List<AppCollection> = emptyList(),
    ) : CollectionsStore {
        private val state = MutableStateFlow(initial)
        override val collections: Flow<List<AppCollection>> = state

        override suspend fun save(collections: List<AppCollection>) {
            throw IOException("collections write failed")
        }
    }
}
