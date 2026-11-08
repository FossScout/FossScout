package com.fossscout.core.matching

import com.fossscout.core.index.SignatureStatus
import com.fossscout.core.index.SnapshotFreshness
import com.fossscout.core.index.StoreSnapshot
import com.fossscout.core.model.StoreId
import com.fossscout.core.store.AdapterType
import com.fossscout.core.store.LinkOnlyReason
import com.fossscout.core.store.StoreAvailability
import com.fossscout.core.store.StoreDescriptor
import com.fossscout.core.store.StoreEntry
import com.fossscout.core.store.StoreMode
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StoreSearchRepositoryTest {
    @Test
    fun `groups the same package across stores without hiding store metadata`() = runTest {
        val repository = repository(
            entries = listOf(entry("fdroid", "F-Droid"), entry("izzy", "IzzyOnDroid")),
            catalogs = mapOf(
                "fdroid" to catalog("fdroid", example(license = "MIT", version = "1.0")),
                "izzy" to catalog(
                    "izzy",
                    example(
                        license = "GPL-3.0-only",
                        version = "2.0",
                        antiFeatures = listOf("Tracking"),
                    ),
                ),
            ),
        )

        val response = repository.searchStores(query("example"))

        val group = response.groups.single()
        assertEquals("org.example.app", group.packageName)
        assertEquals("F-Droid", group.primaryStore.storeName)
        assertEquals(listOf("F-Droid", "IzzyOnDroid"), group.stores.map { it.storeName })
        assertEquals(listOf("1.0", "2.0"), group.stores.map { it.versionName })
        assertEquals(listOf("Tracking"), group.antiFeatures)
        assertEquals(NOW - 1_000L, group.stores.first().fetchedAtEpochMillis)
        assertEquals(SnapshotFreshness.FRESH, group.stores.first().freshness)
        assertEquals(SignatureStatus.VERIFIED, group.stores.first().signatureStatus)
    }

    @Test
    fun `search runs across every enabled indexed store`() = runTest {
        val repository = repository(
            entries = listOf(entry("fdroid", "F-Droid"), entry("izzy", "IzzyOnDroid")),
            catalogs = mapOf(
                "fdroid" to catalog("fdroid", example()),
                "izzy" to catalog("izzy", example()),
            ),
        )

        val response = repository.searchStores(query("example"))

        assertEquals(2, response.groups.single().stores.size)
        assertEquals(2, response.indexedStoreCount)
    }

    @Test
    fun `store filter combines with grouping`() = runTest {
        val repository = repository(
            entries = listOf(entry("fdroid", "F-Droid"), entry("izzy", "IzzyOnDroid")),
            catalogs = mapOf(
                "fdroid" to catalog("fdroid", example(version = "1.0")),
                "izzy" to catalog("izzy", example(version = "2.0")),
            ),
        )

        val response = repository.searchStores(
            query("example", storeIds = setOf("izzy")),
        )

        val group = response.groups.single()
        assertEquals(listOf("IzzyOnDroid"), group.stores.map { it.storeName })
        assertEquals("2.0", group.primaryStore.versionName)
        assertEquals(listOf("F-Droid", "IzzyOnDroid"), response.storeOptions.map { it.storeName })
    }

    @Test
    fun `trust filters apply per store before grouping`() = runTest {
        val repository = repository(
            entries = listOf(entry("fdroid", "F-Droid"), entry("izzy", "IzzyOnDroid")),
            snapshots = mapOf(
                "fdroid" to snapshot("fdroid", SignatureStatus.VERIFIED),
                "izzy" to snapshot("izzy", SignatureStatus.UNVERIFIED),
            ),
            catalogs = mapOf(
                "fdroid" to catalog("fdroid", example(license = "MIT", source = null)),
                "izzy" to catalog("izzy", example()),
            ),
        )

        val verified = repository.searchStores(
            query("example", trustFilter = StoreSearchTrustFilter.VERIFIED_INDEX),
        )
        val openSource = repository.searchStores(
            query("example", trustFilter = StoreSearchTrustFilter.OPEN_SOURCE),
        )

        assertEquals(listOf("F-Droid"), verified.groups.single().stores.map { it.storeName })
        assertEquals(
            listOf("IzzyOnDroid"),
            openSource.groups.single().stores.map { it.storeName },
        )
    }

    @Test
    fun `anti feature filter keeps only stores without anti features`() = runTest {
        val repository = repository(
            entries = listOf(entry("fdroid", "F-Droid"), entry("izzy", "IzzyOnDroid")),
            catalogs = mapOf(
                "fdroid" to catalog(
                    "fdroid",
                    example(antiFeatures = listOf("NonFreeNet")),
                ),
                "izzy" to catalog("izzy", example()),
            ),
        )

        val response = repository.searchStores(
            query("example", trustFilter = StoreSearchTrustFilter.NO_ANTI_FEATURES),
        )

        assertEquals(listOf("IzzyOnDroid"), response.groups.single().stores.map { it.storeName })
    }

    @Test
    fun `link only stores are explicit handoffs and never parsed`() = runTest {
        val repository = repository(
            entries = listOf(
                entry("fdroid", "F-Droid"),
                entry(
                    id = "vendor",
                    name = "Vendor Store",
                    matchable = false,
                    reason = LinkOnlyReason.MISSING_CATALOG_ENDPOINT,
                ),
            ),
            catalogs = mapOf("fdroid" to catalog("fdroid", example())),
        )

        val all = repository.searchStores(query("example"))
        val linkOnly = repository.searchStores(
            query("example", availabilityFilter = StoreSearchAvailabilityFilter.LINK_ONLY),
        )
        val indexed = repository.searchStores(
            query("example", availabilityFilter = StoreSearchAvailabilityFilter.INDEXED),
        )

        assertEquals(listOf("fdroid"), all.groups.single().stores.map { it.storeId })
        assertEquals("vendor", all.linkOnlyStores.single().storeId)
        assertEquals("https://vendor.example", all.linkOnlyStores.single().homepage)
        assertEquals(LinkOnlyReason.MISSING_CATALOG_ENDPOINT, all.linkOnlyStores.single().reason)
        assertTrue(linkOnly.groups.isEmpty())
        assertEquals(1, linkOnly.linkOnlyStores.size)
        assertEquals(1, indexed.groups.size)
        assertTrue(indexed.linkOnlyStores.isEmpty())
        assertFalse(all.storeOptions.first { it.storeId == "vendor" }.indexed)
    }

    @Test
    fun `missing and unreadable caches are reported instead of looking empty`() = runTest {
        val repository = repository(
            entries = listOf(entry("fdroid", "F-Droid"), entry("izzy", "IzzyOnDroid")),
            snapshots = mapOf("izzy" to snapshot("izzy")),
            catalogs = mapOf("izzy" to catalog("izzy", example())),
            failures = setOf("izzy"),
        )

        val response = repository.searchStores(query("example"))

        assertTrue(response.groups.isEmpty())
        assertEquals(
            listOf(
                StoreSearchUnavailable("fdroid", "F-Droid", StoreSearchUnavailableReason.NEVER_SYNCED),
                StoreSearchUnavailable(
                    "izzy",
                    "IzzyOnDroid",
                    StoreSearchUnavailableReason.INDEX_UNREADABLE,
                ),
            ),
            response.unavailableStores,
        )
        assertEquals(
            SnapshotFreshness.NEVER_SYNCED,
            response.storeOptions.first { it.storeId == "fdroid" }.freshness,
        )
    }

    @Test
    fun `disabled stores are excluded from results options and handoffs`() = runTest {
        val repository = repository(
            entries = listOf(
                entry("fdroid", "F-Droid", enabled = false),
                entry("izzy", "IzzyOnDroid"),
                entry("vendor", "Vendor", enabled = false, matchable = false),
            ),
            catalogs = mapOf("izzy" to catalog("izzy", example())),
        )

        val response = repository.searchStores(query("example"))

        assertEquals(listOf("izzy"), response.storeOptions.map { it.storeId })
        assertEquals(listOf("izzy"), response.groups.single().stores.map { it.storeId })
        assertTrue(response.linkOnlyStores.isEmpty())
    }

    @Test
    fun `search matches summary and package text`() = runTest {
        val repository = repository(
            entries = listOf(entry("fdroid", "F-Droid")),
            catalogs = mapOf(
                "fdroid" to catalog(
                    "fdroid",
                    example(summary = "Offline password manager"),
                ),
            ),
        )

        val bySummary = repository.searchStores(query("password"))
        val byPackage = repository.searchStores(query("org.example"))

        assertEquals("org.example.app", bySummary.groups.single().packageName)
        assertEquals("org.example.app", byPackage.groups.single().packageName)
    }

    @Test
    fun `blank queries do not read indexes or return results`() = runTest {
        val repository = repository(
            entries = listOf(entry("fdroid", "F-Droid")),
            catalogs = mapOf("fdroid" to catalog("fdroid", example())),
        )

        val response = repository.searchStores(query("   "))

        assertTrue(response.groups.isEmpty())
        assertTrue(response.linkOnlyStores.isEmpty())
    }

    private fun repository(
        entries: List<StoreEntry>,
        snapshots: Map<String, StoreSnapshot> = entries
            .filter { it.availability.mode == StoreMode.MATCHABLE }
            .associate { it.descriptor.id.value to snapshot(it.descriptor.id.value) },
        catalogs: Map<String, StoreCatalogData>,
        failures: Set<String> = emptySet(),
    ) = MatchingRepository(
        storeEntries = flowOf(entries),
        snapshots = flowOf(snapshots),
        catalog = FakeCatalog(catalogs, failures),
        now = { NOW },
        freshnessWindowMillis = FRESHNESS,
    )

    private fun query(
        text: String,
        storeIds: Set<String> = emptySet(),
        trustFilter: StoreSearchTrustFilter = StoreSearchTrustFilter.ALL,
        availabilityFilter: StoreSearchAvailabilityFilter = StoreSearchAvailabilityFilter.ALL,
    ) = StoreSearchQuery(
        text = text,
        storeIds = storeIds,
        trustFilter = trustFilter,
        availabilityFilter = availabilityFilter,
    )

    private fun catalog(storeId: String, entry: CatalogEntry) =
        StoreCatalogData(storeId, listOf(entry))

    private fun example(
        license: String? = "MIT",
        source: String? = "https://example.org/src",
        version: String = "1.0",
        summary: String = "An example app",
        antiFeatures: List<String> = emptyList(),
    ) = CatalogEntry(
        packageName = "org.example.app",
        name = "Example",
        summary = summary,
        categories = listOf("Tools"),
        license = license,
        sourceCode = source,
        versionName = version,
        versionCode = version.substringBefore('.').toLong(),
        antiFeatures = antiFeatures,
        signerSha256 = listOf("aaaa"),
    )

    private fun entry(
        id: String,
        name: String,
        enabled: Boolean = true,
        matchable: Boolean = true,
        reason: LinkOnlyReason = LinkOnlyReason.ADAPTER_UNSUPPORTED,
    ) = StoreEntry(
        descriptor = StoreDescriptor(
            id = StoreId(id),
            name = name,
            homepage = "https://$id.example",
            adapterType = AdapterType.FDROID_V2,
            indexUrl = "https://$id.example/repo",
            enabled = enabled,
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
        fetchedAt: Long = NOW - 1_000L,
    ) = StoreSnapshot(
        storeId = id,
        fetchedAtEpochMillis = fetchedAt,
        packageCount = 1,
        payloadPath = "$id.ndjson",
        payloadSizeBytes = 10L,
        signatureStatus = signatureStatus,
        sourceUrl = "https://$id.example/repo",
    )

    private class FakeCatalog(
        private val catalogs: Map<String, StoreCatalogData>,
        private val failures: Set<String>,
    ) : StoreCatalog {
        override suspend fun load(snapshot: StoreSnapshot): StoreCatalogData {
            if (snapshot.storeId in failures) {
                throw IllegalStateException("Unreadable cache")
            }
            return catalogs[snapshot.storeId]
                ?: throw IllegalStateException("No catalog for ${snapshot.storeId}")
        }
    }

    private companion object {
        const val NOW = 10_000_000L
        const val FRESHNESS = 24L * 60L * 60L * 1_000L
    }
}
