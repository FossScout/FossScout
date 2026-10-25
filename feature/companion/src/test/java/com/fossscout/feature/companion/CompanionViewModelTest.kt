package com.fossscout.feature.companion

import com.fossscout.core.handoff.CompanionStoreDetector
import com.fossscout.core.handoff.CompanionStores
import com.fossscout.core.handoff.HandoffPreferences
import com.fossscout.core.handoff.HandoffPreferencesRepository
import com.fossscout.core.handoff.HandoffPreferencesStore
import com.fossscout.core.handoff.InstalledCompanionStore
import com.fossscout.core.handoff.ReplacementVerdict
import com.fossscout.core.inventory.InstalledApp
import com.fossscout.core.inventory.InstalledAppSource
import com.fossscout.core.inventory.InventoryEnumeration
import com.fossscout.core.matching.ConfidenceLabel
import com.fossscout.core.matching.CoverageSnapshot
import com.fossscout.core.matching.MatchCandidate
import com.fossscout.core.matching.MatchDecision
import com.fossscout.core.matching.MatchLevel
import com.fossscout.core.matching.MatchOutcome
import com.fossscout.core.store.BuiltInStores
import com.fossscout.core.store.StoreAvailability
import com.fossscout.core.store.StoreEntry
import com.fossscout.core.store.StoreMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
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
class CompanionViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val preferencesStore = FakeHandoffPreferencesStore()
    private val stores = flowOf(
        listOf(
            StoreEntry(
                descriptor = BuiltInStores.fdroid,
                availability = StoreAvailability(StoreMode.MATCHABLE),
            ),
        ),
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
    fun `update assessment and F-Droid handoff are exposed`() = runTest {
        val viewModel = viewModel(
            installed = installed(versionCode = 10L, signer = SIGNER),
            clients = listOf(InstalledCompanionStore(CompanionStores.fdroid, enabled = true)),
        )

        viewModel.open(decision())
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(ReplacementVerdict.UPDATE, state.assessment?.verdict)
        assertEquals(CompanionStores.fdroid, state.selectedClient?.store)
        assertEquals(
            "market://details?id=org.example.app",
            state.appHandoff?.marketUri,
        )
        assertNotNull(state.repositoryHandoff)
        assertTrue(state.canOpenAppInStore)
        assertTrue(state.canAddRepository)
    }

    @Test
    fun `replace verdict blocks the store action until confirmation`() = runTest {
        val viewModel = viewModel(
            installed = installed(signer = "aaaa"),
            clients = listOf(InstalledCompanionStore(CompanionStores.fdroid, enabled = true)),
        )

        viewModel.open(decision(primary = candidate(signerSha256 = listOf("bbbb"))))
        advanceUntilIdle()

        val before = viewModel.uiState.value
        assertEquals(ReplacementVerdict.REPLACE, before.assessment?.verdict)
        assertTrue(before.requiresConfirmation)
        assertFalse(before.canOpenAppInStore)

        viewModel.setReplaceConfirmed(true)

        assertTrue(viewModel.uiState.value.canOpenAppInStore)
    }

    @Test
    fun `no installed client still exposes a browser and repository fallback`() = runTest {
        val viewModel = viewModel(installed = installed(), clients = emptyList())

        viewModel.open(decision())
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertNull(state.selectedClient)
        assertNull(state.appHandoff)
        assertNotNull(state.fallback)
        assertNotNull(state.repositoryHandoff)
        assertTrue(state.hasAnyAction)
    }

    @Test
    fun `missing installed app is not reported as a replacement`() = runTest {
        val viewModel = viewModel(installed = null, clients = emptyList())

        viewModel.open(decision())
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(ReplacementVerdict.NOT_INSTALLED, state.assessment?.verdict)
        assertFalse(state.installedLookupFailed)
        assertNotNull(state.appHandoff ?: state.fallback)
    }

    @Test
    fun `no primary candidate is a visible error`() = runTest {
        val viewModel = viewModel(installed = installed(), clients = emptyList())

        viewModel.open(decision(primary = null))
        advanceUntilIdle()

        assertEquals(CompanionError.NO_CANDIDATE, viewModel.uiState.value.error)
        assertNull(viewModel.uiState.value.candidate)
    }

    @Test
    fun `preferred client is restored and changes are persisted`() = runTest {
        preferencesStore.save(
            HandoffPreferences(
                preferredStores = mapOf("org.example.app" to CompanionStores.droidify.id),
            ),
        )
        val viewModel = viewModel(
            installed = installed(),
            clients = listOf(
                InstalledCompanionStore(CompanionStores.fdroid, enabled = true),
                InstalledCompanionStore(CompanionStores.droidify, enabled = true),
            ),
        )

        viewModel.open(decision())
        advanceUntilIdle()
        assertEquals(CompanionStores.droidify, viewModel.uiState.value.selectedClient?.store)

        viewModel.selectClient(CompanionStores.fdroid.id)
        advanceUntilIdle()

        assertEquals(CompanionStores.fdroid, viewModel.uiState.value.selectedClient?.store)
        assertEquals(
            CompanionStores.fdroid.id,
            preferencesStore.current.preferredStores["org.example.app"],
        )
    }

    private fun viewModel(
        installed: InstalledApp?,
        clients: List<InstalledCompanionStore>,
    ): CompanionViewModel = CompanionViewModel(
        appSource = FakeInstalledAppSource(installed),
        stores = stores,
        detector = FakeDetector(clients),
        preferences = HandoffPreferencesRepository(preferencesStore),
    )

    private fun installed(
        versionCode: Long = 10L,
        signer: String? = SIGNER,
    ): InstalledApp = InstalledApp(
        packageName = PACKAGE,
        label = "Example",
        versionName = "1.0",
        versionCode = versionCode,
        installerPackage = "com.android.vending",
        signerSha256 = signer,
    )

    private fun decision(primary: MatchCandidate? = candidate()): MatchDecision = MatchDecision(
        appKey = PACKAGE,
        label = "Example",
        packageName = PACKAGE,
        level = MatchLevel.L1,
        outcome = MatchOutcome.MATCH,
        confidence = ConfidenceLabel.HIGH,
        primary = primary,
        coverage = CoverageSnapshot(checkedAtEpochMillis = 0L),
    )

    private fun candidate(
        signerSha256: List<String> = listOf(SIGNER),
        versionCode: Long = 20L,
    ): MatchCandidate = MatchCandidate(
        appKey = PACKAGE,
        level = MatchLevel.L1,
        storeId = BuiltInStores.FDROID_ID,
        storeName = "F-Droid",
        packageName = PACKAGE,
        displayName = "Example",
        versionName = "2.0",
        versionCode = versionCode,
        signerSha256 = signerSha256,
    )

    private class FakeInstalledAppSource(
        private val app: InstalledApp?,
    ) : InstalledAppSource {
        override val supportsFullScan: Boolean = false

        override suspend fun find(packageName: String): InstalledApp? =
            app?.takeIf { it.packageName == packageName }

        override suspend fun search(query: String, limit: Int): List<InstalledApp> =
            app?.let(::listOf).orEmpty()

        override suspend fun enumerate(): InventoryEnumeration =
            InventoryEnumeration(apps = app?.let(::listOf).orEmpty())
    }

    private class FakeDetector(
        private val clients: List<InstalledCompanionStore>,
    ) : CompanionStoreDetector {
        override suspend fun detect(): List<InstalledCompanionStore> = clients
    }

    private class FakeHandoffPreferencesStore : HandoffPreferencesStore {
        private val state = MutableStateFlow(HandoffPreferences())
        override val preferences: Flow<HandoffPreferences> = state.asStateFlow()
        val current: HandoffPreferences get() = state.value

        override suspend fun save(preferences: HandoffPreferences) {
            state.value = preferences
        }
    }

    private companion object {
        const val PACKAGE = "org.example.app"
        const val SIGNER = "aaaa"
    }
}
