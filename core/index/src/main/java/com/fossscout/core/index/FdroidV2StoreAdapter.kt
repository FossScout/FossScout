package com.fossscout.core.index

import com.fossscout.core.store.AdapterType
import com.fossscout.core.store.StoreAdapter
import com.fossscout.core.store.StoreAdapterCapabilities
import com.fossscout.core.store.StoreAdapterCapabilitiesCatalog
import com.fossscout.core.store.StoreAdapterFailure
import com.fossscout.core.store.StoreAdapterResult
import com.fossscout.core.store.StoreAppSummary
import com.fossscout.core.store.StoreArtifact
import com.fossscout.core.store.StoreArtifactRequest
import com.fossscout.core.store.StoreDescriptor
import com.fossscout.core.store.StoreFailureKind
import com.fossscout.core.store.StoreHealth
import com.fossscout.core.store.StoreHealthStatus
import com.fossscout.core.store.StoreListingPage
import com.fossscout.core.store.StoreListingRequest
import com.fossscout.core.store.StoreSearchRequest
import com.fossscout.core.store.StoreSyncRequest
import com.fossscout.core.store.StoreSyncResult
import com.fossscout.core.store.StoreVerification
import com.fossscout.core.store.StoreVerifyRequest
import com.fossscout.core.store.StoreVersion
import com.fossscout.core.store.StoreVersionsRequest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first

/**
 * Read-only adapter over the last verified snapshot. This is what makes a
 * user-added F-Droid repository usable after a successful sync (F04-R2).
 */
class FdroidV2StoreAdapter(
    private val descriptor: StoreDescriptor,
    private val metadataStore: SnapshotMetadataStore,
    private val payloadStore: SnapshotPayloadStore,
    private val syncRepository: IndexSyncRepository,
    private val parser: FdroidV2Parser = FdroidV2Parser(),
) : StoreAdapter {
    override val adapterType: AdapterType = AdapterType.FDROID_V2
    override val capabilities: StoreAdapterCapabilities =
        StoreAdapterCapabilitiesCatalog.forType(AdapterType.FDROID_V2)

    override suspend fun search(
        request: StoreSearchRequest,
    ): StoreAdapterResult<List<StoreAppSummary>> = guarded {
        val limit = request.limit.coerceAtLeast(1)
        val matches = mutableListOf<StoreAppSummary>()
        val snapshot = snapshot() ?: return@guarded emptyList()
        payloadStore.forEachPackage(snapshot.payloadPath, parser) { packageRecord ->
            if (packageRecord.matches(request.query)) {
                matches += packageRecord.toSummary()
            }
            matches.size < limit
        }
        matches
    }

    override suspend fun list(
        request: StoreListingRequest,
    ): StoreAdapterResult<StoreListingPage> = guarded {
        val offset = request.offset.coerceAtLeast(0)
        val limit = request.limit.coerceAtLeast(1)
        val items = mutableListOf<StoreAppSummary>()
        var seen = 0
        var hasMore = false
        val snapshot = snapshot() ?: return@guarded StoreListingPage(emptyList())
        payloadStore.forEachPackage(snapshot.payloadPath, parser) { packageRecord ->
            if (seen >= offset) {
                if (items.size < limit) {
                    items += packageRecord.toSummary()
                } else {
                    hasMore = true
                    return@forEachPackage false
                }
            }
            seen++
            true
        }
        StoreListingPage(
            items = items,
            nextOffset = if (hasMore) offset + items.size else null,
        )
    }

    override suspend fun versions(
        request: StoreVersionsRequest,
    ): StoreAdapterResult<List<StoreVersion>> = guarded {
        val packageRecord = findPackage(request.packageName) ?: return@guarded emptyList()
        packageRecord.versions.map { version ->
            StoreVersion(
                packageName = packageRecord.packageName,
                versionCode = version.versionCode,
                versionName = version.versionName,
                downloadUrl = version.artifactUrl(),
                sourceUrl = packageRecord.sourceCode,
                addedEpochMillis = version.addedEpochMillis,
            )
        }
    }

    override suspend fun resolveArtifact(
        request: StoreArtifactRequest,
    ): StoreAdapterResult<StoreArtifact> = guarded {
        val packageRecord = findPackage(request.packageName)
        val version = packageRecord?.versions
            ?.firstOrNull { it.versionCode == request.versionCode }
        val url = version?.artifactUrl()
        if (version == null || url == null) {
            throw IndexSyncException(
                SyncFailureKind.IO,
                "Version ${request.versionCode} is not in the cached index",
            )
        }
        StoreArtifact(
            packageName = request.packageName,
            versionCode = version.versionCode,
            downloadUrl = url,
            sizeBytes = version.sizeBytes,
            sha256 = version.fileSha256,
        )
    }

    override suspend fun sync(
        request: StoreSyncRequest,
    ): StoreAdapterResult<StoreSyncResult> {
        if (request.descriptor.id != descriptor.id) {
            return StoreAdapterResult.Failure(
                StoreAdapterFailure(
                    StoreFailureKind.UNKNOWN,
                    "Adapter is bound to ${descriptor.id.value}",
                ),
            )
        }
        return try {
            val result = syncRepository.sync(request.descriptor)
            when (result.job.state) {
                SyncState.SUCCEEDED -> {
                    val snapshot = result.snapshot
                    StoreAdapterResult.Success(
                        StoreSyncResult(
                            itemCount = snapshot?.packageCount ?: 0,
                            indexVersion = snapshot?.indexVersion?.toString(),
                            health = StoreHealth(
                                status = StoreHealthStatus.OK,
                                lastSyncEpochMillis = snapshot?.fetchedAtEpochMillis,
                                itemCount = snapshot?.packageCount,
                            ),
                        ),
                    )
                }

                else -> StoreAdapterResult.Failure(
                    StoreAdapterFailure(
                        kind = result.job.error?.kind.toStoreFailureKind(),
                        message = result.job.error?.message ?: "Index sync failed",
                    ),
                )
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (exception: Exception) {
            StoreAdapterResult.Failure(
                StoreAdapterFailure(
                    StoreFailureKind.UNKNOWN,
                    exception.message ?: "Index sync failed",
                ),
            )
        }
    }

    override suspend fun verify(
        request: StoreVerifyRequest,
    ): StoreAdapterResult<StoreVerification> = guarded {
        val state = metadataStore.state.first()
        val snapshot = state.snapshots[descriptor.id.value]
        val trust = state.trustRecords[descriptor.id.value]
        when (snapshot?.signatureStatus) {
            SignatureStatus.VERIFIED -> StoreVerification(
                verified = true,
                fingerprint = trust?.signerFingerprint,
                note = "Signed index verified",
            )

            SignatureStatus.UNVERIFIED -> StoreVerification(
                verified = false,
                fingerprint = trust?.signerFingerprint,
                note = "Signature is valid but the repository fingerprint is not pinned",
            )

            else -> StoreVerification(
                verified = false,
                fingerprint = trust?.signerFingerprint,
                note = trust?.failure?.name ?: "No verified snapshot",
            )
        }
    }

    private suspend fun snapshot(): StoreSnapshot? =
        metadataStore.state.first().snapshots[descriptor.id.value]

    private suspend fun findPackage(packageName: String): StorePackage? {
        val snapshot = snapshot() ?: return null
        var match: StorePackage? = null
        payloadStore.forEachPackage(snapshot.payloadPath, parser) { packageRecord ->
            if (packageRecord.packageName == packageName) {
                match = packageRecord
                false
            } else {
                true
            }
        }
        return match
    }

    private fun StorePackage.toSummary(): StoreAppSummary {
        val latest = latestVersion()
        return StoreAppSummary(
            packageName = packageName,
            name = localizedName(),
            summary = localizedSummary(),
            versionCode = latest?.versionCode,
            versionName = latest?.versionName,
            downloadUrl = latest?.artifactUrl(),
            sourceUrl = sourceCode,
            license = license,
        )
    }

    private fun StorePackageVersion.artifactUrl(): String? {
        val file = fileName ?: return null
        val base = descriptor.indexUrl?.trim()?.trimEnd('/') ?: return null
        return base + "/" + file.trimStart('/')
    }

    private suspend fun <T> guarded(
        block: suspend () -> T,
    ): StoreAdapterResult<T> = try {
        StoreAdapterResult.Success(block())
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (exception: Exception) {
        StoreAdapterResult.Failure(
            StoreAdapterFailure(
                StoreFailureKind.IO,
                exception.message ?: "Could not read the cached index",
            ),
        )
    }

    private fun SyncFailureKind?.toStoreFailureKind(): StoreFailureKind = when (this) {
        SyncFailureKind.PARSE -> StoreFailureKind.PARSE
        SyncFailureKind.NETWORK,
        SyncFailureKind.HTTP_STATUS,
        SyncFailureKind.HTTP_POLICY,
        -> StoreFailureKind.NETWORK

        SyncFailureKind.VERIFICATION -> StoreFailureKind.INVALID_RESPONSE
        SyncFailureKind.IO,
        SyncFailureKind.STORAGE_LIMIT,
        -> StoreFailureKind.IO

        else -> StoreFailureKind.UNKNOWN
    }
}
