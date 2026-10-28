package com.fossscout.core.data

import com.fossscout.core.model.AppKey
import com.fossscout.core.model.MatchOverride
import com.fossscout.core.model.MatchOverrideTarget
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Local source of truth for user match corrections. */
class MatchOverridesRepository(
    private val store: MatchOverridesStore,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val mutex = Mutex()

    val overrides: Flow<List<MatchOverride>> = store.overrides

    suspend fun setTarget(
        installedAppKey: AppKey,
        target: MatchOverrideTarget,
        note: String = "",
    ): MatchOverride = upsert(
        installedAppKey = installedAppKey,
        target = target,
        note = note,
    )

    suspend fun setNoMatch(
        installedAppKey: AppKey,
        note: String = "",
    ): MatchOverride = upsert(
        installedAppKey = installedAppKey,
        target = null,
        note = note,
    )

    suspend fun remove(installedAppKey: AppKey) = mutex.withLock {
        val current = store.overrides.first()
        if (current.none { it.installedAppKey == installedAppKey }) return@withLock
        store.save(current.filterNot { it.installedAppKey == installedAppKey })
    }

    /** Atomically replaces every override; used by explicit backup restore. */
    suspend fun replaceAll(overrides: List<MatchOverride>) = mutex.withLock {
        overrides.forEach(MatchOverride::validate)
        require(overrides.map(MatchOverride::installedAppKey).toSet().size == overrides.size) {
            "Duplicate match override app keys"
        }
        store.save(overrides)
    }

    private suspend fun upsert(
        installedAppKey: AppKey,
        target: MatchOverrideTarget?,
        note: String,
    ): MatchOverride = mutex.withLock {
        val current = store.overrides.first()
        val existing = current.firstOrNull { it.installedAppKey == installedAppKey }
        val timestamp = now()
        val override = MatchOverride(
            installedAppKey = installedAppKey,
            target = target,
            note = note.trim(),
            createdAtEpochMillis = existing?.createdAtEpochMillis ?: timestamp,
            updatedAtEpochMillis = timestamp,
        )
        override.validate()
        store.save(
            if (existing != null) {
                current.map { if (it.installedAppKey == installedAppKey) override else it }
            } else {
                current + override
            },
        )
        override
    }
}
