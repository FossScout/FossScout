package com.fossscout.core.matching

import com.fossscout.core.index.SignatureStatus
import com.fossscout.core.index.StoreSnapshot
import com.fossscout.core.inventory.Classification
import com.fossscout.core.inventory.InstalledApp
import com.fossscout.core.model.AppKey
import com.fossscout.core.model.MatchOverride
import com.fossscout.core.model.MatchOverrideTarget
import com.fossscout.core.model.StoreId
import com.fossscout.core.store.AdapterType
import com.fossscout.core.store.StoreAvailability
import com.fossscout.core.store.StoreDescriptor
import com.fossscout.core.store.StoreEntry
import com.fossscout.core.store.StoreMode
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MatchingRepositoryTest {
    @Test
    fun `match uses the cached catalog and reports fresh coverage`() = runTest {
        val repository = repository(
            catalogs = mapOf("store-1" to StoreCatalogData("store-1", listOf(ENTRY))),
        )

        val decision = repository.match(query("org.example.app"))

        assertEquals(MatchOutcome.MATCH, decision.outcome)
        assertEquals(MatchLevel.L1, decision.level)
        assertTrue(decision.coverage.complete)
    }

    @Test
    fun `exact package with license and source classifies as FOSS`() = runTest {
        val repository = repository(
            catalogs = mapOf("store-1" to StoreCatalogData("store-1", listOf(ENTRY))),
        )

        val result = repository.classify(installed("org.example.app"))

        assertEquals(Classification.FOSS, result.classification)
        assertTrue(result.explanation!!.contains("Store 1"))
        assertTrue(result.explanation!!.contains("GPL-3.0-only"))
    }

    @Test
    fun `missing license classifies as unknown not FOSS`() = runTest {
        val repository = repository(
            catalogs = mapOf(
                "store-1" to StoreCatalogData(
                    "store-1",
                    listOf(ENTRY.copy(license = null)),
                ),
            ),
        )

        val result = repository.classify(installed("org.example.app"))

        assertEquals(Classification.UNKNOWN, result.classification)
        assertTrue(result.explanation!!.contains("license", ignoreCase = true))
    }

    @Test
    fun `fork match is not classified as the installed app being FOSS`() = runTest {
        val repository = repository(
            catalogs = mapOf("store-1" to StoreCatalogData("store-1", listOf(ENTRY))),
        )

        val result = repository.classify(
            installed("org.example.app", signer = "bbbb"),
        )

        assertEquals(Classification.UNKNOWN, result.classification)
        assertTrue(result.explanation!!.contains("fork", ignoreCase = true))
    }

    @Test
    fun `complete no-match coverage stays unknown and never claims proprietary`() = runTest {
        val repository = repository(
            catalogs = mapOf("store-1" to StoreCatalogData("store-1", emptyList())),
        )

        val result = repository.classify(installed("org.example.missing"))

        assertEquals(Classification.UNKNOWN, result.classification)
        assertTrue(result.explanation!!.contains("does not prove", ignoreCase = true))
    }

    @Test
    fun `stale snapshot still matches offline but coverage is incomplete`() = runTest {
        val repository = repository(
            catalogs = mapOf("store-1" to StoreCatalogData("store-1", listOf(ENTRY))),
            snapshotFetchedAt = NOW - FRESHNESS - 1L,
        )

        val decision = repository.match(query("org.example.app"))

        assertEquals(MatchLevel.L1, decision.level)
        assertEquals(MatchOutcome.MATCH, decision.outcome)
        assertEquals(ConfidenceLabel.MEDIUM, decision.confidence)
        assertTrue(!decision.coverage.complete)
    }

    @Test
    fun `catalog read failure is a coverage gap and classification stays unknown`() = runTest {
        val repository = repository(catalogs = emptyMap())

        val decision = repository.match(query("org.example.app"))
        val result = repository.classify(installed("org.example.app"))

        assertEquals(MatchOutcome.INCOMPLETE, decision.outcome)
        assertEquals(CoverageStatus.FAILED, decision.coverage.stores.single().status)
        assertEquals(Classification.UNKNOWN, result.classification)
        assertTrue(result.explanation!!.contains("incomplete", ignoreCase = true))
    }

    @Test
    fun `disabled stores are neither loaded nor queried`() = runTest {
        val catalog = FakeCatalog(
            catalogs = mapOf(
                "store-1" to StoreCatalogData("store-1", listOf(ENTRY)),
                "store-2" to StoreCatalogData("store-2", emptyList()),
            ),
        )
        val repository = MatchingRepository(
            storeEntries = flowOf(
                listOf(
                    entry(enabled = false),
                    entry(id = "store-2", name = "Store 2"),
                ),
            ),
            snapshots = flowOf(
                mapOf(
                    "store-1" to snapshot("store-1"),
                    "store-2" to snapshot("store-2"),
                ),
            ),
            catalog = catalog,
            now = { NOW },
            freshnessWindowMillis = FRESHNESS,
        )

        val decision = repository.match(query("org.example.app"))

        assertEquals(1, catalog.loads)
        assertEquals(MatchOutcome.NOT_FOUND, decision.outcome)
        assertEquals(
            CoverageReason.DISABLED,
            decision.coverage.stores.first { it.storeId == "store-1" }.reason,
        )
    }

    @Test
    fun `correction target is attached without replacing the automatic result`() = runTest {
        val correction = matchOverride(
            target = MatchOverrideTarget(
                appKey = AppKey.from("org.example.other"),
                displayName = "Other",
                storeId = StoreId("store-1"),
                storeName = "Store 1",
            ),
        )
        val repository = repository(
            catalogs = mapOf(
                "store-1" to StoreCatalogData("store-1", listOf(ENTRY, OTHER_ENTRY)),
            ),
            overrides = listOf(correction),
        )

        val decision = repository.match(query("org.example.app"))

        assertEquals(correction, decision.correction)
        assertEquals("org.example.app", decision.primary?.packageName)
        assertEquals(true, decision.correctionTargetAvailable)
    }

    @Test
    fun `missing correction target is marked unavailable`() = runTest {
        val correction = matchOverride(
            target = MatchOverrideTarget(
                appKey = AppKey.from("org.example.missing"),
                displayName = "Missing",
                storeId = StoreId("store-1"),
                storeName = "Store 1",
            ),
        )
        val repository = repository(
            catalogs = mapOf("store-1" to StoreCatalogData("store-1", listOf(ENTRY))),
            overrides = listOf(correction),
        )

        val decision = repository.match(query("org.example.app"))

        assertEquals(false, decision.correctionTargetAvailable)
    }

    @Test
    fun `no-match correction keeps classification unknown`() = runTest {
        val correction = matchOverride(target = null)
        val repository = repository(
            catalogs = mapOf("store-1" to StoreCatalogData("store-1", listOf(ENTRY))),
            overrides = listOf(correction),
        )

        val decision = repository.match(query("org.example.app"))
        val result = repository.classify(installed("org.example.app"))

        assertTrue(decision.correction?.isNoMatch == true)
        assertEquals(Classification.UNKNOWN, result.classification)
        assertTrue(result.explanation!!.contains("no suitable match", ignoreCase = true))
    }

    @Test
    fun `catalog search finds targets by name and package`() = runTest {
        val repository = repository(
            catalogs = mapOf(
                "store-1" to StoreCatalogData("store-1", listOf(ENTRY, OTHER_ENTRY)),
            ),
        )

        val byName = repository.searchCatalog("other")
        val byPackage = repository.searchCatalog("org.example.")

        assertEquals("org.example.other", byName.single().entry.packageName)
        assertEquals("Store 1", byName.single().storeName)
        assertEquals(
            listOf("org.example.app", "org.example.other"),
            byPackage.map { it.entry.packageName },
        )
    }

    private fun repository(
        catalogs: Map<String, StoreCatalogData>,
        snapshotFetchedAt: Long = NOW - 1_000L,
        overrides: List<MatchOverride> = emptyList(),
    ) = MatchingRepository(
        storeEntries = flowOf(listOf(entry())),
        snapshots = flowOf(
            mapOf("store-1" to snapshot("store-1", snapshotFetchedAt)),
        ),
        catalog = FakeCatalog(catalogs),
        now = { NOW },
        freshnessWindowMillis = FRESHNESS,
        overrides = flowOf(overrides),
    )

    private fun matchOverride(target: MatchOverrideTarget?) = MatchOverride(
        installedAppKey = AppKey.from("org.example.app"),
        target = target,
        note = "private",
        createdAtEpochMillis = 1,
        updatedAtEpochMillis = 2,
    )

    private fun query(packageName: String) = MatchQuery(
        appKey = packageName,
        label = "Example",
        packageName = packageName,
    )

    private fun installed(
        packageName: String,
        signer: String = "aaaa",
    ) = InstalledApp(
        packageName = packageName,
        label = "Example",
        signerSha256 = signer,
    )

    private fun entry(
        id: String = "store-1",
        name: String = "Store 1",
        enabled: Boolean = true,
        matchable: Boolean = true,
    ) = StoreEntry(
        descriptor = StoreDescriptor(
            id = StoreId(id),
            name = name,
            homepage = "https://store.example",
            adapterType = AdapterType.FDROID_V2,
            indexUrl = "https://store.example/repo",
            enabled = enabled,
        ),
        availability = if (matchable) {
            StoreAvailability(StoreMode.MATCHABLE)
        } else {
            StoreAvailability(
                StoreMode.LINK_ONLY,
                com.fossscout.core.store.LinkOnlyReason.ADAPTER_UNSUPPORTED,
            )
        },
    )

    private fun snapshot(id: String, fetchedAt: Long = NOW - 1_000L) = StoreSnapshot(
        storeId = id,
        fetchedAtEpochMillis = fetchedAt,
        packageCount = 1,
        payloadPath = "$id.ndjson",
        payloadSizeBytes = 10L,
        signatureStatus = SignatureStatus.VERIFIED,
        sourceUrl = "https://store.example/repo",
    )

    private class FakeCatalog(
        private val catalogs: Map<String, StoreCatalogData>,
    ) : StoreCatalog {
        var loads: Int = 0
            private set

        override suspend fun load(snapshot: StoreSnapshot): StoreCatalogData {
            loads++
            return catalogs[snapshot.storeId]
                ?: throw IllegalStateException("No catalog for ${snapshot.storeId}")
        }
    }

    private companion object {
        const val NOW = 10_000_000L
        const val FRESHNESS = 24L * 60L * 60L * 1_000L

        val ENTRY = CatalogEntry(
            packageName = "org.example.app",
            name = "Example",
            summary = "An example app",
            categories = listOf("Tools"),
            license = "GPL-3.0-only",
            sourceCode = "https://example.org/src",
            versionName = "1.0",
            signerSha256 = listOf("aaaa"),
        )

        val OTHER_ENTRY = CatalogEntry(
            packageName = "org.example.other",
            name = "Other",
            summary = "Another app",
            categories = listOf("Tools"),
            license = "MIT",
            sourceCode = "https://example.org/other",
            versionName = "2.0",
            signerSha256 = listOf("bbbb"),
        )
    }
}
