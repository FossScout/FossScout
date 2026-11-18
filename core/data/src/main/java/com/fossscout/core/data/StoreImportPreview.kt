package com.fossscout.core.data

import com.fossscout.core.store.StoreDescriptor

/**
 * Read-only view of a shared store descriptor before import.
 *
 * [existingStoreName] is set when the descriptor's source id is already in the
 * local registry. Import always assigns a new local id, so the source id is only
 * used to stop the same descriptor being added twice.
 */
data class StoreImportPreview(
    val descriptor: StoreDescriptor,
    val existingStoreName: String? = null,
) {
    val canImport: Boolean get() = existingStoreName == null
}
