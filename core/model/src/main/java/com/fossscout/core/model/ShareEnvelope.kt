package com.fossscout.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Version of the offline sharing contract. */
const val SHARE_SCHEMA_VERSION = 1

/**
 * What a share envelope contains.
 *
 * The wire value is stable and lower-case so ordinary JSON tools and future
 * versions can read it without knowing Kotlin enum names.
 */
@Serializable
enum class ShareType {
    @SerialName("single_favourite")
    SINGLE_FAVOURITE,

    @SerialName("collection")
    COLLECTION,

    @SerialName("all_favourites")
    ALL_FAVOURITES,
}

/**
 * One app in a share envelope.
 *
 * [note] is nullable and is only populated when the user explicitly opted in.
 * [preferredStoreId] is an advisory reference; importers must never create a
 * store or open/install anything from it without a separate user action.
 */
@Serializable
data class ShareItem(
    val appKey: AppKey,
    val displayName: String,
    val preferredStoreId: StoreId? = null,
    val tags: Set<String> = emptySet(),
    val note: String? = null,
) {
    fun validate() {
        require(displayName.isNotBlank()) { "Shared display name is blank" }
        require(note == null || note.length <= MAX_NOTE_LENGTH) { "Shared note is too long" }
        require(tags.size <= MAX_TAGS) { "Too many shared tags" }
        require(tags.none(String::isBlank)) { "Shared tag is blank" }
        require(tags.all { it.length <= MAX_TAG_LENGTH }) { "Shared tag is too long" }
    }
}

/**
 * Portable, offline-only sharing contract for F12.
 *
 * Contract notes for S09 (full backup):
 * - The envelope is self-describing and versioned with [schemaVersion].
 * - Collection payloads reuse the same [AppCollection] identity rules: ordered
 *   canonical [AppKey]s plus name/description and an optional [collectionId].
 *   A backup can embed this envelope (or a list of them) under its own outer
 *   envelope without inventing a second collection format.
 * - [collectionId] is the conflict identity for collections. Favourite conflict
 *   identity is the canonical [AppKey]. Both are stable across devices.
 * - Notes are private and optional. Any backup or sharing writer must default
 *   to omitting them and require explicit consent before setting [ShareItem.note].
 * - Stores are references, never descriptors. Importers must not add, enable,
 *   or trust a store merely because a share envelope names one.
 *
 * S09 must not reuse this as a full-device backup envelope: it intentionally has
 * no settings, store descriptors, inventory, encryption, or checksum fields.
 */
@Serializable
data class ShareEnvelope(
    val schemaVersion: Int,
    val type: ShareType,
    val name: String,
    val description: String = "",
    val collectionId: String? = null,
    val items: List<ShareItem> = emptyList(),
    val exportedAtEpochMillis: Long? = null,
) {
    fun validate() {
        require(schemaVersion == SHARE_SCHEMA_VERSION) {
            "Unsupported share schema version: $schemaVersion"
        }
        require(name.isNotBlank()) { "Shared list name is blank" }
        require(type != ShareType.SINGLE_FAVOURITE || items.size == 1) {
            "A single-favourite share must contain exactly one item"
        }
        items.forEach(ShareItem::validate)
    }
}
