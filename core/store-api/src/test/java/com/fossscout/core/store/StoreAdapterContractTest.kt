package com.fossscout.core.store

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StoreAdapterContractTest {
    @Test
    fun `F-Droid v2 advertises every operation`() {
        val capabilities = StoreAdapterCapabilitiesCatalog.forType(AdapterType.FDROID_V2)

        assertEquals(StoreAdapterCapability.entries.toSet(), capabilities.supported)
        StoreAdapterCapability.entries.forEach { capability ->
            assertTrue(capabilities.supports(capability))
            assertNull(capabilities.unsupportedReason(capability))
        }
    }

    @Test
    fun `deep link adapter explains unsupported operations`() {
        val capabilities = StoreAdapterCapabilitiesCatalog.forType(AdapterType.DEEP_LINK)

        assertTrue(capabilities.supports(StoreAdapterCapability.SEARCH))
        assertEquals(
            StoreCapabilityUnsupportedReason.NO_CATALOG_INDEX,
            capabilities.unsupportedReason(StoreAdapterCapability.LISTING),
        )
        assertEquals(
            StoreCapabilityUnsupportedReason.NO_CATALOG_INDEX,
            capabilities.unsupportedReason(StoreAdapterCapability.SYNC),
        )
        assertEquals(
            StoreCapabilityUnsupportedReason.NO_ARTIFACT_URL,
            capabilities.unsupportedReason(StoreAdapterCapability.ARTIFACT_RESOLUTION),
        )
        assertEquals(
            StoreCapabilityUnsupportedReason.NO_SIGNATURE_METADATA,
            capabilities.unsupportedReason(StoreAdapterCapability.VERIFICATION),
        )
    }

    @Test
    fun `source repository adapter has a reduced capability set`() {
        val capabilities = StoreAdapterCapabilitiesCatalog.forType(
            AdapterType.SOURCE_REPOSITORY,
        )

        assertEquals(
            setOf(
                StoreAdapterCapability.VERSIONS,
                StoreAdapterCapability.ARTIFACT_RESOLUTION,
            ),
            capabilities.supported,
        )
        assertFalse(capabilities.supports(StoreAdapterCapability.SYNC))
        assertNotNull(capabilities.unsupportedReason(StoreAdapterCapability.SYNC))
    }

    @Test
    fun `fake adapter exposes supported results and unsupported reasons`() = runBlocking {
        val adapter = FakeStoreAdapter(
            adapterType = AdapterType.DEEP_LINK,
            searchResults = listOf(
                StoreAppSummary(
                    packageName = "org.example.app",
                    name = "Example",
                ),
            ),
        )

        val search = adapter.search(StoreSearchRequest("example"))
        val sync = adapter.sync(StoreSyncRequest(BuiltInStores.fdroid))

        assertTrue(search is StoreAdapterResult.Success)
        assertEquals(
            "org.example.app",
            (search as StoreAdapterResult.Success<List<StoreAppSummary>>)
                .value.single().packageName,
        )
        assertTrue(sync is StoreAdapterResult.Unsupported)
        assertEquals(
            StoreAdapterCapability.SYNC,
            (sync as StoreAdapterResult.Unsupported).capability,
        )
        assertEquals(
            StoreCapabilityUnsupportedReason.NO_CATALOG_INDEX,
            sync.reason,
        )
    }
}

/**
 * Test-only adapter proving callers must handle unsupported operations instead
 * of assuming every adapter implements the whole contract.
 */
private class FakeStoreAdapter(
    override val adapterType: AdapterType,
    private val searchResults: List<StoreAppSummary> = emptyList(),
) : StoreAdapter {
    override val capabilities = StoreAdapterCapabilitiesCatalog.forType(adapterType)

    override suspend fun search(
        request: StoreSearchRequest,
    ): StoreAdapterResult<List<StoreAppSummary>> = if (
        capabilities.supports(StoreAdapterCapability.SEARCH)
    ) {
        StoreAdapterResult.Success(searchResults)
    } else {
        unsupported(StoreAdapterCapability.SEARCH)
    }

    override suspend fun list(
        request: StoreListingRequest,
    ): StoreAdapterResult<StoreListingPage> = if (
        capabilities.supports(StoreAdapterCapability.LISTING)
    ) {
        StoreAdapterResult.Success(StoreListingPage(emptyList()))
    } else {
        unsupported(StoreAdapterCapability.LISTING)
    }

    override suspend fun versions(
        request: StoreVersionsRequest,
    ): StoreAdapterResult<List<StoreVersion>> = if (
        capabilities.supports(StoreAdapterCapability.VERSIONS)
    ) {
        StoreAdapterResult.Success(emptyList())
    } else {
        unsupported(StoreAdapterCapability.VERSIONS)
    }

    override suspend fun resolveArtifact(
        request: StoreArtifactRequest,
    ): StoreAdapterResult<StoreArtifact> = if (
        capabilities.supports(StoreAdapterCapability.ARTIFACT_RESOLUTION)
    ) {
        StoreAdapterResult.Failure(
            StoreAdapterFailure(StoreFailureKind.INVALID_RESPONSE, "not configured"),
        )
    } else {
        unsupported(StoreAdapterCapability.ARTIFACT_RESOLUTION)
    }

    override suspend fun sync(
        request: StoreSyncRequest,
    ): StoreAdapterResult<StoreSyncResult> = if (
        capabilities.supports(StoreAdapterCapability.SYNC)
    ) {
        StoreAdapterResult.Success(StoreSyncResult(itemCount = 0))
    } else {
        unsupported(StoreAdapterCapability.SYNC)
    }

    override suspend fun verify(
        request: StoreVerifyRequest,
    ): StoreAdapterResult<StoreVerification> = if (
        capabilities.supports(StoreAdapterCapability.VERIFICATION)
    ) {
        StoreAdapterResult.Success(StoreVerification(verified = false))
    } else {
        unsupported(StoreAdapterCapability.VERIFICATION)
    }

    private fun unsupported(
        capability: StoreAdapterCapability,
    ): StoreAdapterResult.Unsupported = StoreAdapterResult.Unsupported(
        capability = capability,
        reason = checkNotNull(capabilities.unsupportedReason(capability)),
    )
}
