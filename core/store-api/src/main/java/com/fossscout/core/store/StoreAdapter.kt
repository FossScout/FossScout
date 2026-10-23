package com.fossscout.core.store

/**
 * The operations a store adapter may provide. Adapters advertise a subset so
 * unsupported behavior can be surfaced instead of silently returning nothing.
 */
enum class StoreAdapterCapability {
    SEARCH,
    LISTING,
    VERSIONS,
    ARTIFACT_RESOLUTION,
    SYNC,
    VERIFICATION,
}

enum class StoreCapabilityUnsupportedReason {
    NOT_IMPLEMENTED,
    NO_CATALOG_INDEX,
    NO_OFFLINE_DATA,
    REQUIRES_PAGE_FETCH,
    NO_ARTIFACT_URL,
    NO_SIGNATURE_METADATA,
}

data class StoreAdapterCapabilities(
    val adapterType: AdapterType,
    val supported: Set<StoreAdapterCapability>,
    val unsupported: Map<StoreAdapterCapability, StoreCapabilityUnsupportedReason> = emptyMap(),
) {
    init {
        require(supported.isNotEmpty()) { "An adapter must support at least one capability" }
    }

    fun supports(capability: StoreAdapterCapability): Boolean = capability in supported

    fun unsupportedReason(capability: StoreAdapterCapability): StoreCapabilityUnsupportedReason? =
        if (supports(capability)) {
            null
        } else {
            unsupported[capability] ?: StoreCapabilityUnsupportedReason.NOT_IMPLEMENTED
        }
}

object StoreAdapterCapabilitiesCatalog {
    fun forType(type: AdapterType): StoreAdapterCapabilities = when (type) {
        AdapterType.FDROID_V2,
        AdapterType.FDROID_V1,
        AdapterType.LIVE_API,
        -> full(type)

        AdapterType.GENERIC_JSON -> full(type).copy(
            unsupported = mapOf(
                StoreAdapterCapability.VERIFICATION to
                    StoreCapabilityUnsupportedReason.NO_SIGNATURE_METADATA,
            ),
        )

        AdapterType.SOURCE_REPOSITORY -> capabilities(
            type = type,
            supported = setOf(
                StoreAdapterCapability.VERSIONS,
                StoreAdapterCapability.ARTIFACT_RESOLUTION,
            ),
        )

        AdapterType.DEEP_LINK -> capabilities(
            type = type,
            supported = setOf(StoreAdapterCapability.SEARCH),
            unsupported = mapOf(
                StoreAdapterCapability.LISTING to
                    StoreCapabilityUnsupportedReason.NO_CATALOG_INDEX,
                StoreAdapterCapability.VERSIONS to
                    StoreCapabilityUnsupportedReason.NO_CATALOG_INDEX,
                StoreAdapterCapability.ARTIFACT_RESOLUTION to
                    StoreCapabilityUnsupportedReason.NO_ARTIFACT_URL,
                StoreAdapterCapability.SYNC to
                    StoreCapabilityUnsupportedReason.NO_CATALOG_INDEX,
                StoreAdapterCapability.VERIFICATION to
                    StoreCapabilityUnsupportedReason.NO_SIGNATURE_METADATA,
            ),
        )
    }

    private fun full(type: AdapterType) = capabilities(
        type = type,
        supported = StoreAdapterCapability.entries.toSet(),
    )

    private fun capabilities(
        type: AdapterType,
        supported: Set<StoreAdapterCapability>,
        unsupported: Map<StoreAdapterCapability, StoreCapabilityUnsupportedReason> = emptyMap(),
    ): StoreAdapterCapabilities {
        val reasons = StoreAdapterCapability.entries
            .filterNot(supported::contains)
            .associateWith { capability ->
                unsupported[capability] ?: StoreCapabilityUnsupportedReason.NOT_IMPLEMENTED
            }
        return StoreAdapterCapabilities(
            adapterType = type,
            supported = supported,
            unsupported = reasons,
        )
    }
}

/**
 * S02 keeps the matchable set to the F-Droid-compatible adapters that S03 will
 * implement. Every other adapter type is persisted as link-only.
 */
object StoreAdapterSupport {
    val matchableAdapterTypes: Set<AdapterType> = setOf(
        AdapterType.FDROID_V2,
        AdapterType.FDROID_V1,
    )
}

data class StoreSearchRequest(val query: String, val limit: Int = 50)

data class StoreListingRequest(val offset: Int = 0, val limit: Int = 50)

data class StoreVersionsRequest(val packageName: String)

data class StoreArtifactRequest(val packageName: String, val versionCode: Long)

data class StoreSyncRequest(val descriptor: StoreDescriptor)

data class StoreVerifyRequest(val descriptor: StoreDescriptor)

data class StoreAppSummary(
    val packageName: String,
    val name: String,
    val summary: String = "",
    val versionCode: Long? = null,
    val versionName: String? = null,
    val downloadUrl: String? = null,
    val sourceUrl: String? = null,
    val license: String? = null,
)

data class StoreListingPage(
    val items: List<StoreAppSummary>,
    val nextOffset: Int? = null,
)

data class StoreVersion(
    val packageName: String,
    val versionCode: Long,
    val versionName: String,
    val downloadUrl: String? = null,
    val sourceUrl: String? = null,
    val addedEpochMillis: Long? = null,
)

data class StoreArtifact(
    val packageName: String,
    val versionCode: Long,
    val downloadUrl: String,
    val sizeBytes: Long? = null,
    val sha256: String? = null,
)

data class StoreSyncResult(
    val itemCount: Int,
    val indexVersion: String? = null,
    val health: StoreHealth = StoreHealth(status = StoreHealthStatus.OK),
)

data class StoreVerification(
    val verified: Boolean,
    val fingerprint: String? = null,
    val note: String? = null,
)

enum class StoreFailureKind { NETWORK, PARSE, INVALID_RESPONSE, IO, UNKNOWN }

data class StoreAdapterFailure(
    val kind: StoreFailureKind,
    val message: String,
)

sealed interface StoreAdapterResult<out T> {
    data class Success<T>(val value: T) : StoreAdapterResult<T>

    data class Failure(val failure: StoreAdapterFailure) : StoreAdapterResult<Nothing>

    data class Unsupported(
        val capability: StoreAdapterCapability,
        val reason: StoreCapabilityUnsupportedReason,
    ) : StoreAdapterResult<Nothing>
}

/**
 * Implemented by S03 and test fakes. S02 only ships the contract, so no real
 * adapter performs network calls yet.
 */
interface StoreAdapter {
    val adapterType: AdapterType
    val capabilities: StoreAdapterCapabilities

    suspend fun search(request: StoreSearchRequest): StoreAdapterResult<List<StoreAppSummary>>

    suspend fun list(request: StoreListingRequest): StoreAdapterResult<StoreListingPage>

    suspend fun versions(
        request: StoreVersionsRequest,
    ): StoreAdapterResult<List<StoreVersion>>

    suspend fun resolveArtifact(
        request: StoreArtifactRequest,
    ): StoreAdapterResult<StoreArtifact>

    suspend fun sync(request: StoreSyncRequest): StoreAdapterResult<StoreSyncResult>

    suspend fun verify(request: StoreVerifyRequest): StoreAdapterResult<StoreVerification>
}
