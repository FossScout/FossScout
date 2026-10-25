package com.fossscout.core.data

import com.fossscout.core.model.StoreId
import com.fossscout.core.store.AdapterType
import com.fossscout.core.store.BuiltInStores
import com.fossscout.core.store.StoreAdapterSupport
import com.fossscout.core.store.StoreDescriptor
import com.fossscout.core.store.StoreDraft
import com.fossscout.core.store.StoreEntry
import com.fossscout.core.store.StoreHealth
import com.fossscout.core.store.StoreRegistry
import com.fossscout.core.store.StoreValidation
import com.fossscout.core.store.STORE_DESCRIPTOR_SCHEMA_VERSION
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Local source of truth for stores. Priorities are normalized to 0..n-1, with
 * lower numbers matching first. Changes are persisted immediately, and derived
 * matchability is recomputed on every read so disable/delete/reorder/update
 * affect the next query without a restart.
 */
class StoreRegistryRepository(
    private val store: StoreRegistryStore,
    private val supportedAdapterTypes: Set<AdapterType> =
        StoreAdapterSupport.matchableAdapterTypes,
    private val codec: StoreRegistryCodec = StoreRegistryCodec(),
    private val idFactory: () -> StoreId = {
        StoreId("custom-${UUID.randomUUID()}")
    },
) {
    private val mutex = Mutex()

    val entries: Flow<List<StoreEntry>> = store.registry.map { registry ->
        registry.sortedByPriority().stores.map(::entryFor)
    }

    val matchableDescriptors: Flow<List<StoreDescriptor>> = entries.map { entries ->
        entries.filter(StoreEntry::matchable).map(StoreEntry::descriptor)
    }

    suspend fun add(draft: StoreDraft): StoreDescriptor = mutex.withLock {
        val current = currentRegistry()
        val descriptor = descriptorFrom(
            draft = draft,
            id = idFactory(),
            priority = current.stores.size,
        )
        require(current.stores.none { it.id == descriptor.id }) {
            "Store ${descriptor.id.value} already exists"
        }
        save(current.stores + descriptor)
        descriptor
    }

    suspend fun update(id: StoreId, draft: StoreDraft): StoreDescriptor = mutex.withLock {
        val current = currentRegistry()
        val existing = current.stores.firstOrNull { it.id == id }
            ?: throw NoSuchElementException("Unknown store: ${id.value}")
        val updated = descriptorFrom(
            draft = draft,
            id = existing.id,
            priority = existing.priority,
            enabled = existing.enabled,
            health = existing.health,
            builtIn = existing.builtIn,
        )
        save(current.stores.map { if (it.id == id) updated else it })
        updated
    }

    suspend fun setEnabled(id: StoreId, enabled: Boolean) = mutex.withLock {
        val current = currentRegistry()
        val existing = current.stores.firstOrNull { it.id == id }
            ?: throw NoSuchElementException("Unknown store: ${id.value}")
        if (existing.enabled == enabled) return@withLock
        save(
            current.stores.map { descriptor ->
                if (descriptor.id == id) descriptor.copy(enabled = enabled) else descriptor
            },
        )
    }

    suspend fun updateHealth(id: StoreId, health: StoreHealth) = mutex.withLock {
        val current = currentRegistry()
        val existing = current.stores.firstOrNull { it.id == id }
            ?: throw NoSuchElementException("Unknown store: ${id.value}")
        if (existing.health == health) return@withLock
        save(
            current.stores.map { descriptor ->
                if (descriptor.id == id) descriptor.copy(health = health) else descriptor
            },
        )
    }

    suspend fun reorder(orderedIds: List<StoreId>) = mutex.withLock {
        val current = currentRegistry()
        val currentIds = current.stores.map(StoreDescriptor::id)
        require(orderedIds.size == currentIds.size && orderedIds.toSet() == currentIds.toSet()) {
            "Reorder must contain every store exactly once"
        }
        val byId = current.stores.associateBy(StoreDescriptor::id)
        save(orderedIds.map(byId::getValue))
    }

    suspend fun move(id: StoreId, offset: Int): Boolean = mutex.withLock {
        val current = currentRegistry()
        val ordered = current.stores.sortedBy(StoreDescriptor::priority)
        val index = ordered.indexOfFirst { it.id == id }
        if (index < 0) throw NoSuchElementException("Unknown store: ${id.value}")
        val target = index + offset
        if (target !in ordered.indices) return@withLock false
        val reordered = ordered.toMutableList().apply {
            add(target, removeAt(index))
        }
        save(reordered)
        true
    }

    suspend fun delete(id: StoreId) = mutex.withLock {
        val current = currentRegistry()
        if (current.stores.none { it.id == id }) {
            throw NoSuchElementException("Unknown store: ${id.value}")
        }
        save(current.stores.filterNot { it.id == id })
    }

    /**
     * Restores only the custom portion of a backup. Built-in descriptors stay
     * under the app's control, so a backup cannot downgrade their endpoint or
     * pinned fingerprint.
     */
    suspend fun replaceCustom(stores: List<StoreDescriptor>) = mutex.withLock {
        val current = currentRegistry()
        val builtIns = current.stores.filter(StoreDescriptor::builtIn)
        val builtInIds = BuiltInStores.registry().stores.map { it.id }.toSet()
        require(stores.none(StoreDescriptor::builtIn)) {
            "Restored stores must be custom stores"
        }
        require(stores.none { it.id in builtInIds }) {
            "A custom store cannot reuse a built-in store id"
        }
        require(stores.map { it.id }.distinct().size == stores.size) {
            "Duplicate store IDs"
        }
        stores.forEach(StoreValidation::requireValid)
        save(builtIns + stores)
    }

    suspend fun exportDescriptor(id: StoreId): String = mutex.withLock {
        val descriptor = currentRegistry().stores.firstOrNull { it.id == id }
            ?: throw NoSuchElementException("Unknown store: ${id.value}")
        codec.encodeDescriptor(descriptor)
    }

    private suspend fun currentRegistry(): StoreRegistry =
        store.registry.first().sortedByPriority()

    private suspend fun save(stores: List<StoreDescriptor>) {
        val normalized = stores.mapIndexed { index, descriptor ->
            descriptor.copy(priority = index)
        }
        store.save(StoreRegistry(stores = normalized))
    }

    private fun descriptorFrom(
        draft: StoreDraft,
        id: StoreId,
        priority: Int,
        enabled: Boolean = true,
        health: StoreHealth = StoreHealth(),
        builtIn: Boolean = false,
    ): StoreDescriptor {
        val homepage = draft.homepage.trim()
        val indexUrl = draft.indexUrl.trim().takeIf(String::isNotEmpty)
            ?: homepage.takeIf {
                draft.adapterType == AdapterType.FDROID_V1 ||
                    draft.adapterType == AdapterType.FDROID_V2
            }
        val descriptor = StoreDescriptor(
            id = id,
            name = draft.name.trim(),
            homepage = homepage,
            adapterType = draft.adapterType,
            indexUrl = indexUrl,
            apiUrl = draft.apiUrl.trim().takeIf(String::isNotEmpty),
            repositoryUrl = draft.repositoryUrl.trim().takeIf(String::isNotEmpty),
            fingerprint = StoreValidation.normalizeFingerprint(draft.fingerprint)
                .takeIf(String::isNotEmpty),
            enabled = enabled,
            priority = priority,
            trustNote = draft.trustNote.trim(),
            health = health,
            schemaVersion = STORE_DESCRIPTOR_SCHEMA_VERSION,
            allowInsecureHttp = draft.allowInsecureHttp,
            builtIn = builtIn,
        )
        StoreValidation.requireValid(descriptor)
        return descriptor
    }

    private fun entryFor(descriptor: StoreDescriptor): StoreEntry = StoreEntry(
        descriptor = descriptor,
        availability = descriptor.availability(supportedAdapterTypes),
    )
}
