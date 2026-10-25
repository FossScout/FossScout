package com.fossscout.core.handoff

/**
 * Detects installed F-Droid-compatible clients. Implementations must query the
 * explicit package names in [CompanionStores] and must not enumerate packages.
 */
interface CompanionStoreDetector {
    suspend fun detect(): List<InstalledCompanionStore>
}
