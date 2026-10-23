package com.fossscout.core.data

import com.fossscout.core.model.AppCollection
import com.fossscout.core.model.AppKey
import com.fossscout.core.model.Favourite
import com.fossscout.core.model.ShareEnvelope
import com.fossscout.core.model.ShareItem
import com.fossscout.core.model.ShareType
import com.fossscout.core.model.StoreId
import java.util.UUID
import kotlinx.coroutines.flow.first

enum class ImportConflictMode {
    /** Keep existing details, add missing items, and union collection items. */
    MERGE,

    /** Overwrite matching entries and, for an all-favourites list, remove absent ones. */
    REPLACE,

    /** Leave conflicting entries untouched and only add new entries. */
    SKIP,
}

enum class ImportItemAction {
    ADD,
    MERGE,
    REPLACE,
    SKIP,
}

enum class ImportCollectionAction {
    NONE,
    CREATE,
    MERGE,
    REPLACE,
    SKIP,
}

data class ImportPreviewItem(
    val appKey: AppKey,
    val displayName: String,
    val action: ImportItemAction,
    val preferredStoreId: StoreId? = null,
    val hasNote: Boolean = false,
    val unknownStore: Boolean = false,
)

/**
 * Immutable preview of everything an import would do. No repository write
 * happens until [ShareImportService.apply] is called with the user's choices.
 */
data class ImportPreview(
    val envelope: ShareEnvelope,
    val mode: ImportConflictMode,
    val items: List<ImportPreviewItem>,
    val removedFavouriteCount: Int = 0,
    val collectionAction: ImportCollectionAction = ImportCollectionAction.NONE,
    val collectedItemCount: Int = 0,
    val notesItemCount: Int = 0,
) {
    val addedFavouriteCount: Int
        get() = items.count { it.action == ImportItemAction.ADD }

    val mergedFavouriteCount: Int
        get() = items.count { it.action == ImportItemAction.MERGE }

    val replacedFavouriteCount: Int
        get() = items.count { it.action == ImportItemAction.REPLACE }

    val skippedFavouriteCount: Int
        get() = items.count { it.action == ImportItemAction.SKIP }

    val conflictCount: Int
        get() = items.count { it.action != ImportItemAction.ADD }

    val hasNotes: Boolean
        get() = notesItemCount > 0

    val unknownStores: List<StoreId>
        get() = items.mapNotNull { item ->
            item.preferredStoreId.takeIf { item.unknownStore }
        }.distinct()
}

/**
 * Applies the versioned share envelope to local favourites and collections.
 *
 * Safety rules:
 * - [preview] only reads; it never writes favourites, collections, or stores.
 * - Stores named by an envelope are only accepted when the id is already known
 *   locally. Unknown stores are reported in the preview and dropped on import;
 *   nothing is installed or opened.
 * - Notes are dropped unless [apply] receives `includeNotes = true`.
 * - Deleting a collection is never part of import. Replace mode only replaces
 *   the incoming collection/favourites, never unrelated collections.
 */
class ShareImportService(
    private val favouritesRepository: FavouritesRepository,
    private val collectionsRepository: CollectionsRepository,
    private val now: () -> Long = System::currentTimeMillis,
    private val collectionIdFactory: () -> String = { UUID.randomUUID().toString() },
) {
    suspend fun preview(
        envelope: ShareEnvelope,
        mode: ImportConflictMode,
        knownStoreIds: Set<StoreId> = emptySet(),
    ): ImportPreview {
        envelope.validate()
        val favourites = favouritesRepository.favourites.first()
        val collections = collectionsRepository.collections.first()
        val currentKeys = favourites.map(Favourite::appKey).toSet()
        val incoming = envelope.items.distinctBy(ShareItem::appKey)

        val previewItems = incoming.map { item ->
            val exists = item.appKey in currentKeys
            val action = when {
                !exists -> ImportItemAction.ADD
                mode == ImportConflictMode.SKIP -> ImportItemAction.SKIP
                mode == ImportConflictMode.MERGE -> ImportItemAction.MERGE
                else -> ImportItemAction.REPLACE
            }
            ImportPreviewItem(
                appKey = item.appKey,
                displayName = item.displayName,
                action = action,
                preferredStoreId = item.preferredStoreId,
                hasNote = !item.note.isNullOrBlank(),
                unknownStore = item.preferredStoreId != null &&
                    item.preferredStoreId !in knownStoreIds,
            )
        }

        val removed = if (envelope.type == ShareType.ALL_FAVOURITES &&
            mode == ImportConflictMode.REPLACE
        ) {
            currentKeys.count { key -> incoming.none { it.appKey == key } }
        } else {
            0
        }

        val collectionAction = when {
            envelope.type != ShareType.COLLECTION -> ImportCollectionAction.NONE
            envelope.collectionId == null -> ImportCollectionAction.CREATE
            collections.none { it.id == envelope.collectionId } -> ImportCollectionAction.CREATE
            mode == ImportConflictMode.SKIP -> ImportCollectionAction.SKIP
            mode == ImportConflictMode.MERGE -> ImportCollectionAction.MERGE
            else -> ImportCollectionAction.REPLACE
        }

        return ImportPreview(
            envelope = envelope,
            mode = mode,
            items = previewItems,
            removedFavouriteCount = removed,
            collectionAction = collectionAction,
            collectedItemCount = if (envelope.type == ShareType.COLLECTION) {
                incoming.size
            } else {
                0
            },
            notesItemCount = incoming.count { !it.note.isNullOrBlank() },
        )
    }

    suspend fun apply(
        envelope: ShareEnvelope,
        mode: ImportConflictMode,
        includeNotes: Boolean,
        knownStoreIds: Set<StoreId> = emptySet(),
    ) {
        envelope.validate()
        val timestamp = now()
        val currentFavourites = favouritesRepository.favourites.first()
        val incoming = envelope.items.distinctBy(ShareItem::appKey)
        val currentByKey = currentFavourites.associateBy(Favourite::appKey)
        val incomingKeys = incoming.map(ShareItem::appKey).toSet()

        val working = if (envelope.type == ShareType.ALL_FAVOURITES &&
            mode == ImportConflictMode.REPLACE
        ) {
            currentFavourites.filter { it.appKey in incomingKeys }
        } else {
            currentFavourites
        }.toMutableList()

        incoming.forEach { item ->
            val index = working.indexOfFirst { it.appKey == item.appKey }
            val existing = currentByKey[item.appKey]
            when {
                index < 0 -> working += favouriteFrom(
                    item = item,
                    existing = null,
                    includeNotes = includeNotes,
                    knownStoreIds = knownStoreIds,
                    timestamp = timestamp,
                )

                mode == ImportConflictMode.SKIP -> Unit

                mode == ImportConflictMode.MERGE -> working[index] = merge(
                    existing = working[index],
                    item = item,
                    includeNotes = includeNotes,
                    knownStoreIds = knownStoreIds,
                    timestamp = timestamp,
                )

                else -> working[index] = favouriteFrom(
                    item = item,
                    existing = existing,
                    includeNotes = includeNotes,
                    knownStoreIds = knownStoreIds,
                    timestamp = timestamp,
                )
            }
        }

        if (working != currentFavourites) {
            favouritesRepository.replaceAll(working)
        }

        if (envelope.type == ShareType.COLLECTION) {
            importCollection(
                envelope = envelope,
                mode = mode,
                incomingKeys = incomingKeys,
                timestamp = timestamp,
            )
        }
    }

    private suspend fun importCollection(
        envelope: ShareEnvelope,
        mode: ImportConflictMode,
        incomingKeys: Set<AppKey>,
        timestamp: Long,
    ) {
        val current = collectionsRepository.collections.first()
        val requestedId = envelope.collectionId?.trim()?.takeIf(String::isNotEmpty)
        val existing = requestedId?.let { id -> current.firstOrNull { it.id == id } }

        val updated = when {
            existing == null -> AppCollection(
                id = requestedId ?: uniqueCollectionId(current),
                name = envelope.name.trim(),
                description = envelope.description.trim(),
                itemKeys = incomingKeys.distinct(),
                createdAtEpochMillis = timestamp,
                updatedAtEpochMillis = timestamp,
            )

            mode == ImportConflictMode.SKIP -> return

            mode == ImportConflictMode.MERGE -> existing.copy(
                itemKeys = (existing.itemKeys + incomingKeys).distinct(),
                updatedAtEpochMillis = timestamp,
            )

            else -> existing.copy(
                name = envelope.name.trim(),
                description = envelope.description.trim(),
                itemKeys = incomingKeys.distinct(),
                updatedAtEpochMillis = timestamp,
            )
        }

        val result = if (existing == null) {
            current + updated
        } else {
            current.map { collection ->
                if (collection.id == updated.id) updated else collection
            }
        }
        collectionsRepository.replaceAll(result)
    }

    private fun favouriteFrom(
        item: ShareItem,
        existing: Favourite?,
        includeNotes: Boolean,
        knownStoreIds: Set<StoreId>,
        timestamp: Long,
    ): Favourite = Favourite(
        appKey = item.appKey,
        displayName = item.displayName.trim(),
        preferredStoreId = item.preferredStoreId?.takeIf { it in knownStoreIds },
        note = if (includeNotes) item.note.orEmpty() else existing?.note.orEmpty(),
        tags = item.tags,
        createdAtEpochMillis = existing?.createdAtEpochMillis ?: timestamp,
        updatedAtEpochMillis = timestamp,
    ).also(Favourite::validate)

    private fun merge(
        existing: Favourite,
        item: ShareItem,
        includeNotes: Boolean,
        knownStoreIds: Set<StoreId>,
        timestamp: Long,
    ): Favourite = existing.copy(
        displayName = item.displayName.trim().ifBlank { existing.displayName },
        preferredStoreId = item.preferredStoreId?.takeIf { it in knownStoreIds }
            ?: existing.preferredStoreId,
        tags = existing.tags + item.tags,
        note = if (includeNotes && !item.note.isNullOrBlank()) {
            item.note.orEmpty()
        } else {
            existing.note
        },
        updatedAtEpochMillis = timestamp,
    ).also(Favourite::validate)

    private fun uniqueCollectionId(current: List<AppCollection>): String {
        var candidate = collectionIdFactory()
        while (current.any { it.id == candidate }) {
            candidate = collectionIdFactory()
        }
        return candidate
    }
}
