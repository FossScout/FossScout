package com.fossscout.core.data

import com.fossscout.core.model.AppCollection
import kotlinx.coroutines.flow.Flow

interface CollectionsStore {
    val collections: Flow<List<AppCollection>>

    suspend fun save(collections: List<AppCollection>)
}
