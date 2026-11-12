package com.fossscout.feature.matching

import com.fossscout.core.index.SignatureStatus
import com.fossscout.core.index.StoreSnapshot
import com.fossscout.core.inventory.InstalledApp
import com.fossscout.core.inventory.InstalledAppSource
import com.fossscout.core.inventory.InventoryEnumeration
import com.fossscout.core.matching.CatalogEntry
import com.fossscout.core.matching.MatchLevel
import com.fossscout.core.matching.MatchOutcome
import com.fossscout.core.matching.MatchingRepository
import com.fossscout.core.matching.StoreCatalog
import com.fossscout.core.matching.StoreCatalogData
import com.fossscout.core.matching.TrackerConfidence
import com.fossscout.core.matching.TrackerDatasetInfo
import com.fossscout.core.matching.TrackerMatch
import com.fossscout.core.matching.TrackerScanResult
import com.fossscout.core.matching.TrackerScanSource
import com.fossscout.core.matching.TrackerScanStatus
import com.fossscout.core.model.AppKey
import com.fossscout.core.model.MatchOverride
import com.fossscout.core.model.MatchOverrideTarget
import com.fossscout.core.model.StoreId
import com.fossscout.core.store.AdapterType
import com.fossscout.core.store.StoreAvailability
import com.fossscout.core.store.StoreDescriptor
import com.fossscout.core.store.StoreEntry
import com.fossscout.core.store.StoreMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
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
class MatchingViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val app = InstalledApp(
        packageName = "org.example.app",
        label = "Example",
        signerSha256 = "aaaa",
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial app loads an exact match`() = runTest(dispatcher) {
        val viewModel = viewModel(
            initialApp = app,
        )
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(MatchOutcome.MATCH, state.decision?.outcome)
        assertEquals(MatchLevel.L1, state.decision?.level)
        assertFalse(state.isLoading)
        assertEquals("org.example.app", state.query)
    }

    @Test
    fun `manual check refreshes installed permissions from package manager`() = runTest(dispatcher) {
        val fresh = app.copy(
            requestedPermissions = listOf("android.permission.CAMERA"),
        )
        val viewModel = viewModel(
            source = FakeSource(listOf(fresh)),
        )

        viewModel.matchApp(app)
        advanceUntilIdle()

        assertEquals(
            listOf("android.permission.CAMERA"),
            viewModel.uiState.value.installedApp?.requestedPermissions,
        )
    }

    @Test
    fun `package input keeps the installed app for comparison`() = runTest(dispatcher) {
        val viewModel = viewModel(
            source = FakeSource(listOf(app)),
        )
        viewModel.onQueryChange("org.example.app")
        viewModel.checkInput()
        advanceUntilIdle()

        assertEquals("org.example.app", viewModel.uiState.value.installedApp?.packageName)
    }

    @Test
    fun `free text with one installed match checks that app`() = runTest(dispatcher) {
        val viewModel = viewModel(
            source = FakeSource(listOf(app)),
        )
        viewModel.onQueryChange("Example")
        viewModel.checkInput()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(MatchOutcome.MATCH, state.decision?.outcome)
        assertEquals("org.example.app", state.query)
        assertTrue(state.candidates.isEmpty())
    }

    @Test
    fun `ambiguous free text shows candidates and selection matches`() = runTest(dispatcher) {
        val second = app.copy(packageName = "org.example.two", label = "Example Two")
        val viewModel = viewModel(source = FakeSource(listOf(app, second)))

        viewModel.onQueryChange("Example")
        viewModel.checkInput()
        advanceUntilIdle()

        assertEquals(2, viewModel.uiState.value.candidates.size)
        assertNull(viewModel.uiState.value.decision)

        viewModel.selectCandidate(second)
        advanceUntilIdle()

        assertEquals("org.example.two", viewModel.uiState.value.query)
        assertNotNull(viewModel.uiState.value.decision)
        assertTrue(viewModel.uiState.value.candidates.isEmpty())
    }

    @Test
    fun `unresolved free text still produces a coverage-aware result`() = runTest(dispatcher) {
        val viewModel = viewModel(source = FakeSource(emptyList()))

        viewModel.onQueryChange("Maps")
        viewModel.checkInput()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(MatchOutcome.ALTERNATIVES, state.decision?.outcome)
        assertEquals("Maps", state.query)
    }

    @Test
    fun `hide and undo update only the visible suggestions`() = runTest(dispatcher) {
        val viewModel = viewModel(source = FakeSource(emptyList()))

        viewModel.onQueryChange("Maps")
        viewModel.checkInput()
        advanceUntilIdle()

        val suggestion = viewModel.uiState.value.visibleAlternatives.single()
        viewModel.hideAlternative(suggestion.packageName)

        assertTrue(viewModel.uiState.value.visibleAlternatives.isEmpty())
        assertEquals(suggestion, viewModel.uiState.value.lastHiddenAlternative)

        viewModel.undoHide()

        assertEquals(1, viewModel.uiState.value.visibleAlternatives.size)
        assertNull(viewModel.uiState.value.lastHiddenAlternative)
    }

    @Test
    fun `disabled alternatives are not requested`() = runTest(dispatcher) {
        val viewModel = viewModel(
            source = FakeSource(emptyList()),
        )
        viewModel.setIncludeAlternatives(false)

        viewModel.onQueryChange("Maps")
        viewModel.checkInput()
        advanceUntilIdle()

        assertEquals(MatchOutcome.NOT_FOUND, viewModel.uiState.value.decision?.outcome)
        assertTrue(viewModel.uiState.value.visibleAlternatives.isEmpty())
    }

    @Test
    fun `blank query shows a validation error`() = runTest(dispatcher) {
        val viewModel = viewModel()

        viewModel.checkInput()

        assertEquals(MatchError.QUERY_REQUIRED, viewModel.uiState.value.error)
    }

    @Test
    fun `search failure is reported separately from matching failure`() = runTest(dispatcher) {
        val viewModel = viewModel(source = FakeSource(failure = true))

        viewModel.onQueryChange("Example")
        viewModel.checkInput()
        advanceUntilIdle()

        assertEquals(MatchError.SEARCH_FAILED, viewModel.uiState.value.error)
        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun `matching failure leaves cached indexes untouched and reports an error`() =
        runTest(dispatcher) {
            val viewModel = viewModel(storeEntries = flow { error("registry read failed") })

            viewModel.onQueryChange("org.example.app")
            viewModel.checkInput()
            advanceUntilIdle()

            assertEquals(MatchError.MATCH_FAILED, viewModel.uiState.value.error)
            assertNull(viewModel.uiState.value.decision)
            assertFalse(viewModel.uiState.value.isLoading)
        }

    @Test
    fun `correction dialog searches and saves a selected target`() = runTest(dispatcher) {
        val overrides = MutableStateFlow<List<MatchOverride>>(emptyList())
        val viewModel = viewModel(
            initialApp = app,
            overrides = overrides,
            setCorrection = { appKey, target, note ->
                overrides.value = listOf(
                    MatchOverride(
                        installedAppKey = appKey,
                        target = target,
                        note = note,
                        createdAtEpochMillis = 1,
                        updatedAtEpochMillis = 2,
                    ),
                )
            },
            removeCorrection = { overrides.value = emptyList() },
        )
        advanceUntilIdle()

        viewModel.startCorrection()
        advanceUntilIdle()
        assertEquals("Example", viewModel.uiState.value.correctionEditor?.query)
        assertTrue(viewModel.uiState.value.correctionEditor?.hasSearched == true)

        viewModel.onCorrectionQueryChange("Open")
        assertFalse(viewModel.uiState.value.correctionEditor?.hasSearched == true)
        viewModel.searchCorrectionTargets()
        advanceUntilIdle()
        val target = viewModel.uiState.value.correctionEditor?.results?.single()
        assertNotNull(target)

        viewModel.selectCorrectionTarget(target!!)
        viewModel.onCorrectionNoteChange("private note")
        viewModel.saveCorrection()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertNull(state.correctionEditor)
        assertEquals("private note", state.decision?.correction?.note)
        assertEquals(
            "org.maps.alt",
            state.decision?.correction?.target?.appKey?.value,
        )
    }

    @Test
    fun `no-match correction is saved and can be undone`() = runTest(dispatcher) {
        val overrides = MutableStateFlow<List<MatchOverride>>(emptyList())
        val viewModel = viewModel(
            initialApp = app,
            overrides = overrides,
            setCorrection = { appKey, target, note ->
                overrides.value = listOf(
                    MatchOverride(
                        installedAppKey = appKey,
                        target = target,
                        note = note,
                        createdAtEpochMillis = 1,
                        updatedAtEpochMillis = 2,
                    ),
                )
            },
            removeCorrection = { overrides.value = emptyList() },
        )
        advanceUntilIdle()

        viewModel.startCorrection()
        advanceUntilIdle()
        viewModel.saveNoMatch()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.decision?.correction?.isNoMatch == true)

        viewModel.undoCorrection()
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.decision?.correction)
    }

    @Test
    fun `saving without a target reports a validation error`() = runTest(dispatcher) {
        val viewModel = viewModel(
            initialApp = app,
            setCorrection = { _, _, _ -> },
            removeCorrection = {},
        )
        advanceUntilIdle()

        viewModel.startCorrection()
        advanceUntilIdle()
        viewModel.saveCorrection()

        assertEquals(
            MatchCorrectionError.TARGET_REQUIRED,
            viewModel.uiState.value.correctionEditor?.error,
        )
    }

    @Test
    fun `tracker scan exposes dataset and installed matches`() = runTest(dispatcher) {
        val source = FakeTrackerScanSource(
            result = TrackerScanResult(
                status = TrackerScanStatus.COMPLETE,
                matches = listOf(
                    TrackerMatch(
                        name = "Analytics",
                        confidence = TrackerConfidence.KNOWN,
                        matchedSignatures = listOf("com.example.tracker."),
                    ),
                ),
                scannedFiles = 1,
            ),
        )

        val viewModel = viewModel(
            initialApp = app,
            trackerScanSource = source,
        )
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.tracker)
        assertTrue(viewModel.startTrackerScan())
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("2026-11-10", state.tracker?.dataset?.version)
        assertEquals(listOf("Analytics"), state.tracker?.installed?.matches?.map(TrackerMatch::name))
        assertFalse(state.isTrackerScanning)
    }

    @Test
    fun `tracker scan failure does not fail the match`() = runTest(dispatcher) {
        val source = FakeTrackerScanSource(failure = true)

        val viewModel = viewModel(
            initialApp = app,
            trackerScanSource = source,
        )
        advanceUntilIdle()

        assertTrue(viewModel.startTrackerScan())
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(MatchOutcome.MATCH, state.decision?.outcome)
        assertEquals(TrackerScanStatus.UNAVAILABLE, state.tracker?.installed?.status)
        assertFalse(state.isTrackerScanning)
    }

    private fun viewModel(
        source: FakeSource = FakeSource(listOf(app)),
        storeEntries: Flow<List<StoreEntry>> = flowOf(listOf(entry())),
        initialQuery: String? = null,
        initialApp: InstalledApp? = null,
        includeAlternatives: Boolean = true,
        overrides: Flow<List<MatchOverride>> = flowOf(emptyList()),
        setCorrection: (suspend (AppKey, MatchOverrideTarget?, String) -> Unit)? = null,
        removeCorrection: (suspend (AppKey) -> Unit)? = null,
        trackerScanSource: TrackerScanSource? = null,
    ): MatchingViewModel = MatchingViewModel(
        repository = MatchingRepository(
            storeEntries = storeEntries,
            snapshots = flowOf(mapOf("store-1" to snapshot())),
            catalog = FakeCatalog(),
            now = { NOW },
            freshnessWindowMillis = FRESHNESS,
            overrides = overrides,
        ),
        appSource = source,
        includeAlternatives = includeAlternatives,
        initialQuery = initialQuery,
        initialApp = initialApp,
        setCorrection = setCorrection,
        removeCorrection = removeCorrection,
        trackerScanSource = trackerScanSource,
    )

    private fun entry() = StoreEntry(
        descriptor = StoreDescriptor(
            id = StoreId("store-1"),
            name = "Store 1",
            homepage = "https://store.example",
            adapterType = AdapterType.FDROID_V2,
            indexUrl = "https://store.example/repo",
        ),
        availability = StoreAvailability(StoreMode.MATCHABLE),
    )

    private fun snapshot() = StoreSnapshot(
        storeId = "store-1",
        fetchedAtEpochMillis = NOW - 1_000L,
        packageCount = 2,
        payloadPath = "store-1.ndjson",
        payloadSizeBytes = 10L,
        signatureStatus = SignatureStatus.VERIFIED,
        sourceUrl = "https://store.example/repo",
    )

    private class FakeCatalog : StoreCatalog {
        override suspend fun load(snapshot: StoreSnapshot): StoreCatalogData =
            StoreCatalogData(
                storeId = snapshot.storeId,
                entries = listOf(
                    CatalogEntry(
                        packageName = "org.example.app",
                        name = "Example",
                        summary = "An example app",
                        categories = listOf("Tools"),
                        license = "GPL-3.0-only",
                        sourceCode = "https://example.org/src",
                        signerSha256 = listOf("aaaa"),
                    ),
                    CatalogEntry(
                        packageName = "org.maps.alt",
                        name = "Open Maps",
                        summary = "Offline maps",
                        categories = listOf("Maps & Navigation"),
                        license = "Apache-2.0",
                        sourceCode = "https://example.org/maps",
                    ),
                ),
            )
    }

    private class FakeSource(
        private val apps: List<InstalledApp> = emptyList(),
        private val failure: Boolean = false,
    ) : InstalledAppSource {
        override val supportsFullScan: Boolean = false

        override suspend fun find(packageName: String): InstalledApp? =
            apps.firstOrNull { it.packageName == packageName }

        override suspend fun search(query: String, limit: Int): List<InstalledApp> {
            if (failure) error("search failed")
            val normalized = query.trim().lowercase()
            return apps.filter {
                it.packageName.lowercase().contains(normalized) ||
                    it.label.lowercase().contains(normalized)
            }.take(limit)
        }

        override suspend fun enumerate(): InventoryEnumeration = InventoryEnumeration(apps)
    }

    private class FakeTrackerScanSource(
        private val result: TrackerScanResult = TrackerScanResult(),
        private val failure: Boolean = false,
    ) : TrackerScanSource {
        override val datasetInfo = TrackerDatasetInfo(
            name = "Test dataset",
            version = "2026-11-10",
            licenseName = "ODbL 1.0",
            licenseUrl = "https://example.org/license",
            sourceUrl = "https://example.org/source",
        )

        override suspend fun scan(packageName: String): TrackerScanResult {
            if (failure) error("scan failed")
            return result
        }
    }

    private companion object {
        const val NOW = 10_000_000L
        const val FRESHNESS = 24L * 60L * 60L * 1_000L
    }
}
