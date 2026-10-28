package com.fossscout.core.backup

import com.fossscout.core.feature.FeatureId
import com.fossscout.core.feature.FeatureSettingValue
import com.fossscout.core.feature.FeatureSettings
import com.fossscout.core.feature.IndexCacheSettings
import com.fossscout.core.handoff.HandoffPreferences
import com.fossscout.core.model.AppCollection
import com.fossscout.core.model.AppKey
import com.fossscout.core.model.Favourite
import com.fossscout.core.model.MatchOverride
import com.fossscout.core.model.MatchOverrideTarget
import com.fossscout.core.model.StoreId
import com.fossscout.core.store.AdapterType
import com.fossscout.core.store.StoreDescriptor

internal fun sampleFavourite(
    key: String = "org.example.app",
    displayName: String = "Example",
    note: String = "private note",
    preferredStoreId: StoreId? = StoreId("custom-repo"),
    tags: Set<String> = setOf("tools"),
    createdAt: Long = 10,
    updatedAt: Long = 20,
): Favourite = Favourite(
    appKey = AppKey(key),
    displayName = displayName,
    preferredStoreId = preferredStoreId,
    note = note,
    tags = tags,
    createdAtEpochMillis = createdAt,
    updatedAtEpochMillis = updatedAt,
)

internal fun sampleCollection(
    id: String = "collection-1",
    name: String = "Utilities",
    itemKeys: List<AppKey> = listOf(AppKey("org.example.app")),
    createdAt: Long = 10,
    updatedAt: Long = 20,
): AppCollection = AppCollection(
    id = id,
    name = name,
    description = "Useful tools",
    itemKeys = itemKeys,
    createdAtEpochMillis = createdAt,
    updatedAtEpochMillis = updatedAt,
)

internal fun sampleStore(
    id: String = "custom-repo",
    fingerprint: String = "a".repeat(64),
    trustNote: String = "Pinned from official page",
): StoreDescriptor = StoreDescriptor(
    id = StoreId(id),
    name = "Custom repo",
    homepage = "https://example.com",
    adapterType = AdapterType.FDROID_V2,
    indexUrl = "https://example.com/repo",
    fingerprint = fingerprint,
    trustNote = trustNote,
    priority = 2,
)

internal fun sampleSettings(
    disabled: Set<FeatureId> = setOf(FeatureId.STANDALONE),
    storageBudget: Int = 512,
): FeatureSettings = FeatureSettings(
    disabledFeatureIds = disabled,
    values = mapOf(
        FeatureId.INDEX_CACHE to mapOf(
            IndexCacheSettings.STORAGE_BUDGET_MB to
                FeatureSettingValue.IntegerValue(storageBudget),
        ),
    ),
)

internal fun sampleHandoff(
    preferredStores: Map<String, String> = mapOf("org.example.app" to "fdroid"),
): HandoffPreferences = HandoffPreferences(preferredStores = preferredStores)

internal fun sampleMatchOverride(
    installedAppKey: String = "org.example.app",
    target: MatchOverrideTarget? = MatchOverrideTarget(
        appKey = AppKey("org.example.alt"),
        displayName = "Alternative",
        storeId = StoreId("custom-repo"),
        storeName = "Custom repo",
    ),
    note: String = "private correction",
    createdAt: Long = 10,
    updatedAt: Long = 20,
): MatchOverride = MatchOverride(
    installedAppKey = AppKey(installedAppKey),
    target = target,
    note = note,
    createdAtEpochMillis = createdAt,
    updatedAtEpochMillis = updatedAt,
)

internal fun samplePayload(
    favourites: List<Favourite> = listOf(sampleFavourite()),
    collections: List<AppCollection> = listOf(sampleCollection()),
    customStores: List<StoreDescriptor> = listOf(sampleStore()),
    featureSettings: FeatureSettings = sampleSettings(),
    handoffPreferences: HandoffPreferences = sampleHandoff(),
    matchOverrides: List<MatchOverride>? = null,
): BackupPayload = BackupPayload(
    favourites = favourites,
    collections = collections,
    customStores = customStores,
    featureSettings = BackupFeatureSettings.from(featureSettings),
    handoffPreferences = BackupHandoffPreferences.from(handoffPreferences),
    matchOverrides = matchOverrides,
)

internal fun sampleEnvelope(
    payload: BackupPayload = samplePayload(),
    appVersion: String = "0.1.0",
    createdAtEpochMillis: Long = 1_000,
): BackupEnvelope = BackupCodec().create(
    appVersion = appVersion,
    createdAtEpochMillis = createdAtEpochMillis,
    payload = payload,
)
