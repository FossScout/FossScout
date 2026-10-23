package com.fossscout.core.data

import com.fossscout.core.model.AppCollection
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow

class FakeCollectionsStore(
    initial: List<AppCollection> = emptyList(),
    var readError: Throwable? = null,
) : CollectionsStore {
    private val state = MutableStateFlow(initial)

    var saveCalls: Int = 0
        private set

    override val collections: Flow<List<AppCollection>> = flow {
        readError?.let { throw it }
        emitAll(state)
    }

    override suspend fun save(collections: List<AppCollection>) {
        saveCalls += 1
        state.value = collections
    }
}
