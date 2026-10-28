package com.fossscout.core.model

import kotlinx.serialization.Serializable

const val MATCH_OVERRIDE_SCHEMA_VERSION = 1

/** A user-selected replacement target for an installed app. */
@Serializable
data class MatchOverrideTarget(
    val appKey: AppKey,
    val displayName: String,
    val storeId: StoreId,
    val storeName: String,
) {
    init {
        require(displayName.isNotBlank()) { "Match override target name is blank" }
        require(storeName.isNotBlank()) { "Match override target store is blank" }
    }
}

/**
 * Local correction for one installed app. A null [target] means the user marked
 * the app as having no suitable match.
 */
@Serializable
data class MatchOverride(
    val installedAppKey: AppKey,
    val target: MatchOverrideTarget? = null,
    val note: String = "",
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
    val schemaVersion: Int = MATCH_OVERRIDE_SCHEMA_VERSION,
) {
    init {
        require(createdAtEpochMillis >= 0) { "Match override creation time is negative" }
        require(updatedAtEpochMillis >= 0) { "Match override update time is negative" }
    }

    val isNoMatch: Boolean get() = target == null

    fun validate() {
        require(schemaVersion == MATCH_OVERRIDE_SCHEMA_VERSION) {
            "Unsupported match override schema version: $schemaVersion"
        }
        require(note.length <= MAX_NOTE_LENGTH) { "Match override note is too long" }
        target?.let {
            require(it.displayName.isNotBlank()) { "Match override target name is blank" }
            require(it.storeName.isNotBlank()) { "Match override target store is blank" }
        }
    }
}
