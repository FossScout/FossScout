package com.fossscout.app.handoff

import android.util.Log
import com.fossscout.core.handoff.CompanionStoreDetector
import com.fossscout.core.handoff.CompanionStores
import com.fossscout.core.handoff.InstalledCompanionStore
import com.fossscout.core.inventory.InstalledAppSource
import kotlinx.coroutines.CancellationException

/**
 * Detects only the explicit package IDs in [CompanionStores]. The manifest
 * declares those packages, so the Play profile does not need broad visibility.
 */
class InventoryCompanionStoreDetector(
    private val appSource: InstalledAppSource,
) : CompanionStoreDetector {
    override suspend fun detect(): List<InstalledCompanionStore> = buildList {
        CompanionStores.all.forEach { store ->
            val app = try {
                appSource.find(store.packageName)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (throwable: Throwable) {
                Log.w(TAG, "Could not query ${store.packageName}", throwable)
                null
            }
            if (app != null) {
                add(
                    InstalledCompanionStore(
                        store = store,
                        enabled = app.isEnabled,
                        versionName = app.versionName,
                    ),
                )
            }
        }
    }

    private companion object {
        const val TAG = "CompanionStoreDetector"
    }
}
