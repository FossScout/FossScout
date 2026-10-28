package com.fossscout.core.backup

import com.fossscout.core.feature.FeatureId
import com.fossscout.core.feature.FeatureSettingValue
import com.fossscout.core.feature.FeatureSettings
import com.fossscout.core.handoff.HandoffPreferences
import com.fossscout.core.model.AppCollection
import com.fossscout.core.model.AppKey
import com.fossscout.core.model.Favourite
import com.fossscout.core.model.MatchOverride
import com.fossscout.core.model.StoreId
import com.fossscout.core.store.StoreDescriptor
import com.fossscout.core.store.StoreHealth

enum class BackupConflictMode {
    /** Add missing items and combine conflicts; existing values win for settings. */
    MERGE,

    /** Replace favourites, collections, custom stores, settings, and handoff choices. */
    REPLACE,

    /** Only add missing favourites, collections, and custom stores. */
    SKIP,
}

enum class BackupChangeAction { ADD, MERGE, REPLACE, SKIP, NONE }

enum class BackupSettingsAction { NONE, MERGE, REPLACE, SKIP }

enum class BackupHandoffAction { NONE, MERGE, REPLACE, SKIP }

data class BackupFavouritePreview(
    val appKey: AppKey,
    val displayName: String,
    val action: BackupChangeAction,
    val noteChanged: Boolean = false,
    val tagsAdded: Int = 0,
    val preferredStoreChanged: Boolean = false,
)

data class BackupCollectionPreview(
    val id: String,
    val name: String,
    val action: BackupChangeAction,
    val itemCount: Int,
    val addedItemCount: Int = 0,
    val removedItemCount: Int = 0,
    val nameChanged: Boolean = false,
    val descriptionChanged: Boolean = false,
)

data class BackupStorePreview(
    val id: StoreId,
    val name: String,
    val action: BackupChangeAction,
    val fingerprintChanged: Boolean = false,
    val trustNoteChanged: Boolean = false,
)

data class BackupMatchOverridePreview(
    val installedAppKey: AppKey,
    val targetDisplayName: String? = null,
    val targetPackageName: String? = null,
    val action: BackupChangeAction,
    val targetChanged: Boolean = false,
    val noteChanged: Boolean = false,
)

data class BackupSettingsPreview(
    val action: BackupSettingsAction,
    val changed: Boolean = false,
    val disabledAdded: Int = 0,
    val disabledRemoved: Int = 0,
    val valueChanges: Int = 0,
)

data class BackupHandoffPreview(
    val action: BackupHandoffAction,
    val changed: Boolean = false,
    val added: Int = 0,
    val removed: Int = 0,
)

data class BackupPreview(
    val manifest: BackupManifest,
    val favourites: List<BackupFavouritePreview> = emptyList(),
    val collections: List<BackupCollectionPreview> = emptyList(),
    val stores: List<BackupStorePreview> = emptyList(),
    val matchOverrides: List<BackupMatchOverridePreview> = emptyList(),
    val settings: BackupSettingsPreview = BackupSettingsPreview(BackupSettingsAction.NONE),
    val handoff: BackupHandoffPreview = BackupHandoffPreview(BackupHandoffAction.NONE),
    val notesItemCount: Int = 0,
    val removedFavouriteCount: Int = 0,
    val removedCollectionCount: Int = 0,
    val removedStoreCount: Int = 0,
    val removedMatchOverrideCount: Int = 0,
) {
    val addedFavouriteCount: Int get() = favourites.count { it.action == BackupChangeAction.ADD }
    val mergedFavouriteCount: Int get() = favourites.count { it.action == BackupChangeAction.MERGE }
    val replacedFavouriteCount: Int get() =
        favourites.count { it.action == BackupChangeAction.REPLACE }
    val skippedFavouriteCount: Int get() =
        favourites.count { it.action == BackupChangeAction.SKIP }
    val conflictFavouriteCount: Int get() =
        favourites.count { it.action != BackupChangeAction.ADD }

    val addedCollectionCount: Int get() =
        collections.count { it.action == BackupChangeAction.ADD }
    val replacedCollectionCount: Int get() =
        collections.count { it.action == BackupChangeAction.REPLACE }
    val addedStoreCount: Int get() = stores.count { it.action == BackupChangeAction.ADD }
    val replacedStoreCount: Int get() =
        stores.count { it.action == BackupChangeAction.REPLACE }

    val addedMatchOverrideCount: Int get() =
        matchOverrides.count { it.action == BackupChangeAction.ADD }
    val mergedMatchOverrideCount: Int get() =
        matchOverrides.count { it.action == BackupChangeAction.MERGE }
    val replacedMatchOverrideCount: Int get() =
        matchOverrides.count { it.action == BackupChangeAction.REPLACE }
    val skippedMatchOverrideCount: Int get() =
        matchOverrides.count { it.action == BackupChangeAction.SKIP }
    val conflictMatchOverrideCount: Int get() =
        matchOverrides.count { it.action != BackupChangeAction.ADD }

    val hasConflicts: Boolean
        get() = conflictFavouriteCount > 0 ||
            collections.any { it.action != BackupChangeAction.ADD } ||
            stores.any { it.action != BackupChangeAction.ADD } ||
            conflictMatchOverrideCount > 0 ||
            settings.changed ||
            handoff.changed
}

data class BackupLocalState(
    val favourites: List<Favourite> = emptyList(),
    val collections: List<AppCollection> = emptyList(),
    val customStores: List<StoreDescriptor> = emptyList(),
    val featureSettings: FeatureSettings = FeatureSettings(),
    val handoffPreferences: HandoffPreferences = HandoffPreferences(),
    val matchOverrides: List<MatchOverride> = emptyList(),
)

data class BackupRestorePlan(
    val previous: BackupLocalState,
    val target: BackupLocalState,
    val preview: BackupPreview,
)

/**
 * Pure restore planning. The planner never writes; it computes the complete
 * target state and a per-section preview so callers can validate and show the
 * result before mutating local data.
 */
object BackupPlanner {
    fun plan(
        envelope: BackupEnvelope,
        local: BackupLocalState,
        mode: BackupConflictMode,
        nowEpochMillis: Long,
    ): BackupRestorePlan {
        envelope.validateStructure()
        val incoming = envelope.payload
        val incomingFavourites = incoming.favourites
        val localFavouritesByKey = local.favourites.associateBy(Favourite::appKey)

        val favouritePreview = incomingFavourites.map { item ->
            val existing = localFavouritesByKey[item.appKey]
            val action = actionFor(existing != null, mode)
            BackupFavouritePreview(
                appKey = item.appKey,
                displayName = item.displayName,
                action = action,
                noteChanged = existing != null && existing.note != item.note,
                tagsAdded = (item.tags - existing?.tags.orEmpty()).size,
                preferredStoreChanged = existing != null &&
                    item.preferredStoreId != null &&
                    existing.preferredStoreId != item.preferredStoreId,
            )
        }

        val targetFavourites = when (mode) {
            BackupConflictMode.REPLACE -> incomingFavourites
            BackupConflictMode.MERGE -> {
                val result = local.favourites.toMutableList()
                incomingFavourites.forEach { item ->
                    val index = result.indexOfFirst { it.appKey == item.appKey }
                    if (index < 0) {
                        result += item
                    } else {
                        result[index] = mergeFavourite(
                            existing = result[index],
                            incoming = item,
                            nowEpochMillis = nowEpochMillis,
                        )
                    }
                }
                result
            }

            BackupConflictMode.SKIP -> local.favourites +
                incomingFavourites.filter { it.appKey !in localFavouritesByKey }
        }

        val incomingCollectionsById = incoming.collections.associateBy(AppCollection::id)
        val localCollectionsById = local.collections.associateBy(AppCollection::id)
        val collectionPreview = incoming.collections.map { item ->
            val existing = localCollectionsById[item.id]
            BackupCollectionPreview(
                id = item.id,
                name = item.name,
                action = actionFor(existing != null, mode),
                itemCount = item.itemKeys.size,
                addedItemCount = (item.itemKeys - existing?.itemKeys.orEmpty().toSet()).size,
                removedItemCount = (existing?.itemKeys.orEmpty() - item.itemKeys.toSet()).size,
                nameChanged = existing != null && existing.name != item.name,
                descriptionChanged = existing != null && existing.description != item.description,
            )
        }

        val targetCollections = when (mode) {
            BackupConflictMode.REPLACE -> incoming.collections
            BackupConflictMode.MERGE -> {
                val merged = local.collections.map { existing ->
                    val item = incomingCollectionsById[existing.id] ?: return@map existing
                    val combinedKeys = (existing.itemKeys + item.itemKeys).distinct()
                    if (combinedKeys == existing.itemKeys) {
                        existing
                    } else {
                        existing.copy(
                            itemKeys = combinedKeys,
                            updatedAtEpochMillis = maxOf(
                                nowEpochMillis,
                                existing.updatedAtEpochMillis,
                                item.updatedAtEpochMillis,
                            ),
                        )
                    }
                }
                merged + incoming.collections.filter { it.id !in localCollectionsById }
            }

            BackupConflictMode.SKIP -> local.collections +
                incoming.collections.filter { it.id !in localCollectionsById }
        }

        val incomingStores = incoming.customStores.map { it.copy(health = StoreHealth()) }
        val localStoresById = local.customStores.associateBy(StoreDescriptor::id)
        val storePreview = incomingStores.map { item ->
            val existing = localStoresById[item.id]
            BackupStorePreview(
                id = item.id,
                name = item.name,
                action = actionFor(existing != null, mode),
                fingerprintChanged = existing != null && existing.fingerprint != item.fingerprint,
                trustNoteChanged = existing != null && existing.trustNote != item.trustNote,
            )
        }

        val targetStores = when (mode) {
            BackupConflictMode.REPLACE -> incomingStores
            BackupConflictMode.MERGE,
            BackupConflictMode.SKIP -> local.customStores +
                incomingStores.filter { it.id !in localStoresById }
        }

        val incomingOverrides = incoming.matchOverrides.orEmpty()
        val localOverridesByKey = local.matchOverrides.associateBy(MatchOverride::installedAppKey)
        val overridePreview = incomingOverrides.map { item ->
            val existing = localOverridesByKey[item.installedAppKey]
            BackupMatchOverridePreview(
                installedAppKey = item.installedAppKey,
                targetDisplayName = item.target?.displayName,
                targetPackageName = item.target?.appKey?.value,
                action = actionFor(existing != null, mode),
                targetChanged = existing != null && existing.target != item.target,
                noteChanged = existing != null && existing.note != item.note,
            )
        }

        val targetOverrides = when (mode) {
            BackupConflictMode.REPLACE -> incomingOverrides
            BackupConflictMode.MERGE -> {
                val result = local.matchOverrides.toMutableList()
                incomingOverrides.forEach { item ->
                    val index = result.indexOfFirst {
                        it.installedAppKey == item.installedAppKey
                    }
                    if (index < 0) {
                        result += item
                    } else {
                        result[index] = mergeOverride(result[index], item)
                    }
                }
                result
            }

            BackupConflictMode.SKIP -> local.matchOverrides +
                incomingOverrides.filter { it.installedAppKey !in localOverridesByKey }
        }

        val incomingSettings = incoming.featureSettings!!.toFeatureSettings()
        val targetSettings = when (mode) {
            BackupConflictMode.REPLACE -> incomingSettings
            BackupConflictMode.MERGE -> mergeSettings(local.featureSettings, incomingSettings)
            BackupConflictMode.SKIP -> local.featureSettings
        }
        val settingsPreview = settingsPreview(
            local = local.featureSettings,
            target = targetSettings,
            mode = mode,
        )

        val incomingHandoff = incoming.handoffPreferences!!.toHandoffPreferences()
        val targetHandoff = when (mode) {
            BackupConflictMode.REPLACE -> incomingHandoff
            BackupConflictMode.MERGE -> HandoffPreferences(
                preferredStores = incomingHandoff.preferredStores + local.handoffPreferences.preferredStores,
            )

            BackupConflictMode.SKIP -> local.handoffPreferences
        }
        val handoffPreview = handoffPreview(
            local = local.handoffPreferences,
            target = targetHandoff,
            mode = mode,
        )

        val preview = BackupPreview(
            manifest = envelope.manifest,
            favourites = favouritePreview,
            collections = collectionPreview,
            stores = storePreview,
            matchOverrides = overridePreview,
            settings = settingsPreview,
            handoff = handoffPreview,
            notesItemCount = incomingFavourites.count { it.note.isNotBlank() },
            removedFavouriteCount = if (mode == BackupConflictMode.REPLACE) {
                local.favourites.count { it.appKey !in incomingFavourites.map(Favourite::appKey) }
            } else {
                0
            },
            removedCollectionCount = if (mode == BackupConflictMode.REPLACE) {
                local.collections.count { it.id !in incomingCollectionsById }
            } else {
                0
            },
            removedStoreCount = if (mode == BackupConflictMode.REPLACE) {
                local.customStores.count { it.id !in incomingStores.map(StoreDescriptor::id) }
            } else {
                0
            },
            removedMatchOverrideCount = if (mode == BackupConflictMode.REPLACE) {
                local.matchOverrides.count {
                    it.installedAppKey !in incomingOverrides.map(MatchOverride::installedAppKey)
                }
            } else {
                0
            },
        )

        return BackupRestorePlan(
            previous = local,
            target = BackupLocalState(
                favourites = targetFavourites,
                collections = targetCollections,
                customStores = targetStores,
                featureSettings = targetSettings,
                handoffPreferences = targetHandoff,
                matchOverrides = targetOverrides,
            ),
            preview = preview,
        )
    }

    private fun actionFor(exists: Boolean, mode: BackupConflictMode): BackupChangeAction =
        when {
            !exists -> BackupChangeAction.ADD
            mode == BackupConflictMode.SKIP -> BackupChangeAction.SKIP
            mode == BackupConflictMode.MERGE -> BackupChangeAction.MERGE
            else -> BackupChangeAction.REPLACE
        }

    private fun mergeFavourite(
        existing: Favourite,
        incoming: Favourite,
        nowEpochMillis: Long,
    ): Favourite = existing.copy(
        displayName = incoming.displayName.ifBlank { existing.displayName },
        preferredStoreId = incoming.preferredStoreId ?: existing.preferredStoreId,
        note = incoming.note.ifBlank { existing.note },
        tags = existing.tags + incoming.tags,
        createdAtEpochMillis = minOf(
            existing.createdAtEpochMillis,
            incoming.createdAtEpochMillis,
        ),
        updatedAtEpochMillis = maxOf(
            nowEpochMillis,
            existing.updatedAtEpochMillis,
            incoming.updatedAtEpochMillis,
        ),
    )

    private fun mergeOverride(
        existing: MatchOverride,
        incoming: MatchOverride,
    ): MatchOverride = if (incoming.updatedAtEpochMillis >= existing.updatedAtEpochMillis) {
        incoming
    } else {
        existing
    }

    private fun mergeSettings(
        local: FeatureSettings,
        incoming: FeatureSettings,
    ): FeatureSettings {
        val values = incoming.values
            .mapValues { (_, featureValues) -> featureValues.toMutableMap() }
            .toMutableMap()
        local.values.forEach { (featureId, featureValues) ->
            values.getOrPut(featureId) { mutableMapOf() }.putAll(featureValues)
        }
        return FeatureSettings(
            disabledFeatureIds = local.disabledFeatureIds + incoming.disabledFeatureIds,
            values = values,
        )
    }

    private fun settingsPreview(
        local: FeatureSettings,
        target: FeatureSettings,
        mode: BackupConflictMode,
    ): BackupSettingsPreview {
        val action = when (mode) {
            BackupConflictMode.REPLACE -> BackupSettingsAction.REPLACE
            BackupConflictMode.MERGE -> BackupSettingsAction.MERGE
            BackupConflictMode.SKIP -> BackupSettingsAction.SKIP
        }
        val keys = local.values.keys + target.values.keys
        val valueChanges = keys.sumOf { featureId ->
            val localValues = local.values[featureId].orEmpty()
            val targetValues = target.values[featureId].orEmpty()
            (localValues.keys + targetValues.keys).count { key ->
                localValues[key] != targetValues[key]
            }
        }
        return BackupSettingsPreview(
            action = action,
            changed = local != target,
            disabledAdded = (target.disabledFeatureIds - local.disabledFeatureIds).size,
            disabledRemoved = (local.disabledFeatureIds - target.disabledFeatureIds).size,
            valueChanges = valueChanges,
        )
    }

    private fun handoffPreview(
        local: HandoffPreferences,
        target: HandoffPreferences,
        mode: BackupConflictMode,
    ): BackupHandoffPreview {
        val action = when (mode) {
            BackupConflictMode.REPLACE -> BackupHandoffAction.REPLACE
            BackupConflictMode.MERGE -> BackupHandoffAction.MERGE
            BackupConflictMode.SKIP -> BackupHandoffAction.SKIP
        }
        val localStores = local.preferredStores
        val targetStores = target.preferredStores
        return BackupHandoffPreview(
            action = action,
            changed = localStores != targetStores,
            added = (targetStores.keys - localStores.keys).size,
            removed = (localStores.keys - targetStores.keys).size,
        )
    }
}
