package com.fossscout.core.data

import com.fossscout.core.model.Favourite
import kotlinx.coroutines.flow.Flow

interface FavouritesStore {
    val favourites: Flow<List<Favourite>>

    suspend fun save(favourites: List<Favourite>)
}
