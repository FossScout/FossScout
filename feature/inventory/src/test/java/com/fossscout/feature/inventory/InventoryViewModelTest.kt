package com.fossscout.feature.inventory

import com.fossscout.core.inventory.AppProfile
import com.fossscout.core.inventory.Classification
import com.fossscout.core.inventory.InstalledApp
import com.fossscout.core.inventory.InstalledAppSource
import com.fossscout.core.inventory.InventoryEntry
import com.fossscout.core.inventory.InventoryEnumeration
import com.fossscout.core.inventory.InventoryRepository
import com.fossscout.core.inventory.InventorySnapshot
import com.fossscout.core.inventory.InventoryState
import com.fossscout.core.inventory.InventoryStore
import com.fossscout.core.inventory.ScanMode
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class InventoryViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val example = app("org.example.app", "Example")
    private val other = app("org.example.other", "Other")

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `manual package check updates the snapshot and leaves checking state`() =
        runTest(dispatcher) {
            val store = FakeStore()
            val viewModel = viewModel(
                repository = repository(store, FakeSource(listOf(example))),
            )
            advanceUntilIdle()

            viewModel.onQueryChange("org.example.app")
            viewModel.checkInput()
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertEquals(ScanMode.MANUAL, state.snapshot?.scanMode)
            assertEquals("org.example.app", state.entries.single().identity)
            assertEquals(Classification.UNKNOWN, state.entries.single().classification)
            assertFalse(state.isChecking)
            assertFalse(state.isLoading)
            assertEquals(1, store.saveCalls)
        }

    @Test
    fun `free-text lookup with one match checks that app`() = runTest(dispatcher) {
        val viewModel = viewModel(
            repository = repository(FakeStore(), FakeSource(listOf(example))),
        )
        advanceUntilIdle()

        viewModel.onQueryChange("Example")
        viewModel.checkInput()
        advanceUntilIdle()

        assertEquals(
            "org.example.app",
            viewModel.uiState.value.snapshot?.entries?.single()?.identity,
        )
    }

    @Test
    fun `ambiguous free-text lookup shows a picker and checks the selection`() =
        runTest(dispatcher) {
            val second = app("org.example.example-two", "Example Two")
            val store = FakeStore()
            val viewModel = viewModel(
                repository = repository(store, FakeSource(listOf(example, second))),
            )
            advanceUntilIdle()

            viewModel.onQueryChange("Example")
            viewModel.checkInput()
            advanceUntilIdle()

            assertEquals(2, viewModel.uiState.value.candidates.size)
            assertNull(viewModel.uiState.value.snapshot)
            assertEquals(0, store.saveCalls)

            viewModel.selectCandidate(second)
            advanceUntilIdle()

            assertEquals(
                second.packageName,
                viewModel.uiState.value.snapshot?.entries?.single()?.identity,
            )
            assertTrue(viewModel.uiState.value.candidates.isEmpty())
        }

    @Test
    fun `unresolved free text is recorded as unavailable`() = runTest(dispatcher) {
        val viewModel = viewModel(
            repository = repository(FakeStore(), FakeSource(emptyList())),
        )
        advanceUntilIdle()

        viewModel.onQueryChange("No Such App")
        viewModel.checkInput()
        advanceUntilIdle()

        val entry = viewModel.uiState.value.snapshot?.entries?.single()
        assertEquals("No Such App", entry?.identity)
        assertEquals(Classification.UNAVAILABLE, entry?.classification)
    }

    @Test
    fun `full scan action is unavailable without broad visibility`() = runTest(dispatcher) {
        val source = FakeSource(listOf(example), supportsFullScan = false)
        val store = FakeStore()
        val viewModel = viewModel(
            repository = repository(store, source, allowFullScan = false),
        )
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.canFullScan)
        viewModel.startFullScan()
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.snapshot)
        assertEquals(0, source.enumerateCalls)
        assertEquals(0, store.saveCalls)
    }

    @Test
    fun `full scan action persists a full snapshot`() = runTest(dispatcher) {
        val source = FakeSource(listOf(example, other))
        val viewModel = viewModel(repository = repository(FakeStore(), source))
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.canFullScan)
        viewModel.startFullScan()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(ScanMode.FULL, state.snapshot?.scanMode)
        assertEquals(2, state.entries.size)
        assertFalse(state.isScanning)
    }

    @Test
    fun `cancelling a scan keeps the previous snapshot and stops progress`() =
        runTest(dispatcher) {
            val previous = InventorySnapshot(
                scanMode = ScanMode.FULL,
                capturedAtEpochMillis = 50L,
                entries = listOf(
                    InventoryEntry(
                        identity = example.packageName,
                        app = example,
                        classification = Classification.FOSS,
                    ),
                ),
            )
            val store = FakeStore(InventoryState(snapshot = previous))
            val source = FakeSource(listOf(other), blockEnumeration = true)
            val viewModel = viewModel(repository = repository(store, source))
            advanceUntilIdle()

            viewModel.startFullScan()
            advanceUntilIdle()
            assertTrue(viewModel.uiState.value.isScanning)

            viewModel.cancelScan()
            advanceUntilIdle()

            assertFalse(viewModel.uiState.value.isScanning)
            assertEquals(previous, viewModel.uiState.value.snapshot)
            assertEquals(previous, store.current().snapshot)
            assertEquals(0, store.saveCalls)
        }

    @Test
    fun `filters and the hide list change the list without rescanning`() =
        runTest(dispatcher) {
            val system = app("org.example.system", "System", isSystem = true)
            val disabled = app("org.example.disabled", "Disabled", isEnabled = false)
            val work = app(
                "org.example.work",
                "Work",
                workProfile = true,
            )
            val source = FakeSource(listOf(example, system, disabled, work))
            val viewModel = viewModel(repository = repository(FakeStore(), source))
            advanceUntilIdle()

            viewModel.startFullScan()
            advanceUntilIdle()
            assertEquals(4, viewModel.uiState.value.entries.size)
            assertEquals(1, source.enumerateCalls)

            viewModel.setIncludeSystemApps(false)
            assertEquals(3, viewModel.uiState.value.entries.size)

            viewModel.setIncludeDisabledApps(false)
            assertEquals(2, viewModel.uiState.value.entries.size)

            viewModel.hide(example.packageName)
            advanceUntilIdle()
            assertEquals(1, viewModel.uiState.value.entries.size)
            assertEquals(1, viewModel.uiState.value.hiddenCount)

            viewModel.setIncludeHiddenApps(true)
            assertEquals(2, viewModel.uiState.value.entries.size)
            assertEquals(1, source.enumerateCalls)
        }

    @Test
    fun `stale snapshots are marked from the repository clock`() = runTest(dispatcher) {
        val old = InventorySnapshot(
            scanMode = ScanMode.FULL,
            capturedAtEpochMillis = 0L,
        )
        val repository = InventoryRepository(
            store = FakeStore(InventoryState(snapshot = old)),
            source = FakeSource(emptyList()),
            now = { Long.MAX_VALUE / 2 },
        )
        val viewModel = viewModel(repository = repository)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isStale)
    }

    @Test
    fun `read failures surface an error instead of crashing`() = runTest(dispatcher) {
        val viewModel = viewModel(
            repository = repository(
                FakeStore(readError = IOException("unreadable")),
                FakeSource(emptyList()),
            ),
        )
        advanceUntilIdle()

        assertEquals(InventoryError.READ_FAILED, viewModel.uiState.value.error)
        assertFalse(viewModel.uiState.value.isLoading)
    }

    private fun viewModel(repository: InventoryRepository) = InventoryViewModel(repository)

    private fun repository(
        store: FakeStore,
        source: FakeSource,
        allowFullScan: Boolean = source.supportsFullScan,
    ) = InventoryRepository(
        store = store,
        source = source,
        allowFullScan = allowFullScan,
        now = { 1_000L },
    )

    private fun app(
        packageName: String,
        label: String,
        isSystem: Boolean = false,
        isEnabled: Boolean = true,
        workProfile: Boolean = false,
    ) = InstalledApp(
        packageName = packageName,
        label = label,
        versionName = "1.0",
        versionCode = 1L,
        installerPackage = "com.example.store",
        isSystemApp = isSystem,
        isEnabled = isEnabled,
        profile = AppProfile(userId = 0, isWorkProfile = workProfile),
    )
}

private class FakeStore(
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

private class FakeSource(
    private val apps: List<InstalledApp>,
    override val supportsFullScan: Boolean = true,
    private val blockEnumeration: Boolean = false,
) : InstalledAppSource {
    var enumerateCalls: Int = 0
        private set

    override suspend fun find(packageName: String): InstalledApp? =
        apps.firstOrNull { it.packageName == packageName }

    override suspend fun search(query: String, limit: Int): List<InstalledApp> =
        apps.filter { app ->
            app.packageName.contains(query, ignoreCase = true) ||
                app.label.contains(query, ignoreCase = true)
        }.take(limit)

    override suspend fun enumerate(): InventoryEnumeration {
        enumerateCalls += 1
        if (blockEnumeration) awaitCancellation()
        return InventoryEnumeration(apps)
    }
}
