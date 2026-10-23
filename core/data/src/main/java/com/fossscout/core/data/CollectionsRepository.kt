package com.fossscout.core.data

import com.fossscout.core.model.AppCollection
import com.fossscout.core.model.AppKey
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Local source of truth for collections.
 *
 * Collections store ordered canonical [AppKey]s only, so deleting a collection
 * or removing one of its items never deletes a favourite. Every mutation is
 * persisted immediately; restart safety is provided by the store.
 */
class CollectionsRepository(
    private val store: CollectionsStore,
    private val now: () -> Long = System::currentTimeMillis,
    private val idFactory: () -> String = { UUID.randomUUID().toString() },
) {
    private val mutex = Mutex()

    val collections: Flow<List<AppCollection>> = store.collections

    suspend fun create(
        name: String,
        description: String = "",
        id: String? = null,
        itemKeys: List<AppKey> = emptyList(),
    ): AppCollection = mutex.withLock {
        val current = store.collections.first()
        val timestamp = now()
        val collection = AppCollection(
            id = id?.trim()?.takeIf(String::isNotEmpty) ?: idFactory(),
            name = name.trim(),
            description = description.trim(),
            itemKeys = itemKeys.distinct(),
            createdAtEpochMillis = timestamp,
            updatedAtEpochMillis = timestamp,
        )
        collection.validate()
        require(current.none { it.id == collection.id }) {
            "Collection ${collection.id} already exists"
        }
        store.save(current + collection)
        collection
    }

    suspend fun rename(id: String, name: String) = update(id) { collection ->
        collection.copy(name = name.trim())
    }

    suspend fun updateDescription(id: String, description: String) = update(id) { collection ->
        collection.copy(description = description.trim())
    }

    suspend fun update(id: String, name: String, description: String) = update(id) { collection ->
        collection.copy(name = name.trim(), description = description.trim())
    }

    suspend fun addItem(id: String, appKey: AppKey): Boolean = mutex.withLock {
        val current = store.collections.first()
        val collection = requireCollection(current, id)
        if (appKey in collection.itemKeys) return@withLock false
        saveReplacing(
            current = current,
            updated = collection.copy(
                itemKeys = collection.itemKeys + appKey,
                updatedAtEpochMillis = now(),
            ),
        )
        true
    }

    suspend fun removeItem(id: String, appKey: AppKey): Boolean = mutex.withLock {
        val current = store.collections.first()
        val collection = requireCollection(current, id)
        if (appKey !in collection.itemKeys) return@withLock false
        saveReplacing(
            current = current,
            updated = collection.copy(
                itemKeys = collection.itemKeys - appKey,
                updatedAtEpochMillis = now(),
            ),
        )
        true
    }

    /** Replaces the order while keeping the exact same set of item keys. */
    suspend fun reorder(id: String, orderedKeys: List<AppKey>) = mutex.withLock {
        val current = store.collections.first()
        val collection = requireCollection(current, id)
        require(orderedKeys.size == collection.itemKeys.size) {
            "Reorder must contain every item exactly once"
        }
        require(orderedKeys.toSet() == collection.itemKeys.toSet()) {
            "Reorder must contain every item exactly once"
        }
        if (orderedKeys == collection.itemKeys) return@withLock
        saveReplacing(
            current = current,
            updated = collection.copy(
                itemKeys = orderedKeys,
                updatedAtEpochMillis = now(),
            ),
        )
    }

    suspend fun move(id: String, appKey: AppKey, offset: Int): Boolean = mutex.withLock {
        val current = store.collections.first()
        val collection = requireCollection(current, id)
        val index = collection.itemKeys.indexOf(appKey)
        if (index < 0) return@withLock false
        val target = index + offset
        if (target !in collection.itemKeys.indices) return@withLock false
        val reordered = collection.itemKeys.toMutableList().apply {
            add(target, removeAt(index))
        }
        saveReplacing(
            current = current,
            updated = collection.copy(
                itemKeys = reordered,
                updatedAtEpochMillis = now(),
            ),
        )
        true
    }

    suspend fun delete(id: String) = mutex.withLock {
        val current = store.collections.first()
        if (current.none { it.id == id }) {
            throw NoSuchElementException("Unknown collection: $id")
        }
        store.save(current.filterNot { it.id == id })
    }

    /** Atomically replaces the collection list; used by the import flow. */
    suspend fun replaceAll(collections: List<AppCollection>) = mutex.withLock {
        collections.forEach(AppCollection::validate)
        require(collections.map(AppCollection::id).toSet().size == collections.size) {
            "Duplicate collection IDs"
        }
        store.save(collections)
    }

    private suspend fun update(
        id: String,
        transform: (AppCollection) -> AppCollection,
    ) = mutex.withLock {
        val current = store.collections.first()
        val collection = requireCollection(current, id)
        val updated = transform(collection)
            .copy(updatedAtEpochMillis = now())
        saveReplacing(current = current, updated = updated)
    }

    private suspend fun saveReplacing(
        current: List<AppCollection>,
        updated: AppCollection,
    ) {
        updated.validate()
        store.save(
            current.map { collection ->
                if (collection.id == updated.id) updated else collection
            },
        )
    }

    private fun requireCollection(
        collections: List<AppCollection>,
        id: String,
    ): AppCollection = collections.firstOrNull { it.id == id }
        ?: throw NoSuchElementException("Unknown collection: $id")
}
