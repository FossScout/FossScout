package com.fossscout.core.data

import com.fossscout.core.model.Favourite
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow

class FakeFavouritesStore(
    initial: List<Favourite> = emptyList(),
    private var readError: Throwable? = null,
) : FavouritesStore {
    private val state = MutableStateFlow(initial)

    var saveCalls: Int = 0
        private set

    override val favourites: Flow<List<Favourite>> = flow {
        readError?.let { throw it }
        emitAll(state)
    }

    override suspend fun save(favourites: List<Favourite>) {
        saveCalls += 1
        state.value = favourites
    }
}
