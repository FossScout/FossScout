package com.fossscout.core.data

import com.fossscout.core.feature.FeatureId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class FeatureSettingsRepository(private val store: FeatureSettingsStore) {
    private val mutex = Mutex()

    val disabled: Flow<Set<FeatureId>> =
        store.disabled.map { stored ->
            stored.mapNotNull { value ->
                FeatureId.entries.firstOrNull { it.name == value }
            }.toSet()
        }

    suspend fun setEnabled(id: FeatureId, enabled: Boolean) = mutex.withLock {
        val current = store.disabled.first()
        val updated = if (enabled) current - id.name else current + id.name
        if (updated != current) {
            store.save(updated)
        }
    }
}
