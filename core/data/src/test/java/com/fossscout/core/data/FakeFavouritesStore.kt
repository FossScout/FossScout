package com.fossscout.core.data

import com.fossscout.core.model.Favourite
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeFavouritesStore : FavouritesStore {
    private val state = MutableStateFlow<List<Favourite>>(emptyList())

    override val favourites: Flow<List<Favourite>> = state

    override suspend fun save(favourites: List<Favourite>) {
        state.value = favourites
    }
}
