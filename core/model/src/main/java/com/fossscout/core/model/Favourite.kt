package com.fossscout.core.model

import kotlinx.serialization.Serializable

const val MAX_NOTE_LENGTH = 4_000
const val FAVOURITES_SCHEMA_VERSION = 1

@Serializable
data class Favourite(
    val appKey: AppKey,
    val displayName: String,
    val preferredStoreId: StoreId? = null,
    val note: String = "",
    val tags: Set<String> = emptySet(),
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
    val schemaVersion: Int = FAVOURITES_SCHEMA_VERSION,
) {
    fun validate() {
        require(note.length <= MAX_NOTE_LENGTH) { "Note is too long" }
        require(displayName.isNotBlank()) { "Display name is blank" }
        require(tags.size <= MAX_TAGS) { "Too many tags" }
        require(tags.none(String::isBlank)) { "Tag is blank" }
        require(tags.all { it.length <= MAX_TAG_LENGTH }) { "Tag is too long" }
    }
}

enum class InstalledState { INSTALLED, NOT_INSTALLED, UNAVAILABLE, UNKNOWN }
