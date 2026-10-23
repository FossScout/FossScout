package com.fossscout.feature.stores

import com.fossscout.core.data.StoreRegistryCodec
import com.fossscout.core.data.StoreRegistryRepository
import com.fossscout.core.data.StoreRegistryStore
import com.fossscout.core.model.StoreId
import com.fossscout.core.store.AdapterType
import com.fossscout.core.store.BuiltInStores
import com.fossscout.core.store.LinkOnlyReason
import com.fossscout.core.store.StoreDescriptor
import com.fossscout.core.store.StoreMode
import com.fossscout.core.store.StoreRegistry
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class StoresViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `fresh state lists both predefined stores`() = runTest(dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertEquals(
            listOf(BuiltInStores.FDROID_ID, BuiltInStores.IZZY_ON_DROID_ID),
            state.entries.map { it.descriptor.id.value },
        )
        assertNull(state.error)
    }

    @Test
    fun `HTTP add is blocked until the user opts in`() = runTest(dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.startAdd()
        val editor = requireNotNull(viewModel.uiState.value.editor)
        viewModel.updateEditor(
            editor.copy(
                name = "HTTP store",
                homepage = "http://insecure.example",
                adapterType = AdapterType.DEEP_LINK,
            ),
        )
        viewModel.saveEditor()
        advanceUntilIdle()

        var state = viewModel.uiState.value
        assertEquals(StoresError.HTTP_OPT_IN_REQUIRED, state.error)
        assertEquals(2, state.entries.size)
        assertNotNull(state.editor)

        viewModel.updateEditor(requireNotNull(state.editor).copy(allowInsecureHttp = true))
        viewModel.saveEditor()
        advanceUntilIdle()

        state = viewModel.uiState.value
        assertNull(state.error)
        assertNull(state.editor)
        assertEquals(3, state.entries.size)
        assertTrue(state.entries.last().descriptor.allowInsecureHttp)
    }

    @Test
    fun `link-only store is visible with a reason and excluded from matching`() = runTest(dispatcher) {
        val fakeStore = FakeStoreRegistryStore()
        val viewModel = viewModel(fakeStore)
        advanceUntilIdle()

        viewModel.startAdd()
        viewModel.updateEditor(
            requireNotNull(viewModel.uiState.value.editor).copy(
                name = "Link store",
                homepage = "https://code.example/project",
                adapterType = AdapterType.SOURCE_REPOSITORY,
                indexUrl = "",
            ),
        )
        viewModel.saveEditor()
        advanceUntilIdle()

        val entry = viewModel.uiState.value.entries.last()
        assertEquals(StoreMode.LINK_ONLY, entry.availability.mode)
        assertEquals(LinkOnlyReason.ADAPTER_UNSUPPORTED, entry.availability.reason)
        assertFalse(entry.matchable)
        assertTrue(entry.excludedFromMatching)
    }

    @Test
    fun `edit enable disable reorder and delete update the visible registry`() = runTest(dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()
        val fdroid = viewModel.uiState.value.entries
            .first { it.descriptor.id.value == BuiltInStores.FDROID_ID }
            .descriptor
        val izzyId = StoreId(BuiltInStores.IZZY_ON_DROID_ID)

        viewModel.startEdit(fdroid)
        viewModel.updateEditor(
            requireNotNull(viewModel.uiState.value.editor).copy(name = "F-Droid main"),
        )
        viewModel.saveEditor()
        advanceUntilIdle()
        assertEquals(
            "F-Droid main",
            viewModel.uiState.value.entries.first().descriptor.name,
        )

        viewModel.setEnabled(izzyId, false)
        advanceUntilIdle()
        assertFalse(
            viewModel.uiState.value.entries
                .first { it.descriptor.id == izzyId }
                .matchable,
        )

        viewModel.moveUp(izzyId)
        advanceUntilIdle()
        assertEquals(
            izzyId,
            viewModel.uiState.value.entries.first().descriptor.id,
        )

        val izzy = viewModel.uiState.value.entries.first().descriptor
        viewModel.requestDelete(izzy)
        viewModel.confirmDelete()
        advanceUntilIdle()
        assertEquals(1, viewModel.uiState.value.entries.size)
        assertNull(viewModel.uiState.value.deleteRequest)
    }

    @Test
    fun `export produces a portable versioned descriptor`() = runTest(dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.export(StoreId(BuiltInStores.FDROID_ID))
        advanceUntilIdle()

        val request = requireNotNull(viewModel.uiState.value.exportRequest)
        val decoded = StoreRegistryCodec().decodeDescriptor(request.json)
        assertEquals(BuiltInStores.fdroid, decoded)
        assertTrue(request.json.contains("\"schemaVersion\":1"))
        assertEquals("F-Droid", request.name)
    }

    @Test
    fun `read failure is visible and leaves the registry empty`() = runTest(dispatcher) {
        val viewModel = viewModel(
            FakeStoreRegistryStore(readError = IOException("unreadable")),
        )
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(StoresError.READ_FAILED, state.error)
        assertFalse(state.isLoading)
        assertTrue(state.entries.isEmpty())
    }

    private fun viewModel(
        store: FakeStoreRegistryStore = FakeStoreRegistryStore(),
    ): StoresViewModel {
        var nextId = 0
        return StoresViewModel(
            StoreRegistryRepository(
                store = store,
                idFactory = { StoreId("custom-${nextId++}") },
            ),
        )
    }
}

private class FakeStoreRegistryStore(
    initial: StoreRegistry = BuiltInStores.registry(),
    var readError: Throwable? = null,
) : StoreRegistryStore {
    val state = MutableStateFlow(initial)

    override val registry: Flow<StoreRegistry> = flow {
        readError?.let { throw it }
        emitAll(state)
    }

    override suspend fun save(registry: StoreRegistry) {
        state.value = registry
    }
}
