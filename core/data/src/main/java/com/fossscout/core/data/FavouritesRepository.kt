package com.fossscout.core.data

import com.fossscout.core.model.AppKey
import com.fossscout.core.model.Favourite
import com.fossscout.core.model.StoreId
import com.fossscout.core.model.normalizeTags
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class FavouritesRepository(
    private val store: FavouritesStore,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val mutex = Mutex()

    val favourites: Flow<List<Favourite>> = store.favourites

    suspend fun add(
        appKey: AppKey,
        displayName: String,
        preferredStoreId: StoreId? = null,
    ): Favourite = mutex.withLock {
        val current = store.favourites.first()
        val existing = current.firstOrNull { it.appKey == appKey }
        val timestamp = now()
        val favourite = if (existing != null) {
            existing.copy(
                displayName = displayName,
                preferredStoreId = preferredStoreId ?: existing.preferredStoreId,
                updatedAtEpochMillis = timestamp,
            )
        } else {
            Favourite(
                appKey = appKey,
                displayName = displayName,
                preferredStoreId = preferredStoreId,
                createdAtEpochMillis = timestamp,
                updatedAtEpochMillis = timestamp,
            )
        }
        favourite.validate()
        store.save(
            if (existing != null) {
                current.map { if (it.appKey == appKey) favourite else it }
            } else {
                current + favourite
            },
        )
        favourite
    }

    suspend fun remove(appKey: AppKey) = mutex.withLock {
        store.save(store.favourites.first().filterNot { it.appKey == appKey })
    }

    suspend fun updateNote(appKey: AppKey, note: String) = update(appKey) { favourite ->
        favourite.copy(note = note)
    }

    suspend fun updateTags(appKey: AppKey, tags: Iterable<String>) = update(appKey) { favourite ->
        favourite.copy(tags = normalizeTags(tags))
    }

    /**
     * Replaces every editable detail in one write. Tags are normalized here so
     * callers cannot persist duplicates, blanks, or oversized values.
     */
    suspend fun updateDetails(
        appKey: AppKey,
        note: String,
        tags: Iterable<String>,
        preferredStoreId: StoreId?,
    ) = update(appKey) { favourite ->
        favourite.copy(
            note = note,
            tags = normalizeTags(tags),
            preferredStoreId = preferredStoreId,
        )
    }

    suspend fun updatePreferredStore(appKey: AppKey, preferredStoreId: StoreId?) =
        update(appKey) { favourite ->
            favourite.copy(preferredStoreId = preferredStoreId)
        }

    private suspend fun update(
        appKey: AppKey,
        transform: (Favourite) -> Favourite,
    ) = mutex.withLock {
        val current = store.favourites.first()
        store.save(
            current.map { favourite ->
                if (favourite.appKey == appKey) {
                    transform(favourite)
                        .copy(updatedAtEpochMillis = now())
                        .also { it.validate() }
                } else {
                    favourite
                }
            },
        )
    }

    /**
     * Atomically replaces every favourite. Used by explicit import/replace
     * flows, which compute the complete result before writing once.
     */
    suspend fun replaceAll(favourites: List<Favourite>) = mutex.withLock {
        require(favourites.map(Favourite::appKey).toSet().size == favourites.size) {
            "Duplicate favourite app keys"
        }
        favourites.forEach(Favourite::validate)
        store.save(favourites)
    }

    suspend fun toggle(appKey: AppKey, displayName: String) = mutex.withLock {
        val current = store.favourites.first()
        if (current.any { it.appKey == appKey }) {
            store.save(current.filterNot { it.appKey == appKey })
        } else {
            val favourite = Favourite(
                appKey = appKey,
                displayName = displayName,
                createdAtEpochMillis = now(),
                updatedAtEpochMillis = now(),
            )
            favourite.validate()
            store.save(current + favourite)
        }
    }
}
