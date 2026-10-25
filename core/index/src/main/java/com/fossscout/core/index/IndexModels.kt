package com.fossscout.core.index

import com.fossscout.core.store.StoreDescriptor
import java.io.File
import kotlinx.serialization.Serializable

const val INDEX_STATE_SCHEMA_VERSION = 1
const val STORE_SNAPSHOT_SCHEMA_VERSION = 1
const val SYNC_JOB_SCHEMA_VERSION = 1
const val TRUST_RECORD_SCHEMA_VERSION = 1

/** Bound for the local verification audit trail; oldest entries are dropped. */
const val MAX_TRUST_AUDIT_ENTRIES = 100

/** Index formats understood by this build. */
@Serializable
enum class IndexFormat { FDROID_V2 }

/**
 * How the active snapshot was authenticated. [VERIFIED] means the entry JAR
 * signature validated and the signer matched the pinned repository
 * fingerprint. [UNVERIFIED] means the signature validated but the repository
 * has no pinned fingerprint. Neither [UNSIGNED] nor [INVALID] can be activated.
 */
@Serializable
enum class SignatureStatus { VERIFIED, UNVERIFIED, UNSIGNED, INVALID, NOT_CHECKED }

@Serializable
enum class VerificationFailure {
    ENTRY_MISSING,
    ENTRY_MALFORMED,
    NO_SIGNATURE,
    TAMPERED,
    SIGNER_MISMATCH,
    HASH_MISMATCH,
    SIZE_MISMATCH,
    NO_PINNED_FINGERPRINT,
    UNSUPPORTED,
}

/** Freshness of the active snapshot. A missing snapshot is never fresh. */
@Serializable
enum class SnapshotFreshness { FRESH, STALE, NEVER_SYNCED }

/** State machine for one store sync. */
@Serializable
enum class SyncState { QUEUED, RUNNING, SUCCEEDED, FAILED, CANCELLED, BLOCKED }

@Serializable
enum class SyncFailureKind {
    UNSUPPORTED,
    HTTP_POLICY,
    FINGERPRINT_CHANGED,
    NETWORK,
    HTTP_STATUS,
    VERIFICATION,
    PARSE,
    STORAGE_LIMIT,
    IO,
    CANCELLED,
    UNKNOWN,
}

@Serializable
data class SyncFailure(
    val kind: SyncFailureKind,
    val message: String,
)

/**
 * Verified metadata for the active index payload. [payloadPath] is relative to
 * the payload store root so the metadata remains portable within the app's
 * private storage.
 */
@Serializable
data class StoreSnapshot(
    val storeId: String,
    val schemaVersion: Int = STORE_SNAPSHOT_SCHEMA_VERSION,
    val fetchedAtEpochMillis: Long,
    val etag: String? = null,
    val lastModified: String? = null,
    val indexVersion: Int? = null,
    val indexTimestamp: Long? = null,
    /** SHA-256 of the signed index this payload was derived from. */
    val indexSha256: String? = null,
    val packageCount: Int,
    val payloadPath: String,
    val payloadSizeBytes: Long,
    val signatureStatus: SignatureStatus,
    val sourceUrl: String,
    val expectedFingerprint: String? = null,
    val signerFingerprint: String? = null,
    val format: IndexFormat = IndexFormat.FDROID_V2,
) {
    init {
        require(storeId.isNotBlank()) { "Store id is blank" }
        require(packageCount >= 0) { "Package count must not be negative" }
        require(payloadPath.isNotBlank()) { "Payload path is blank" }
        require(payloadSizeBytes >= 0) { "Payload size must not be negative" }
        require(signatureStatus != SignatureStatus.UNSIGNED) {
            "An unsigned index must never become an active snapshot"
        }
        require(signatureStatus != SignatureStatus.INVALID) {
            "An invalid index must never become an active snapshot"
        }
    }

    fun freshness(
        nowEpochMillis: Long,
        staleAfterMillis: Long = DEFAULT_STALE_AFTER_MILLIS,
    ): SnapshotFreshness =
        if (nowEpochMillis - fetchedAtEpochMillis > staleAfterMillis) {
            SnapshotFreshness.STALE
        } else {
            SnapshotFreshness.FRESH
        }
}

/** Default: an index older than one day is visibly stale. */
const val DEFAULT_STALE_AFTER_MILLIS: Long = 24L * 60L * 60L * 1_000L

/**
 * Observable progress for one sync. A job is persisted so the Stores UI can
 * show the last attempt even after the process restarts.
 */
@Serializable
data class SyncJob(
    val storeId: String,
    val state: SyncState,
    val startedAtEpochMillis: Long? = null,
    val finishedAtEpochMillis: Long? = null,
    val bytesDownloaded: Long = 0,
    val totalBytes: Long? = null,
    val usedDiff: Boolean = false,
    val usedConditionalRequest: Boolean = false,
    val error: SyncFailure? = null,
    val schemaVersion: Int = SYNC_JOB_SCHEMA_VERSION,
) {
    init {
        require(storeId.isNotBlank()) { "Store id is blank" }
        require(bytesDownloaded >= 0) { "Bytes downloaded must not be negative" }
        require(totalBytes == null || totalBytes >= 0) { "Total bytes must not be negative" }
    }

    val running: Boolean get() = state == SyncState.RUNNING || state == SyncState.QUEUED

    val progress: Float?
        get() {
            val total = totalBytes ?: return null
            if (total <= 0L) return null
            return (bytesDownloaded.toFloat() / total.toFloat()).coerceIn(0f, 1f)
        }
}

/** Result of a signature or digest check. Never inferred from the store label. */
@Serializable
data class VerificationResult(
    val status: SignatureStatus,
    val expectedFingerprint: String? = null,
    val signerFingerprint: String? = null,
    val failure: VerificationFailure? = null,
    val note: String = "",
) {
    val verified: Boolean get() = status == SignatureStatus.VERIFIED

    /**
     * A cryptographically valid index may be activated when it has no pin, but
     * it stays [SignatureStatus.UNVERIFIED] and cannot be used for one-tap
     * installs. Unsigned or invalid data is never activatable.
     */
    val activatable: Boolean
        get() = status == SignatureStatus.VERIFIED || status == SignatureStatus.UNVERIFIED
}

/**
 * Local verification history entry. F14-R6 exposes this evidence to the UI:
 * expected fingerprint, actual signer, signature status, and source URL.
 */
@Serializable
data class TrustRecord(
    val storeId: String,
    val checkedAtEpochMillis: Long,
    val signatureStatus: SignatureStatus,
    val expectedFingerprint: String? = null,
    val signerFingerprint: String? = null,
    val sourceUrl: String,
    val indexVersion: Int? = null,
    val indexTimestamp: Long? = null,
    val failure: VerificationFailure? = null,
    val schemaVersion: Int = TRUST_RECORD_SCHEMA_VERSION,
) {
    init {
        require(storeId.isNotBlank()) { "Store id is blank" }
        require(sourceUrl.isNotBlank()) { "Source URL is blank" }
    }
}

/** Persisted metadata for every store index. Payload bytes live separately. */
@Serializable
data class IndexState(
    val schemaVersion: Int = INDEX_STATE_SCHEMA_VERSION,
    val snapshots: Map<String, StoreSnapshot> = emptyMap(),
    val trustRecords: Map<String, TrustRecord> = emptyMap(),
    val jobs: Map<String, SyncJob> = emptyMap(),
    /** Bounded, append-only local verification history. */
    val trustAudit: List<TrustRecord> = emptyList(),
)

/** First line of every normalized payload file. */
@Serializable
data class SnapshotPayloadHeader(
    val schemaVersion: Int = 1,
    val format: IndexFormat = IndexFormat.FDROID_V2,
    val storeId: String,
    val indexTimestamp: Long? = null,
    val fetchedAtEpochMillis: Long,
) {
    init {
        require(storeId.isNotBlank()) { "Store id is blank" }
    }
}

/** A normalized package record extracted from a signed F-Droid v2 index. */
@Serializable
data class StorePackage(
    val packageName: String,
    val name: Map<String, String> = emptyMap(),
    val summary: Map<String, String> = emptyMap(),
    val description: Map<String, String> = emptyMap(),
    val categories: List<String> = emptyList(),
    val license: String? = null,
    val sourceCode: String? = null,
    val website: String? = null,
    val issueTracker: String? = null,
    val authorName: String? = null,
    val addedAtEpochMillis: Long? = null,
    val lastUpdatedEpochMillis: Long? = null,
    val antiFeatures: Map<String, Map<String, String>> = emptyMap(),
    val versions: List<StorePackageVersion> = emptyList(),
) {
    init {
        require(packageName.isNotBlank()) { "Package name is blank" }
    }

    fun localizedName(locale: String = "en-US"): String =
        name[locale] ?: name["en-US"] ?: name.values.firstOrNull() ?: packageName

    fun localizedSummary(locale: String = "en-US"): String =
        summary[locale] ?: summary["en-US"] ?: summary.values.firstOrNull().orEmpty()

    fun latestVersion(): StorePackageVersion? =
        versions.maxByOrNull(StorePackageVersion::versionCode)

    fun matches(query: String): Boolean {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return false
        return packageName.contains(trimmed, ignoreCase = true) ||
            localizedName().contains(trimmed, ignoreCase = true) ||
            localizedSummary().contains(trimmed, ignoreCase = true)
    }
}

@Serializable
data class StorePackageVersion(
    val fileSha256: String,
    val versionCode: Long,
    val versionName: String = "",
    val fileName: String? = null,
    val sizeBytes: Long? = null,
    val addedEpochMillis: Long? = null,
    val minSdk: Int? = null,
    val targetSdk: Int? = null,
    val signerSha256: List<String> = emptyList(),
    val nativeCode: List<String> = emptyList(),
    val permissions: List<String> = emptyList(),
) {
    init {
        require(fileSha256.isNotBlank()) { "Version file hash is blank" }
        require(sizeBytes == null || sizeBytes >= 0) { "Version size must not be negative" }
    }
}

/** Internal exception used to abort a payload without exposing partial data. */
class IndexSyncException(
    val kind: SyncFailureKind,
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

/** Entry JAR verification request. */
data class EntryVerificationRequest(
    val storeId: String,
    val entryJar: File,
    val expectedFingerprint: String?,
)

/** Digest verification request for a streamed index or diff. */
data class DigestVerificationRequest(
    val storeId: String,
    val expectedSha256: String,
    val actualSha256: String,
    val expectedSize: Long? = null,
    val actualSize: Long? = null,
)

interface IndexVerifier {
    suspend fun verifyEntry(request: EntryVerificationRequest): VerificationResult

    fun verifyDigest(request: DigestVerificationRequest): VerificationResult
}

/** Convenience for the UI layer without coupling it to descriptor details. */
fun StoreDescriptor.pinnedFingerprintOrNull(): String? =
    fingerprint?.trim()?.lowercase()?.replace(":", "")?.replace(" ", "")
        ?.takeIf(String::isNotEmpty)
