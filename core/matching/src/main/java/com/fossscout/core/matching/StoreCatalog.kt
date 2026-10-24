package com.fossscout.core.matching

import com.fossscout.core.index.FdroidV2Parser
import com.fossscout.core.index.SnapshotPayloadStore
import com.fossscout.core.index.StorePackage
import com.fossscout.core.index.StoreSnapshot
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Lean projection of a store index record. Descriptions are not retained: the
 * matching engine only needs identity, license/source evidence, categories,
 * version, signer, and a short summary for alternative ranking.
 */
data class CatalogEntry(
    val packageName: String,
    val name: String,
    val summary: String = "",
    val categories: List<String> = emptyList(),
    val license: String? = null,
    val sourceCode: String? = null,
    val versionName: String? = null,
    val signerSha256: List<String> = emptyList(),
) {
    init {
        require(packageName.isNotBlank()) { "Catalog package is blank" }
        require(name.isNotBlank()) { "Catalog name is blank" }
    }

    val hasLicense: Boolean get() = !license.isNullOrBlank()
    val hasSource: Boolean get() = !sourceCode.isNullOrBlank()
}

/** One store's cached catalog with the indexes the matching engine needs. */
data class StoreCatalogData(
    val storeId: String,
    val entries: List<CatalogEntry> = emptyList(),
) {
    init {
        require(storeId.isNotBlank()) { "Catalog store id is blank" }
    }

    val byPackage: Map<String, CatalogEntry> =
        entries.groupBy(CatalogEntry::packageName).mapValues { it.value.first() }

    val bySource: Map<String, List<CatalogEntry>> =
        entries.mapNotNull { entry ->
            normalizeSourceUrl(entry.sourceCode)?.let { source -> source to entry }
        }.groupBy({ it.first }, { it.second })
}

/** Reads a verified snapshot into a catalog projection. */
interface StoreCatalog {
    suspend fun load(snapshot: StoreSnapshot): StoreCatalogData
}

/**
 * File-backed catalog source with a small cache keyed by payload path.
 *
 * A new sync writes a new payload path, so a cache entry can never mix data from
 * two index generations. The cache is bounded because a device may have several
 * F-Droid-compatible stores.
 */
class SnapshotCatalogSource(
    private val payloadStore: SnapshotPayloadStore,
    private val parser: FdroidV2Parser = FdroidV2Parser(),
    private val maxCachedCatalogs: Int = 4,
) : StoreCatalog {
    private val mutex = Mutex()
    private val cache = LinkedHashMap<String, StoreCatalogData>()

    init {
        require(maxCachedCatalogs > 0) { "Catalog cache must hold at least one store" }
    }

    override suspend fun load(snapshot: StoreSnapshot): StoreCatalogData = mutex.withLock {
        cache.remove(snapshot.payloadPath)?.let { cached ->
            // Re-insert to keep the least-recently-used entry at the front.
            cache[snapshot.payloadPath] = cached
            return@withLock cached
        }

        val entries = mutableListOf<CatalogEntry>()
        payloadStore.forEachPackage(snapshot.payloadPath, parser) { packageRecord ->
            entries += packageRecord.toCatalogEntry()
            true
        }
        val data = StoreCatalogData(storeId = snapshot.storeId, entries = entries)
        cache[snapshot.payloadPath] = data
        while (cache.size > maxCachedCatalogs) {
            val oldest = cache.keys.firstOrNull() ?: break
            cache.remove(oldest)
        }
        data
    }
}

internal fun StorePackage.toCatalogEntry(): CatalogEntry {
    val latest = latestVersion()
    return CatalogEntry(
        packageName = packageName,
        name = localizedName(),
        summary = localizedSummary(),
        categories = categories,
        license = license?.trim()?.takeIf(String::isNotEmpty),
        sourceCode = sourceCode?.trim()?.takeIf(String::isNotEmpty),
        versionName = latest?.versionName?.trim()?.takeIf(String::isNotEmpty),
        signerSha256 = latest?.signerSha256?.map { it.trim().lowercase() }
            ?.filter(String::isNotEmpty)
            .orEmpty(),
    )
}

internal fun normalizeSourceUrl(value: String?): String? =
    value?.trim()?.trimEnd('/')?.lowercase()?.takeIf(String::isNotEmpty)
