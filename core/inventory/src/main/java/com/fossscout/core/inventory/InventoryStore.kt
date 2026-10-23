package com.fossscout.core.inventory

import kotlinx.coroutines.flow.Flow

/** Local persistence contract for the last complete snapshot and hide list. */
interface InventoryStore {
    val state: Flow<InventoryState>

    suspend fun save(state: InventoryState)
}
