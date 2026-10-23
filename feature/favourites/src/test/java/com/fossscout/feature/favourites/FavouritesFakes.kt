package com.fossscout.feature.favourites

import com.fossscout.core.data.StoreRegistryStore
import com.fossscout.core.inventory.InstalledStateProvider
import com.fossscout.core.model.InstalledState
import com.fossscout.core.store.BuiltInStores
import com.fossscout.core.store.StoreRegistry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeStoreRegistryStore(
    initial: StoreRegistry = BuiltInStores.registry(),
) : StoreRegistryStore {
    private val state = MutableStateFlow(initial)

    override val registry: Flow<StoreRegistry> = state

    override suspend fun save(registry: StoreRegistry) {
        state.value = registry
    }
}

class FakeInstalledStateProvider(
    var states: Map<String, InstalledState> = emptyMap(),
    var error: Throwable? = null,
) : InstalledStateProvider {
    var calls: Int = 0
        private set

    override suspend fun stateFor(packageName: String): InstalledState {
        calls += 1
        error?.let { throw it }
        return states[packageName] ?: InstalledState.UNKNOWN
    }
}
