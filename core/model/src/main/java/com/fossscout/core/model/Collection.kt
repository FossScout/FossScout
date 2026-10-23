package com.fossscout.core.model

import kotlinx.serialization.Serializable

/** Version of the persisted and exported collection shape. */
const val COLLECTION_SCHEMA_VERSION = 1

/**
 * An ordered, user-created group of saved apps.
 *
 * Items are canonical [AppKey]s, never separate favourite IDs. A collection is
 * therefore a view over the same identity used by favourites: renaming or
 * deleting a collection never rewrites the favourite that carries the details.
 * Duplicate keys are not allowed; use [CollectionsRepository][com.fossscout.core.data.CollectionsRepository]
 * to keep the order and uniqueness rules.
 */
@Serializable
data class AppCollection(
    val id: String,
    val name: String,
    val description: String = "",
    val itemKeys: List<AppKey> = emptyList(),
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
    val schemaVersion: Int = COLLECTION_SCHEMA_VERSION,
) {
    fun validate() {
        require(id.isNotBlank()) { "Collection id is blank" }
        require(name.isNotBlank()) { "Collection name is blank" }
        require(itemKeys.size == itemKeys.distinct().size) {
            "Collection contains duplicate app keys"
        }
    }
}
