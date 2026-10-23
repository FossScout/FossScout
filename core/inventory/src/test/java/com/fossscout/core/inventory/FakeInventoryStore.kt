package com.fossscout.core.inventory

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow

class FakeInventoryStore(
    initial: InventoryState = InventoryState(),
    var readError: Throwable? = null,
) : InventoryStore {
    private val stateFlow = MutableStateFlow(initial)

    var saveCalls: Int = 0
        private set

    override val state: Flow<InventoryState> = flow {
        readError?.let { throw it }
        emitAll(stateFlow)
    }

    override suspend fun save(state: InventoryState) {
        saveCalls += 1
        stateFlow.value = state
    }

    fun current(): InventoryState = stateFlow.value
}
