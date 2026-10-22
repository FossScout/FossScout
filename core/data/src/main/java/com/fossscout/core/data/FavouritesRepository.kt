package com.fossscout.core.data

import com.fossscout.core.model.AppKey
import com.fossscout.core.model.Favourite
import com.fossscout.core.model.MAX_NOTE_LENGTH
import com.fossscout.core.model.StoreId
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
        val favourite = if (existing != null) {
            existing.copy(
                displayName = displayName,
                preferredStoreId = preferredStoreId,
                updatedAtEpochMillis = now(),
            )
        } else {
            Favourite(
                appKey = appKey,
                displayName = displayName,
                preferredStoreId = preferredStoreId,
                createdAtEpochMillis = now(),
                updatedAtEpochMillis = now(),
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

    suspend fun updateNote(appKey: AppKey, note: String) = mutex.withLock {
        require(note.length <= MAX_NOTE_LENGTH) { "Note is too long" }
        val current = store.favourites.first()
        store.save(
            current.map { favourite ->
                if (favourite.appKey == appKey) {
                    favourite.copy(note = note, updatedAtEpochMillis = now()).also { it.validate() }
                } else {
                    favourite
                }
            },
        )
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
