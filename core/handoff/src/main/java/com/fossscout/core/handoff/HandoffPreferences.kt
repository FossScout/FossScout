package com.fossscout.core.handoff

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable

const val HANDOFF_PREFERENCES_SCHEMA_VERSION = 1

/**
 * Local-only handoff preferences. Only the client id is stored; no app content,
 * account, or browsing history is collected.
 */
@Serializable
data class HandoffPreferences(
    val schemaVersion: Int = HANDOFF_PREFERENCES_SCHEMA_VERSION,
    val preferredStores: Map<String, String> = emptyMap(),
) {
    init {
        require(schemaVersion == HANDOFF_PREFERENCES_SCHEMA_VERSION) {
            "Unsupported handoff preferences schema version: $schemaVersion"
        }
        preferredStores.forEach { (appKey, clientId) ->
            require(appKey.isNotBlank()) { "Preferred-store app key is blank" }
            require(clientId.isNotBlank()) { "Preferred-store client id is blank" }
        }
    }
}

interface HandoffPreferencesStore {
    val preferences: Flow<HandoffPreferences>

    suspend fun save(preferences: HandoffPreferences)
}

/** Serializes read-modify-write updates so two selections cannot overwrite each other. */
class HandoffPreferencesRepository(
    private val store: HandoffPreferencesStore,
) {
    private val mutex = Mutex()

    val preferences: Flow<HandoffPreferences> = store.preferences

    suspend fun preferredStore(appKey: String): String? {
        val key = appKey.trim()
        if (key.isEmpty()) return null
        return store.preferences.first().preferredStores[key]
    }

    suspend fun setPreferredStore(appKey: String, clientId: String) = mutex.withLock {
        val key = appKey.trim()
        require(key.isNotEmpty()) { "Preferred-store app key is blank" }
        require(CompanionStores.byId(clientId) != null) {
            "Unknown companion store client: $clientId"
        }
        val current = store.preferences.first()
        if (current.preferredStores[key] == clientId) return@withLock
        store.save(
            current.copy(preferredStores = current.preferredStores + (key to clientId)),
        )
    }

    suspend fun clearPreferredStore(appKey: String) = mutex.withLock {
        val key = appKey.trim()
        val current = store.preferences.first()
        if (key.isEmpty() || key !in current.preferredStores) return@withLock
        store.save(
            current.copy(preferredStores = current.preferredStores - key),
        )
    }

    /**
     * Restores a backup snapshot. Unknown client ids and blank keys are dropped
     * so a restore cannot select a client this build cannot open.
     */
    suspend fun replace(preferences: HandoffPreferences) = mutex.withLock {
        val sanitized = preferences.preferredStores.entries
            .map { (appKey, clientId) -> appKey.trim() to clientId.trim() }
            .filter { (appKey, clientId) ->
                appKey.isNotEmpty() && CompanionStores.byId(clientId) != null
            }
            .toMap()
        val current = store.preferences.first()
        if (current.preferredStores != sanitized) {
            store.save(preferences.copy(preferredStores = sanitized))
        }
    }
}
