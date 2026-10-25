package com.fossscout.feature.stores

import com.fossscout.core.data.StoreRegistryCodec
import com.fossscout.core.data.StoreRegistryRepository
import com.fossscout.core.data.StoreRegistryStore
import com.fossscout.core.index.HttpFetcher
import com.fossscout.core.index.HttpRequest
import com.fossscout.core.index.HttpResponse
import com.fossscout.core.index.FdroidV2Parser
import com.fossscout.core.index.IndexState
import com.fossscout.core.index.IndexSyncRepository
import com.fossscout.core.index.PayloadWriteResult
import com.fossscout.core.index.SignatureStatus
import com.fossscout.core.index.SnapshotFreshness
import com.fossscout.core.index.SnapshotMetadataStore
import com.fossscout.core.index.SnapshotPayloadSink
import com.fossscout.core.index.SnapshotPayloadStore
import com.fossscout.core.index.StorePackage
import com.fossscout.core.index.StoreSnapshot
import com.fossscout.core.index.SyncJob
import com.fossscout.core.index.SyncState
import com.fossscout.core.index.TrustRecord
import com.fossscout.core.model.StoreId
import com.fossscout.core.store.AdapterType
import com.fossscout.core.store.BuiltInStores
import com.fossscout.core.store.LinkOnlyReason
import com.fossscout.core.store.StoreDescriptor
import com.fossscout.core.store.StoreMode
import com.fossscout.core.store.StoreRegistry
import java.io.IOException
import java.io.InputStream
import java.nio.file.Files
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

    @Test
    fun `index metadata exposes freshness last sync trust and storage`() = runTest(dispatcher) {
        val now = 2_000_000L
        val snapshot = StoreSnapshot(
            storeId = BuiltInStores.FDROID_ID,
            fetchedAtEpochMillis = now - 1_000,
            indexTimestamp = 1_000,
            indexSha256 = "a".repeat(64),
            packageCount = 42,
            payloadPath = "fdroid-1000.ndjson",
            payloadSizeBytes = 1_024,
            signatureStatus = SignatureStatus.VERIFIED,
            sourceUrl = "https://f-droid.org/repo/index-v2.json",
        )
        val job = SyncJob(
            storeId = BuiltInStores.FDROID_ID,
            state = SyncState.SUCCEEDED,
            finishedAtEpochMillis = now - 1_000,
        )
        val trust = TrustRecord(
            storeId = BuiltInStores.FDROID_ID,
            checkedAtEpochMillis = now - 1_000,
            signatureStatus = SignatureStatus.VERIFIED,
            signerFingerprint = BuiltInStores.FDROID_FINGERPRINT,
            sourceUrl = "https://f-droid.org/repo/entry.jar",
        )
        val indexRepository = IndexSyncRepository(
            metadataStore = FakeSnapshotMetadataStore(
                IndexState(
                    snapshots = mapOf(BuiltInStores.FDROID_ID to snapshot),
                    jobs = mapOf(BuiltInStores.FDROID_ID to job),
                    trustRecords = mapOf(BuiltInStores.FDROID_ID to trust),
                ),
            ),
            payloadStore = UnusedPayloadStore(usageBytes = 1_024),
            httpFetcher = UnusedHttpFetcher,
            tempDir = Files.createTempDirectory("fossscout-stores-vm").toFile(),
            storageBudgetBytes = { 16L * 1024L * 1024L },
            now = { now },
            staleAfterMillis = 24L * 60L * 60L * 1_000L,
        )
        val viewModel = StoresViewModel(
            StoreRegistryRepository(store = FakeStoreRegistryStore()),
            indexRepository,
        )
        advanceUntilIdle()

        val state = viewModel.uiState.value
        val status = requireNotNull(state.syncStatuses[BuiltInStores.FDROID_ID])
        assertEquals(snapshot, status.snapshot)
        assertEquals(trust, status.trust)
        assertEquals(job, status.job)
        assertEquals(SnapshotFreshness.FRESH, status.freshness)
        assertEquals(now - 1_000, status.lastSuccessfulSyncEpochMillis)
        assertFalse(status.isSyncing)
        assertEquals(1_024L, state.storage?.usageBytes)
        assertEquals(16L * 1024L * 1024L, state.storage?.budgetBytes)
        assertFalse(requireNotNull(state.storage).overBudget)
    }

    @Test
    fun `approving a signer rotation clears old trust and starts a fresh sync`() =
        runTest(dispatcher) {
            val oldPin = "a".repeat(64)
            val newPin = "b".repeat(64)
            val custom = StoreDescriptor(
                id = StoreId("custom-rotation"),
                name = "Rotation store",
                homepage = "https://repo.example",
                adapterType = AdapterType.FDROID_V2,
                indexUrl = "https://repo.example/repo",
                fingerprint = oldPin,
                priority = 10,
            )
            val registry = BuiltInStores.registry().copy(
                stores = BuiltInStores.registry().stores + custom,
            )
            val snapshot = StoreSnapshot(
                storeId = custom.id.value,
                fetchedAtEpochMillis = 1_000,
                indexTimestamp = 900,
                indexSha256 = "c".repeat(64),
                packageCount = 1,
                payloadPath = "old.ndjson",
                payloadSizeBytes = 10,
                signatureStatus = SignatureStatus.VERIFIED,
                sourceUrl = "https://repo.example/repo/index-v2.json",
                expectedFingerprint = oldPin,
                signerFingerprint = oldPin,
            )
            val trust = TrustRecord(
                storeId = custom.id.value,
                checkedAtEpochMillis = 1_000,
                signatureStatus = SignatureStatus.INVALID,
                expectedFingerprint = oldPin,
                signerFingerprint = newPin,
                sourceUrl = "https://repo.example/repo/entry.jar",
                failure = com.fossscout.core.index.VerificationFailure.SIGNER_MISMATCH,
            )
            val indexRepository = IndexSyncRepository(
                metadataStore = FakeSnapshotMetadataStore(
                    IndexState(
                        snapshots = mapOf(custom.id.value to snapshot),
                        trustRecords = mapOf(custom.id.value to trust),
                        jobs = mapOf(
                            custom.id.value to SyncJob(
                                storeId = custom.id.value,
                                state = SyncState.BLOCKED,
                            ),
                        ),
                        trustAudit = listOf(trust),
                    ),
                ),
                payloadStore = UnusedPayloadStore(usageBytes = 10),
                httpFetcher = UnusedHttpFetcher,
                tempDir = Files.createTempDirectory("fossscout-rotation-vm").toFile(),
                storageBudgetBytes = { 16L * 1024L * 1024L },
                now = { 2_000_000L },
            )
            val viewModel = StoresViewModel(
                StoreRegistryRepository(store = FakeStoreRegistryStore(registry)),
                indexRepository,
            )
            advanceUntilIdle()

            viewModel.openSecurityDetails(custom.id)
            advanceUntilIdle()
            val details = requireNotNull(viewModel.uiState.value.securityDetails)
            assertEquals(StoreTrustState.ROTATION_REQUIRED, details.trustState)
            assertEquals(newPin, details.rotation?.newFingerprint)

            viewModel.approveFingerprintRotation(custom.id, newPin)
            advanceUntilIdle()

            val state = viewModel.uiState.value
            val descriptor = state.entries.first { it.descriptor.id == custom.id }.descriptor
            assertEquals(newPin, descriptor.fingerprint)
            assertNull(state.securityDetailsId)
            val status = requireNotNull(state.syncStatuses[custom.id.value])
            assertNull(status.snapshot)
            assertNull(status.trust)
            assertEquals(1, status.audit.size)
            assertEquals(SyncState.FAILED, status.job?.state)
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

private class FakeSnapshotMetadataStore(
    initial: IndexState = IndexState(),
) : SnapshotMetadataStore {
    private val mutableState = MutableStateFlow(initial)

    override val state: Flow<IndexState> = flow { emitAll(mutableState) }

    override suspend fun save(state: IndexState) {
        mutableState.value = state
    }
}

private class UnusedPayloadStore(
    private val usageBytes: Long,
) : SnapshotPayloadStore {
    override suspend fun <T> writePayload(
        storeId: String,
        version: Long,
        maxBytes: Long,
        block: suspend (SnapshotPayloadSink) -> T,
    ): PayloadWriteResult<T> = error("Not used")

    override suspend fun open(payloadPath: String): InputStream = error("Not used")

    override suspend fun exists(payloadPath: String): Boolean = false

    override suspend fun delete(payloadPath: String) = Unit

    override suspend fun usageBytes(): Long = usageBytes

    override suspend fun cleanup(referencedPaths: Set<String>): Long = 0

    override suspend fun forEachPackage(
        payloadPath: String,
        parser: FdroidV2Parser,
        action: suspend (StorePackage) -> Boolean,
    ) = Unit
}

private object UnusedHttpFetcher : HttpFetcher {
    override suspend fun get(request: HttpRequest): HttpResponse = error("Not used")
}
