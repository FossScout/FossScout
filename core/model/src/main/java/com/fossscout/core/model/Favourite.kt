package com.fossscout.core.model

import kotlinx.serialization.Serializable

const val MAX_NOTE_LENGTH = 4_000

@Serializable
data class Favourite(
    val appKey: AppKey,
    val displayName: String,
    val preferredStoreId: StoreId? = null,
    val note: String = "",
    val tags: Set<String> = emptySet(),
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
) {
    fun validate() {
        require(note.length <= MAX_NOTE_LENGTH) { "Note is too long" }
        require(displayName.isNotBlank()) { "Display name is blank" }
    }
}

enum class InstalledState { INSTALLED, NOT_INSTALLED, UNAVAILABLE, UNKNOWN }
