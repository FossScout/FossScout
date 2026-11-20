package com.fossscout.feature.matching

import com.fossscout.core.index.ReproducibilityCheck
import com.fossscout.core.index.ReproducibilityChecker
import com.fossscout.core.index.ReproducibilityKey
import com.fossscout.core.index.ReproducibilityStatus
import com.fossscout.core.index.SignatureStatus
import com.fossscout.core.index.StoreSnapshot
import com.fossscout.core.matching.CatalogEntry
import com.fossscout.core.matching.MatchingRepository
import com.fossscout.core.matching.StoreCatalog
import com.fossscout.core.matching.StoreCatalogData
import com.fossscout.core.matching.StoreSearchAvailabilityFilter
import com.fossscout.core.matching.StoreSearchHistory
import com.fossscout.core.matching.StoreSearchHistoryRepository
import com.fossscout.core.matching.StoreSearchHistoryStore
import com.fossscout.core.matching.StoreSearchTrustFilter
import com.fossscout.core.model.StoreId
import com.fossscout.core.store.AdapterType
import com.fossscout.core.store.LinkOnlyReason
import com.fossscout.core.store.StoreAvailability
import com.fossscout.core.store.StoreDescriptor
import com.fossscout.core.store.StoreEntry
import com.fossscout.core.store.StoreMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update
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
class StoreSearchViewModelTest {
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
    fun `initial load lists stores and blank search requires a query`() = runTest(dispatcher) {
        val viewModel = createViewModel(repository(catalogs = twoCatalogs()))
        advanceUntilIdle()

        viewModel.search()

        val state = viewModel.uiState.value
        assertEquals(2, state.storeOptions.size)
        assertEquals(setOf("fdroid", "izzy"), state.selectedStoreIds)
        assertEquals(StoreSearchError.QUERY_REQUIRED, state.error)
        assertTrue(!state.hasSearched)
    }

    @Test
    fun `search groups stores and reports cache metadata`() = runTest(dispatcher) {
        val viewModel = createViewModel(repository(catalogs = twoCatalogs()))

        viewModel.onQueryChange("example")
        viewModel.search()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(1, state.groups.size)
        assertEquals(2, state.groups.single().stores.size)
        assertEquals("F-Droid", state.groups.single().primaryStore.storeName)
        assertTrue(state.hasSearched)
        assertEquals(NOW, state.searchedAtEpochMillis)
        assertNull(state.error)
    }

    @Test
    fun `store and trust filters combine and rerun the search`() = runTest(dispatcher) {
        val viewModel = createViewModel(
            repository(
                entries = listOf(entry("fdroid", "F-Droid"), entry("izzy", "IzzyOnDroid")),
                snapshots = mapOf(
                    "fdroid" to snapshot("fdroid", SignatureStatus.VERIFIED),
                    "izzy" to snapshot("izzy", SignatureStatus.UNVERIFIED),
                ),
                catalogs = mapOf(
                    "fdroid" to catalog("fdroid", example(license = "MIT", source = null)),
                    "izzy" to catalog("izzy", example()),
                ),
            ),
        )
        viewModel.onQueryChange("example")
        viewModel.search()
        advanceUntilIdle()

        viewModel.toggleStore("izzy")
        viewModel.setTrustFilter(StoreSearchTrustFilter.VERIFIED_INDEX)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(listOf("fdroid"), state.groups.single().stores.map { it.storeId })
        assertTrue(state.hasActiveFilters)

        viewModel.clearFilters()
        advanceUntilIdle()

        assertEquals(2, viewModel.uiState.value.groups.single().stores.size)
        assertEquals(StoreSearchTrustFilter.ALL, viewModel.uiState.value.trustFilter)
    }

    @Test
    fun `signer changed badge filter reruns search and clear filters resets it`() = runTest(dispatcher) {
        val viewModel = createViewModel(
            repository(
                catalogs = mapOf(
                    "fdroid" to catalog(
                        "fdroid",
                        example(
                            signerSha256 = listOf("bbbb"),
                            previousSignerSha256 = listOf("aaaa"),
                        ),
                    ),
                    "izzy" to catalog("izzy", example()),
                ),
            ),
        )
        viewModel.onQueryChange("example")
        viewModel.search()
        advanceUntilIdle()

        viewModel.setTrustFilter(StoreSearchTrustFilter.SIGNER_CHANGED)
        advanceUntilIdle()

        assertEquals(
            listOf("fdroid"),
            viewModel.uiState.value.groups.single().stores.map { it.storeId },
        )
        assertTrue(viewModel.uiState.value.hasActiveFilters)

        viewModel.clearFilters()
        advanceUntilIdle()

        assertEquals(2, viewModel.uiState.value.groups.single().stores.size)
        assertEquals(StoreSearchTrustFilter.ALL, viewModel.uiState.value.trustFilter)
    }

    @Test
    fun `link only availability keeps the handoff visible`() = runTest(dispatcher) {
        val viewModel = createViewModel(
            repository(
                entries = listOf(
                    entry("fdroid", "F-Droid"),
                    entry(
                        "vendor",
                        "Vendor",
                        matchable = false,
                        reason = LinkOnlyReason.MISSING_CATALOG_ENDPOINT,
                    ),
                ),
                catalogs = mapOf("fdroid" to catalog("fdroid", example())),
            ),
        )

        viewModel.onQueryChange("example")
        viewModel.setAvailabilityFilter(StoreSearchAvailabilityFilter.LINK_ONLY)
        viewModel.search()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.groups.isEmpty())
        assertEquals("vendor", state.linkOnlyStores.single().storeId)
    }

    @Test
    fun `repository read failure becomes a search error`() = runTest(dispatcher) {
        val repository = MatchingRepository(
            storeEntries = flow { throw IllegalStateException("registry unavailable") },
            snapshots = flowOf(emptyMap()),
            catalog = FakeCatalog(emptyMap()),
            now = { NOW },
        )
        val historyRepository = StoreSearchHistoryRepository(FakeHistoryStore())
        val viewModel = createViewModel(repository, historyRepository)

        viewModel.onQueryChange("example")
        viewModel.search()
        advanceUntilIdle()

        assertEquals(StoreSearchError.SEARCH_FAILED, viewModel.uiState.value.error)
        assertTrue(viewModel.uiState.value.hasSearched)
        assertTrue(historyRepository.history.first().queries.isEmpty())
    }

    @Test
    fun `successful search records the query in history`() = runTest(dispatcher) {
        val historyRepository = StoreSearchHistoryRepository(FakeHistoryStore())
        val viewModel = createViewModel(
            repository(catalogs = twoCatalogs()),
            historyRepository,
        )

        viewModel.onQueryChange("example")
        viewModel.search()
        advanceUntilIdle()

        assertEquals(listOf("example"), viewModel.uiState.value.recentSearches)
        assertEquals(
            listOf("example"),
            historyRepository.history.first().queries,
        )
    }

    @Test
    fun `history can be cleared and disabled`() = runTest(dispatcher) {
        val historyRepository = StoreSearchHistoryRepository(
            FakeHistoryStore(
                StoreSearchHistory(queries = listOf("old query")),
            ),
        )
        val viewModel = createViewModel(
            repository(catalogs = twoCatalogs()),
            historyRepository,
        )
        advanceUntilIdle()

        viewModel.clearSearchHistory()
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.recentSearches.isEmpty())

        viewModel.setHistoryEnabled(false)
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.historyEnabled)

        viewModel.onQueryChange("example")
        viewModel.search()
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.recentSearches.isEmpty())
    }

    @Test
    fun `selecting a recent search fills the query and searches`() = runTest(dispatcher) {
        val historyRepository = StoreSearchHistoryRepository(
            FakeHistoryStore(
                StoreSearchHistory(queries = listOf("example")),
            ),
        )
        val viewModel = createViewModel(
            repository(catalogs = twoCatalogs()),
            historyRepository,
        )
        advanceUntilIdle()

        viewModel.useRecentSearch("example")
        advanceUntilIdle()

        assertEquals("example", viewModel.uiState.value.query)
        assertTrue(viewModel.uiState.value.hasSearched)
        assertEquals(1, viewModel.uiState.value.groups.size)
    }

    @Test
    fun `reproducibility check is F-Droid only and cached by the view model`() = runTest(dispatcher) {
        val checker = FakeReproducibilityChecker()
        val viewModel = createViewModel(
            repository(catalogs = twoCatalogs()),
            reproducibilityChecker = checker,
        )
        viewModel.onQueryChange("example")
        viewModel.search()
        advanceUntilIdle()

        viewModel.checkReproducibility("org.example.app", "izzy", 1L)
        viewModel.checkReproducibility("org.example.app", "fdroid", 1L)
        viewModel.checkReproducibility("org.example.app", "fdroid", 1L)
        advanceUntilIdle()

        val key = ReproducibilityKey("org.example.app", "fdroid", 1L)
        val state = viewModel.uiState.value
        assertEquals(1, checker.calls)
        assertEquals(listOf("org.example.app" to 1L), checker.requests)
        assertEquals(ReproducibilityStatus.VERIFIED, state.reproducibility[key]?.status)
        assertFalse(key in state.checkingReproducibility)
        assertFalse(key in state.reproducibilityErrors)
    }

    @Test
    fun `unavailable reproducibility checks stay retryable`() = runTest(dispatcher) {
        val checker = FakeReproducibilityChecker(
            result = checkResult(ReproducibilityStatus.UNAVAILABLE),
        )
        val viewModel = createViewModel(
            repository(catalogs = twoCatalogs()),
            reproducibilityChecker = checker,
        )
        viewModel.onQueryChange("example")
        viewModel.search()
        advanceUntilIdle()

        viewModel.checkReproducibility("org.example.app", "fdroid", 1L)
        advanceUntilIdle()

        val key = ReproducibilityKey("org.example.app", "fdroid", 1L)
        val state = viewModel.uiState.value
        assertNull(state.reproducibility[key])
        assertTrue(key in state.reproducibilityErrors)
        assertFalse(key in state.checkingReproducibility)
    }

    private fun createViewModel(
        repository: MatchingRepository,
        historyRepository: StoreSearchHistoryRepository =
            StoreSearchHistoryRepository(FakeHistoryStore()),
        reproducibilityChecker: ReproducibilityChecker? = null,
    ) = StoreSearchViewModel(repository, historyRepository, reproducibilityChecker)

    private fun repository(
        entries: List<StoreEntry> = listOf(
            entry("fdroid", "F-Droid"),
            entry("izzy", "IzzyOnDroid"),
        ),
        snapshots: Map<String, StoreSnapshot> = mapOf(
            "fdroid" to snapshot("fdroid"),
            "izzy" to snapshot("izzy"),
        ),
        catalogs: Map<String, StoreCatalogData>,
    ) = MatchingRepository(
        storeEntries = flowOf(entries),
        snapshots = flowOf(snapshots),
        catalog = FakeCatalog(catalogs),
        now = { NOW },
        freshnessWindowMillis = FRESHNESS,
    )

    private fun twoCatalogs() = mapOf(
        "fdroid" to catalog("fdroid", example(version = "1.0")),
        "izzy" to catalog(
            "izzy",
            example(version = "2.0", antiFeatures = listOf("Tracking")),
        ),
    )

    private fun catalog(storeId: String, entry: CatalogEntry) =
        StoreCatalogData(storeId, listOf(entry))

    private fun example(
        license: String? = "MIT",
        source: String? = "https://example.org/src",
        version: String = "1.0",
        antiFeatures: List<String> = emptyList(),
        signerSha256: List<String> = listOf("aaaa"),
        previousSignerSha256: List<String> = emptyList(),
    ) = CatalogEntry(
        packageName = "org.example.app",
        name = "Example",
        summary = "An example app",
        categories = listOf("Tools"),
        license = license,
        sourceCode = source,
        versionName = version,
        versionCode = 1L,
        antiFeatures = antiFeatures,
        signerSha256 = signerSha256,
        previousSignerSha256 = previousSignerSha256,
    )

    private fun entry(
        id: String,
        name: String,
        matchable: Boolean = true,
        reason: LinkOnlyReason = LinkOnlyReason.ADAPTER_UNSUPPORTED,
    ) = StoreEntry(
        descriptor = StoreDescriptor(
            id = StoreId(id),
            name = name,
            homepage = "https://$id.example",
            adapterType = AdapterType.FDROID_V2,
            indexUrl = "https://$id.example/repo",
            enabled = true,
        ),
        availability = if (matchable) {
            StoreAvailability(StoreMode.MATCHABLE)
        } else {
            StoreAvailability(StoreMode.LINK_ONLY, reason)
        },
    )

    private fun snapshot(
        id: String,
        signatureStatus: SignatureStatus = SignatureStatus.VERIFIED,
    ) = StoreSnapshot(
        storeId = id,
        fetchedAtEpochMillis = NOW - 1_000L,
        packageCount = 1,
        payloadPath = "$id.ndjson",
        payloadSizeBytes = 10L,
        signatureStatus = signatureStatus,
        sourceUrl = "https://$id.example/repo",
    )

    private class FakeCatalog(
        private val catalogs: Map<String, StoreCatalogData>,
    ) : StoreCatalog {
        override suspend fun load(snapshot: StoreSnapshot): StoreCatalogData =
            catalogs[snapshot.storeId]
                ?: throw IllegalStateException("No catalog for ${snapshot.storeId}")
    }

    private class FakeHistoryStore(
        initial: StoreSearchHistory = StoreSearchHistory(),
    ) : StoreSearchHistoryStore {
        private val state = MutableStateFlow(initial)

        override val history = state

        override suspend fun save(history: StoreSearchHistory) {
            state.value = history
        }

        override suspend fun clearQueries() {
            state.update { it.copy(queries = emptyList()) }
        }
    }

    private class FakeReproducibilityChecker(
        private val result: ReproducibilityCheck =
            checkResult(ReproducibilityStatus.VERIFIED),
    ) : ReproducibilityChecker {
        var calls: Int = 0
            private set
        val requests = mutableListOf<Pair<String, Long>>()

        override suspend fun check(packageName: String, versionCode: Long): ReproducibilityCheck {
            calls += 1
            requests += packageName to versionCode
            return result
        }
    }

    private companion object {
        const val NOW = 10_000_000L
        const val FRESHNESS = 24L * 60L * 60L * 1_000L

        fun checkResult(status: ReproducibilityStatus) = ReproducibilityCheck(
            packageName = "org.example.app",
            versionCode = 1L,
            status = status,
            sourceUrl = "https://verification.f-droid.org/packages/org.example.app/",
            checkedAtEpochMillis = NOW,
            versionName = "1.0",
        )
    }
}
