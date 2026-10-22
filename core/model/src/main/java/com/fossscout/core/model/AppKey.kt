package com.fossscout.core.model

import kotlinx.serialization.Serializable

@Serializable
@JvmInline
value class AppKey(val value: String) {
    init {
        require(value.isNotBlank()) { "App key is blank" }
    }
}

@Serializable
@JvmInline
value class StoreId(val value: String) {
    init {
        require(value.isNotBlank()) { "Store id is blank" }
    }
}
