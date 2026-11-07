package com.fossscout.core.matching

import com.fossscout.core.index.SignatureStatus
import com.fossscout.core.index.SnapshotFreshness
import com.fossscout.core.store.LinkOnlyReason

/** Trust filters available to unified store search. */
enum class StoreSearchTrustFilter {
    ALL,
    VERIFIED_INDEX,
    OPEN_SOURCE,
    NO_ANTI_FEATURES,
}

/** Whether to show cached-index results, link-only stores, or both. */
enum class StoreSearchAvailabilityFilter {
    ALL,
    INDEXED,
    LINK_ONLY,
}

data class StoreSearchQuery(
    val text: String,
    val storeIds: Set<String> = emptySet(),
    val trustFilter: StoreSearchTrustFilter = StoreSearchTrustFilter.ALL,
    val availabilityFilter: StoreSearchAvailabilityFilter =
        StoreSearchAvailabilityFilter.ALL,
)

/** One enabled store and the search mode it supports. */
data class StoreSearchStoreOption(
    val storeId: String,
    val storeName: String,
    val priority: Int,
    val indexed: Boolean,
    val freshness: SnapshotFreshness = SnapshotFreshness.NEVER_SYNCED,
    val lastSyncEpochMillis: Long? = null,
)

/**
 * Store-specific metadata for one package. Per-store versions, licenses, and
 * anti-features are retained so grouping never hides a source difference.
 */
data class StoreSearchStoreHit(
    val storeId: String,
    val storeName: String,
    val priority: Int,
    val license: String? = null,
    val sourceCode: String? = null,
    val versionName: String? = null,
    val versionCode: Long? = null,
    val antiFeatures: List<String> = emptyList(),
    val fetchedAtEpochMillis: Long? = null,
    val freshness: SnapshotFreshness = SnapshotFreshness.NEVER_SYNCED,
    val signatureStatus: SignatureStatus? = null,
) {
    val hasOpenSourceMetadata: Boolean
        get() = !license.isNullOrBlank() && !sourceCode.isNullOrBlank()

    val isIndexVerified: Boolean
        get() = signatureStatus == SignatureStatus.VERIFIED
}

/** One package grouped across every matching store. */
data class StoreSearchGroup(
    val packageName: String,
    val name: String,
    val summary: String,
    val categories: List<String> = emptyList(),
    val stores: List<StoreSearchStoreHit>,
) {
    init {
        require(packageName.isNotBlank()) { "Search package is blank" }
        require(name.isNotBlank()) { "Search name is blank" }
        require(stores.isNotEmpty()) { "A search group needs at least one store" }
    }

    val primaryStore: StoreSearchStoreHit
        get() = stores.minByOrNull(StoreSearchStoreHit::priority) ?: stores.first()

    val license: String? get() = primaryStore.license

    val sourceCode: String? get() = primaryStore.sourceCode

    val versionName: String? get() = primaryStore.versionName

    val versionCode: Long? get() = primaryStore.versionCode

    val antiFeatures: List<String>
        get() = stores.flatMap(StoreSearchStoreHit::antiFeatures).distinct().sorted()

    val hasOpenSourceMetadata: Boolean
        get() = stores.any(StoreSearchStoreHit::hasOpenSourceMetadata)

    val isIndexVerified: Boolean
        get() = stores.any(StoreSearchStoreHit::isIndexVerified)
}

/** Why an enabled store could not take part in a cached-index search. */
enum class StoreSearchUnavailableReason {
    NEVER_SYNCED,
    INDEX_UNREADABLE,
}

data class StoreSearchUnavailable(
    val storeId: String,
    val storeName: String,
    val reason: StoreSearchUnavailableReason,
)

/** A store that can only be searched by opening its own site. */
data class StoreSearchLinkOnly(
    val storeId: String,
    val storeName: String,
    val homepage: String,
    val reason: LinkOnlyReason,
)

data class StoreSearchResponse(
    val query: String,
    val groups: List<StoreSearchGroup> = emptyList(),
    val linkOnlyStores: List<StoreSearchLinkOnly> = emptyList(),
    val unavailableStores: List<StoreSearchUnavailable> = emptyList(),
    val storeOptions: List<StoreSearchStoreOption> = emptyList(),
    val indexedStoreCount: Int = 0,
    val searchedAtEpochMillis: Long = 0L,
)
