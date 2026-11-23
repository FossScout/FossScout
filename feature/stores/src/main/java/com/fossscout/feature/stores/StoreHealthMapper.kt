package com.fossscout.feature.stores

import com.fossscout.core.index.StoreSnapshot
import com.fossscout.core.index.SyncState
import com.fossscout.core.store.StoreHealth
import com.fossscout.core.store.StoreHealthStatus
import java.util.Locale

internal fun syncHealth(
    previous: StoreHealth,
    state: SyncState,
    snapshot: StoreSnapshot?,
    error: String?,
    latencyMillis: Long,
): StoreHealth {
    val elapsed = latencyMillis.coerceAtLeast(0L)
    return if (state == SyncState.SUCCEEDED) {
        StoreHealth(
            status = StoreHealthStatus.OK,
            lastSyncEpochMillis = snapshot?.fetchedAtEpochMillis
                ?: previous.lastSyncEpochMillis,
            itemCount = snapshot?.packageCount ?: previous.itemCount,
            latencyMillis = elapsed,
        )
    } else {
        previous.copy(
            status = StoreHealthStatus.ERROR,
            error = error,
            latencyMillis = elapsed,
        )
    }
}

internal fun formatSyncLatency(millis: Long): String {
    val elapsed = millis.coerceAtLeast(0L)
    return if (elapsed < 1_000L) {
        "$elapsed ms"
    } else {
        String.format(Locale.ROOT, "%.1f s", elapsed / 1_000.0)
    }
}
