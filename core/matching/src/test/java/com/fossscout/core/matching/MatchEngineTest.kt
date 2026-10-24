package com.fossscout.core.matching

import com.fossscout.core.index.SignatureStatus
import com.fossscout.core.index.SnapshotFreshness
import com.fossscout.core.index.StoreSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MatchEngineTest {
    @Test
    fun `exact package from a fresh store is L1 with evidence`() {
        val decision = evaluate(
            query = query("org.example.app"),
            stores = listOf(freshStore(ENTRY)),
        )

        assertEquals(MatchLevel.L1, decision.level)
        assertEquals(MatchOutcome.MATCH, decision.outcome)
        assertEquals(ConfidenceLabel.HIGH, decision.confidence)
        assertTrue(decision.hasExactMatch)
        assertTrue(decision.primary!!.isFoss)
        assertEquals("GPL-3.0-only", decision.primary!!.license)
        assertEquals("https://example.org/src", decision.primary!!.sourceUrl)
        assertTrue(decision.coverage.complete)
    }

    @Test
    fun `missing license makes an exact package unknown rather than FOSS`() {
        val decision = evaluate(
            query = query("org.example.app"),
            stores = listOf(freshStore(ENTRY.copy(license = null))),
        )

        assertEquals(MatchLevel.L0, decision.level)
        assertEquals(MatchOutcome.UNKNOWN, decision.outcome)
        assertEquals(ConfidenceLabel.UNKNOWN, decision.confidence)
        assertFalse(decision.primary!!.isFoss)
        assertTrue(decision.reasons.any { it.contains("license", ignoreCase = true) })
    }

    @Test
    fun `missing source makes an exact package unknown`() {
        val decision = evaluate(
            query = query("org.example.app"),
            stores = listOf(freshStore(ENTRY.copy(sourceCode = null))),
        )

        assertEquals(MatchLevel.L0, decision.level)
        assertEquals(MatchOutcome.UNKNOWN, decision.outcome)
        assertFalse(decision.primary!!.isFoss)
    }

    @Test
    fun `stale store produces incomplete coverage instead of not found`() {
        val decision = evaluate(
            query = query("org.example.missing"),
            stores = listOf(
                store(
                    id = "store-1",
                    freshness = SnapshotFreshness.STALE,
                    entries = emptyList(),
                ),
            ),
        )

        assertEquals(MatchOutcome.INCOMPLETE, decision.outcome)
        assertEquals(ConfidenceLabel.LOW, decision.confidence)
        assertFalse(decision.coverage.complete)
        assertEquals(CoverageStatus.STALE, decision.coverage.stores.single().status)
    }

    @Test
    fun `matching still works offline from a stale cached snapshot`() {
        val decision = evaluate(
            query = query("org.example.app"),
            stores = listOf(
                store(
                    id = "store-1",
                    freshness = SnapshotFreshness.STALE,
                    entries = listOf(ENTRY),
                ),
            ),
        )

        assertEquals(MatchLevel.L1, decision.level)
        assertEquals(MatchOutcome.MATCH, decision.outcome)
        assertEquals(ConfidenceLabel.MEDIUM, decision.confidence)
        assertFalse(decision.coverage.complete)
    }

    @Test
    fun `disabled store is skipped with a reason and cannot contribute a match`() {
        val decision = evaluate(
            query = query("org.example.app"),
            stores = listOf(
                store(id = "disabled", enabled = false, entries = listOf(ENTRY)),
                store(id = "checked", entries = emptyList()),
            ),
        )

        val disabled = decision.coverage.stores.first { it.storeId == "disabled" }
        assertEquals(CoverageStatus.SKIPPED, disabled.status)
        assertEquals(CoverageReason.DISABLED, disabled.reason)
        assertNull(decision.primary)
        assertEquals(MatchOutcome.NOT_FOUND, decision.outcome)
        assertTrue(decision.coverage.complete)
    }

    @Test
    fun `unsupported store keeps the result incomplete`() {
        val decision = evaluate(
            query = query("org.example.missing"),
            stores = listOf(
                store(id = "link-only", supported = false, entries = emptyList()),
                store(id = "checked", entries = emptyList()),
            ),
        )

        val unsupported = decision.coverage.stores.first { it.storeId == "link-only" }
        assertEquals(CoverageStatus.SKIPPED, unsupported.status)
        assertEquals(CoverageReason.UNSUPPORTED, unsupported.reason)
        assertFalse(decision.coverage.complete)
        assertEquals(MatchOutcome.INCOMPLETE, decision.outcome)
    }

    @Test
    fun `all stores disabled is incomplete not not-found`() {
        val decision = evaluate(
            query = query("org.example.app"),
            stores = listOf(store(id = "disabled", enabled = false, entries = emptyList())),
        )

        assertEquals(MatchOutcome.INCOMPLETE, decision.outcome)
        assertTrue(decision.reasons.any { it.contains("No enabled") })
        assertTrue(decision.coverage.required.isEmpty())
    }

    @Test
    fun `never synced store blocks a not-found conclusion`() {
        val decision = evaluate(
            query = query("org.example.app"),
            stores = listOf(
                MatchStoreInput(
                    storeId = "store-1",
                    storeName = "Store",
                    enabled = true,
                    supported = true,
                    snapshot = null,
                    freshness = SnapshotFreshness.NEVER_SYNCED,
                ),
            ),
        )

        assertEquals(MatchOutcome.INCOMPLETE, decision.outcome)
        assertEquals(
            CoverageReason.NEVER_SYNCED,
            decision.coverage.stores.single().reason,
        )
    }

    @Test
    fun `catalog read failure becomes a coverage gap`() {
        val decision = evaluate(
            query = query("org.example.app"),
            stores = listOf(
                MatchStoreInput(
                    storeId = "store-1",
                    storeName = "Store",
                    enabled = true,
                    supported = true,
                    snapshot = snapshot("store-1"),
                    freshness = SnapshotFreshness.FRESH,
                    loadError = "payload missing",
                ),
            ),
        )

        assertEquals(MatchOutcome.INCOMPLETE, decision.outcome)
        assertEquals(CoverageStatus.FAILED, decision.coverage.stores.single().status)
        assertEquals(CoverageReason.READ_ERROR, decision.coverage.stores.single().reason)
    }

    @Test
    fun `multiple stores carrying the same package expose other matches`() {
        val decision = evaluate(
            query = query("org.example.app"),
            stores = listOf(
                store(id = "first", entries = listOf(ENTRY)),
                store(id = "second", entries = listOf(ENTRY.copy(name = "Example Mirror"))),
            ),
        )

        assertEquals(MatchLevel.L1, decision.level)
        assertEquals(1, decision.otherMatches.size)
        assertEquals("second", decision.otherMatches.single().storeId)
        assertEquals(2, decision.coverage.checkedCount)
    }

    @Test
    fun `signer mismatch is L2 and is not called the same app`() {
        val decision = evaluate(
            query = query("org.example.app", signer = "bbbb"),
            stores = listOf(
                freshStore(
                    ENTRY.copy(signerSha256 = listOf("aaaa")),
                ),
            ),
        )

        assertEquals(MatchLevel.L2, decision.level)
        assertEquals(MatchOutcome.MATCH, decision.outcome)
        assertFalse(decision.primary!!.isSameApp)
        assertTrue(
            decision.primary!!.reasons.any { it.contains("signer", ignoreCase = true) },
        )
    }

    @Test
    fun `source URL match produces L2 only when no exact or fork exists`() {
        val fork = ENTRY.copy(
            packageName = "org.example.fork",
            name = "Example Fork",
            sourceCode = "https://example.org/src/",
        )
        val decision = evaluate(
            query = query(
                "org.example.original",
                sourceUrls = setOf("https://example.org/src"),
            ),
            stores = listOf(freshStore(fork)),
        )

        assertEquals(MatchLevel.L2, decision.level)
        assertEquals(MatchOutcome.MATCH, decision.outcome)
        assertEquals("org.example.fork", decision.primary!!.packageName)
        assertTrue(decision.primary!!.reasons.any { it.contains("source URL") })
        assertTrue(decision.alternatives.isEmpty())
    }

    @Test
    fun `alternatives appear only when there is no L1 or L2`() {
        val alternative = CatalogEntry(
            packageName = "org.maps.alt",
            name = "Open Maps",
            summary = "Offline maps and navigation",
            categories = listOf("Maps & Navigation"),
            license = "Apache-2.0",
            sourceCode = "https://example.org/maps",
        )
        val decision = evaluate(
            query = query(
                "org.proprietary.maps",
                label = "Maps",
                categories = setOf("Maps & Navigation"),
            ),
            stores = listOf(freshStore(alternative)),
        )

        assertEquals(MatchOutcome.ALTERNATIVES, decision.outcome)
        assertEquals(MatchLevel.L3, decision.level)
        assertEquals(1, decision.alternatives.size)
        val suggestion = decision.alternatives.single()
        assertEquals(MatchLevel.L3, suggestion.level)
        assertTrue(suggestion.isDifferentApp)
        assertTrue(suggestion.reasons.any { it.contains("category", ignoreCase = true) })
    }

    @Test
    fun `an exact match suppresses alternatives`() {
        val alternative = CatalogEntry(
            packageName = "org.maps.alt",
            name = "Open Maps",
            categories = listOf("Maps & Navigation"),
            license = "Apache-2.0",
            sourceCode = "https://example.org/maps",
        )
        val decision = evaluate(
            query = query("org.example.app", categories = setOf("Maps & Navigation")),
            stores = listOf(freshStore(ENTRY, alternative)),
        )

        assertEquals(MatchOutcome.MATCH, decision.outcome)
        assertTrue(decision.alternatives.isEmpty())
    }

    @Test
    fun `alternatives are capped and ranked by evidence`() {
        val weak = CatalogEntry(
            packageName = "org.maps.weak",
            name = "Maps Notes",
            summary = "A note taker",
            categories = listOf("Notes"),
            license = "MIT",
            sourceCode = "https://example.org/weak",
        )
        val strong = CatalogEntry(
            packageName = "org.maps.strong",
            name = "Open Maps",
            summary = "Offline maps and navigation",
            categories = listOf("Maps & Navigation", "Navigation"),
            license = "Apache-2.0",
            sourceCode = "https://example.org/strong",
        )
        val decision = evaluate(
            query = query(
                "org.proprietary.maps",
                label = "Maps",
                categories = setOf("Maps & Navigation", "Navigation"),
            ),
            stores = listOf(freshStore(weak, strong)),
            maxAlternatives = 1,
        )

        assertEquals(1, decision.alternatives.size)
        assertEquals("org.maps.strong", decision.alternatives.single().packageName)
    }

    @Test
    fun `alternatives can be disabled for classification`() {
        val alternative = CatalogEntry(
            packageName = "org.maps.alt",
            name = "Open Maps",
            summary = "Offline maps",
            categories = listOf("Maps & Navigation"),
            license = "Apache-2.0",
            sourceCode = "https://example.org/maps",
        )
        val decision = evaluate(
            query = query("org.proprietary.maps", label = "Maps"),
            stores = listOf(freshStore(alternative)),
            maxAlternatives = 0,
        )

        assertTrue(decision.alternatives.isEmpty())
        assertEquals(MatchOutcome.NOT_FOUND, decision.outcome)
    }

    @Test
    fun `stale coverage still shows suggestions but stays incomplete`() {
        val alternative = CatalogEntry(
            packageName = "org.maps.alt",
            name = "Open Maps",
            summary = "Offline maps",
            categories = listOf("Maps & Navigation"),
            license = "Apache-2.0",
            sourceCode = "https://example.org/maps",
        )
        val decision = evaluate(
            query = query("org.proprietary.maps", label = "Maps"),
            stores = listOf(
                store(
                    id = "store-1",
                    freshness = SnapshotFreshness.STALE,
                    entries = listOf(alternative),
                ),
            ),
        )

        assertEquals(MatchOutcome.INCOMPLETE, decision.outcome)
        assertEquals(1, decision.alternatives.size)
        assertFalse(decision.coverage.complete)
    }

    @Test
    fun `not found is conclusive only when every required store is fresh`() {
        val decision = evaluate(
            query = query("org.example.missing"),
            stores = listOf(
                store(id = "one", entries = emptyList()),
                store(id = "two", entries = emptyList()),
            ),
        )

        assertEquals(MatchOutcome.NOT_FOUND, decision.outcome)
        assertEquals(ConfidenceLabel.HIGH, decision.confidence)
        assertTrue(decision.coverage.complete)
        assertEquals(2, decision.coverage.checkedCount)
    }

    @Test
    fun `no stores is incomplete`() {
        val decision = evaluate(query = query("org.example.app"), stores = emptyList())

        assertEquals(MatchOutcome.INCOMPLETE, decision.outcome)
        assertFalse(decision.coverage.complete)
    }

    @Test
    fun `missing metadata on the exact package still allows alternatives`() {
        val alternative = CatalogEntry(
            packageName = "org.example.alt",
            name = "Example Alternative",
            summary = "Example tool",
            categories = listOf("Tools"),
            license = "MIT",
            sourceCode = "https://example.org/alt",
        )
        val decision = evaluate(
            query = query("org.example.app", categories = setOf("Tools")),
            stores = listOf(
                freshStore(ENTRY.copy(license = null), alternative),
            ),
        )

        assertEquals(MatchOutcome.UNKNOWN, decision.outcome)
        assertEquals(MatchLevel.L0, decision.primary!!.level)
        assertEquals(1, decision.alternatives.size)
    }

    private fun evaluate(
        query: MatchQuery,
        stores: List<MatchStoreInput>,
        maxAlternatives: Int = DEFAULT_MAX_ALTERNATIVES,
    ): MatchDecision = MatchEngine.evaluate(
        query = query,
        stores = stores,
        nowEpochMillis = NOW,
        freshnessWindowMillis = FRESHNESS,
        maxAlternatives = maxAlternatives,
    )

    private fun query(
        packageName: String,
        label: String = "Example",
        signer: String? = null,
        categories: Set<String> = emptySet(),
        sourceUrls: Set<String> = emptySet(),
    ) = MatchQuery(
        appKey = packageName,
        label = label,
        packageName = packageName,
        installedSignerSha256 = signer,
        categories = categories,
        sourceUrls = sourceUrls,
    )

    private fun freshStore(vararg entries: CatalogEntry): MatchStoreInput =
        store(id = "store-1", entries = entries.toList())

    private fun store(
        id: String,
        enabled: Boolean = true,
        supported: Boolean = true,
        freshness: SnapshotFreshness = SnapshotFreshness.FRESH,
        entries: List<CatalogEntry>,
    ) = MatchStoreInput(
        storeId = id,
        storeName = "Store $id",
        enabled = enabled,
        supported = supported,
        snapshot = snapshot(id),
        freshness = freshness,
        catalog = StoreCatalogData(id, entries),
    )

    private fun snapshot(id: String) = StoreSnapshot(
        storeId = id,
        fetchedAtEpochMillis = NOW - 1_000L,
        packageCount = 1,
        payloadPath = "$id.ndjson",
        payloadSizeBytes = 10L,
        signatureStatus = SignatureStatus.VERIFIED,
        sourceUrl = "https://store.example/repo",
    )

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
            versionName = "1.2.3",
            signerSha256 = listOf("aaaa"),
        )
    }
}
