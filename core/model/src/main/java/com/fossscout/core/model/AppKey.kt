package com.fossscout.core.model

import kotlinx.serialization.Serializable

/**
 * Canonical identity for a favourite. Package names are used where known;
 * otherwise a stable catalog or source key is used. The stored value is always
 * trimmed and non-blank so the same app cannot appear under two spellings.
 */
@Serializable
@JvmInline
value class AppKey(val value: String) {
    init {
        require(value.isNotBlank()) { "App key is blank" }
        require(value == value.trim()) { "App key must not have surrounding whitespace" }
    }

    companion object {
        /** Builds a canonical key from user or catalog input. */
        fun from(raw: String): AppKey = AppKey(raw.trim())
    }
}

@Serializable
@JvmInline
value class StoreId(val value: String) {
    init {
        require(value.isNotBlank()) { "Store id is blank" }
    }
}
