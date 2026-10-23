package com.fossscout.core.store

import com.fossscout.core.model.StoreId
import kotlinx.serialization.Serializable

const val STORE_DESCRIPTOR_SCHEMA_VERSION = 1
const val STORE_REGISTRY_SCHEMA_VERSION = 1
const val STORE_EXPORT_SCHEMA_VERSION = 1

/** How a store exposes its catalog and artifacts. */
@Serializable
enum class AdapterType {
    FDROID_V2,
    FDROID_V1,
    LIVE_API,
    GENERIC_JSON,
    SOURCE_REPOSITORY,
    DEEP_LINK,
}

@Serializable
enum class StoreHealthStatus { UNKNOWN, OK, DEGRADED, ERROR }

/**
 * Per-store health record. S03 fills this in during sync; S02 only persists it
 * and leaves new stores in [StoreHealthStatus.UNKNOWN].
 */
@Serializable
data class StoreHealth(
    val status: StoreHealthStatus = StoreHealthStatus.UNKNOWN,
    val lastSyncEpochMillis: Long? = null,
    val itemCount: Int? = null,
    val error: String? = null,
    val latencyMillis: Long? = null,
) {
    init {
        require(itemCount == null || itemCount >= 0) { "Item count must not be negative" }
        require(latencyMillis == null || latencyMillis >= 0) {
            "Latency must not be negative"
        }
    }
}

/**
 * A store the registry can query. Values are security-sensitive: in particular
 * [fingerprint] must come from an official source and is never guessed.
 */
@Serializable
data class StoreDescriptor(
    val id: StoreId,
    val name: String,
    val homepage: String,
    val adapterType: AdapterType,
    val indexUrl: String? = null,
    val apiUrl: String? = null,
    val repositoryUrl: String? = null,
    val fingerprint: String? = null,
    val enabled: Boolean = true,
    val priority: Int = 0,
    val trustNote: String = "",
    val health: StoreHealth = StoreHealth(),
    val schemaVersion: Int = STORE_DESCRIPTOR_SCHEMA_VERSION,
    val allowInsecureHttp: Boolean = false,
    val builtIn: Boolean = false,
) {
    /**
     * Matching availability is derived instead of persisted so a later build
     * can make a previously unsupported adapter usable without stale state.
     * Disabled stores are still [StoreMode.MATCHABLE]; callers must also check
     * [enabled] (see [StoreEntry.matchable]).
     */
    fun availability(
        supportedAdapterTypes: Set<AdapterType> = StoreAdapterSupport.matchableAdapterTypes,
    ): StoreAvailability {
        if (adapterType !in supportedAdapterTypes) {
            return StoreAvailability(StoreMode.LINK_ONLY, LinkOnlyReason.ADAPTER_UNSUPPORTED)
        }
        if (!hasRequiredEndpoint()) {
            return StoreAvailability(StoreMode.LINK_ONLY, LinkOnlyReason.MISSING_CATALOG_ENDPOINT)
        }
        return StoreAvailability(StoreMode.MATCHABLE)
    }

    private fun hasRequiredEndpoint(): Boolean = when (adapterType) {
        AdapterType.FDROID_V1,
        AdapterType.FDROID_V2,
        AdapterType.GENERIC_JSON,
        -> !indexUrl.isNullOrBlank()

        AdapterType.LIVE_API -> !apiUrl.isNullOrBlank()
        AdapterType.SOURCE_REPOSITORY -> !repositoryUrl.isNullOrBlank()
        AdapterType.DEEP_LINK -> true
    }
}

@Serializable
data class StoreRegistry(
    val schemaVersion: Int = STORE_REGISTRY_SCHEMA_VERSION,
    val stores: List<StoreDescriptor> = emptyList(),
) {
    fun sortedByPriority(): StoreRegistry =
        copy(stores = stores.sortedBy(StoreDescriptor::priority))
}

/** Portable, versioned wrapper for exporting one descriptor. */
@Serializable
data class StoreDescriptorExport(
    val schemaVersion: Int = STORE_EXPORT_SCHEMA_VERSION,
    val descriptor: StoreDescriptor,
)

enum class StoreMode { MATCHABLE, LINK_ONLY }

/** Why a store cannot contribute catalog results. */
enum class LinkOnlyReason {
    ADAPTER_UNSUPPORTED,
    MISSING_CATALOG_ENDPOINT,
}

data class StoreAvailability(
    val mode: StoreMode,
    val reason: LinkOnlyReason? = null,
) {
    init {
        require((mode == StoreMode.LINK_ONLY) == (reason != null)) {
            "LINK_ONLY stores need a reason and MATCHABLE stores must not have one"
        }
    }
}

/**
 * A registry entry plus derived matching state. UI and matching code must use
 * this instead of re-deriving availability, so link-only stores cannot leak
 * into queries.
 */
data class StoreEntry(
    val descriptor: StoreDescriptor,
    val availability: StoreAvailability,
) {
    val matchable: Boolean
        get() = descriptor.enabled && availability.mode == StoreMode.MATCHABLE

    val excludedFromMatching: Boolean
        get() = !matchable
}

/** User-editable fields for adding or editing a store. */
data class StoreDraft(
    val name: String,
    val homepage: String,
    val adapterType: AdapterType,
    val indexUrl: String = "",
    val apiUrl: String = "",
    val repositoryUrl: String = "",
    val fingerprint: String = "",
    val trustNote: String = "",
    val allowInsecureHttp: Boolean = false,
)
