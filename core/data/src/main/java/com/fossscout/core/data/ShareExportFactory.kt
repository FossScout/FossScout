package com.fossscout.core.data

import com.fossscout.core.model.AppCollection
import com.fossscout.core.model.AppKey
import com.fossscout.core.model.Favourite
import com.fossscout.core.model.SHARE_SCHEMA_VERSION
import com.fossscout.core.model.ShareEnvelope
import com.fossscout.core.model.ShareItem
import com.fossscout.core.model.ShareType

/**
 * Builds share envelopes from local state.
 *
 * Notes are excluded unless [includeNotes] is explicitly true. Preferred stores
 * are copied as references only; no store descriptor is embedded.
 */
class ShareExportFactory(
    private val now: () -> Long = System::currentTimeMillis,
) {
    fun forFavourite(
        favourite: Favourite,
        includeNotes: Boolean = false,
    ): ShareEnvelope = ShareEnvelope(
        schemaVersion = SHARE_SCHEMA_VERSION,
        type = ShareType.SINGLE_FAVOURITE,
        name = favourite.displayName,
        items = listOf(itemFor(favourite, includeNotes)),
        exportedAtEpochMillis = now(),
    )

    fun forCollection(
        collection: AppCollection,
        favourites: List<Favourite>,
        includeNotes: Boolean = false,
    ): ShareEnvelope {
        val byKey = favourites.associateBy(Favourite::appKey)
        return ShareEnvelope(
            schemaVersion = SHARE_SCHEMA_VERSION,
            type = ShareType.COLLECTION,
            name = collection.name,
            description = collection.description,
            collectionId = collection.id,
            items = collection.itemKeys.map { key ->
                val favourite = byKey[key]
                if (favourite == null) {
                    ShareItem(appKey = key, displayName = key.value)
                } else {
                    itemFor(favourite, includeNotes)
                }
            },
            exportedAtEpochMillis = now(),
        )
    }

    fun forAllFavourites(
        favourites: List<Favourite>,
        includeNotes: Boolean = false,
    ): ShareEnvelope = ShareEnvelope(
        schemaVersion = SHARE_SCHEMA_VERSION,
        type = ShareType.ALL_FAVOURITES,
        name = ALL_FAVOURITES_NAME,
        items = favourites.map { favourite -> itemFor(favourite, includeNotes) },
        exportedAtEpochMillis = now(),
    )

    fun itemFor(favourite: Favourite, includeNotes: Boolean): ShareItem = ShareItem(
        appKey = favourite.appKey,
        displayName = favourite.displayName,
        preferredStoreId = favourite.preferredStoreId,
        tags = favourite.tags,
        note = favourite.note
            .takeIf { includeNotes && it.isNotBlank() },
    )

    private companion object {
        const val ALL_FAVOURITES_NAME = "All saved projects"
    }
}

/** Returns a safe, descriptive file name fragment for a share envelope. */
fun shareFileBaseName(name: String, fallback: String = "fossscout-list"): String {
    val sanitized = name
        .trim()
        .lowercase()
        .map { character ->
            when {
                character.isLetterOrDigit() -> character
                character == '-' || character == '_' -> character
                else -> '-'
            }
        }
        .joinToString("")
        .trim('-')
        .replace(Regex("-+"), "-")
    return sanitized.ifBlank { fallback }.take(60)
}

/** Convenience for tests and callers that only need a canonical key list. */
internal fun ShareEnvelope.itemKeys(): List<AppKey> = items.map(ShareItem::appKey)
