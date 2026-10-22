package com.fossscout.core.model

import kotlinx.serialization.Serializable

@Serializable
data class AppCollection(
    val id: String,
    val name: String,
    val description: String = "",
    val itemKeys: List<AppKey> = emptyList(),
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
)
