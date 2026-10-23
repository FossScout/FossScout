package com.fossscout.core.index

import com.fossscout.core.store.AdapterType
import com.fossscout.core.store.StoreDescriptor
import java.io.File
import java.io.FileOutputStream
import java.io.FilterInputStream
import java.io.InputStream
import java.net.URI
import java.security.DigestInputStream
import java.security.MessageDigest
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

interface SnapshotMetadataStore {
    val state: Flow<IndexState>

    suspend fun save(state: IndexState)
}

data class IndexSyncResult(
    val job: SyncJob,
    val snapshot: StoreSnapshot?,
    val usedDiff: Boolean = false,
)

data class StorageStatus(
    val usageBytes: Long,
    val budgetBytes: Long,
) {
    val overBudget: Boolean get() = usageBytes > budgetBytes
    val remainingBytes: Long get() = (budgetBytes - usageBytes).coerceAtLeast(0)
}

data class StorageCleanupResult(
    val freedBytes: Long,
    val status: StorageStatus,
)

/**
 * Fetches, verifies, and atomically activates an F-Droid v2 index.
 *
 * The security boundary is explicit:
 * 1. `entry.jar` must pass JAR signature verification and match the pinned
 *    repository fingerprint before any index bytes are trusted.
 * 2. The index/diff SHA-256 must match the signed entry.
 * 3. Only then is a new payload renamed and its metadata persisted.
 *
 * A corrupt, tampered, unsigned, cancelled, or over-budget sync never replaces
 * the active snapshot.
 */
class IndexSyncRepository(
    private val metadataStore: SnapshotMetadataStore,
    private val payloadStore: SnapshotPayloadStore,
    private val httpFetcher: HttpFetcher,
    private val tempDir: File,
    private val verifier: IndexVerifier = JarIndexVerifier(),
    private val parser: FdroidV2Parser = FdroidV2Parser(),
    private val storageBudgetBytes: suspend () -> Long = { DEFAULT_STORAGE_BUDGET_BYTES },
    private val now: () -> Long = System::currentTimeMillis,
    private val staleAfterMillis: Long = DEFAULT_STALE_AFTER_MILLIS,
) {
    private val syncMutex = Mutex()
    private val json = Json { encodeDefaults = true }

    val state: Flow<IndexState> = metadataStore.state
    val snapshots: Flow<Map<String, StoreSnapshot>> = state.map { it.snapshots }
    val trustRecords: Flow<Map<String, TrustRecord>> = state.map { it.trustRecords }
    val jobs: Flow<Map<String, SyncJob>> = state.map { it.jobs }

    fun freshness(snapshot: StoreSnapshot?): SnapshotFreshness =
        snapshot?.freshness(now(), staleAfterMillis) ?: SnapshotFreshness.NEVER_SYNCED

    suspend fun storageStatus(): StorageStatus = StorageStatus(
        usageBytes = payloadStore.usageBytes(),
        budgetBytes = storageBudgetBytes(),
    )

    suspend fun cleanupStorage(): StorageCleanupResult = syncMutex.withLock {
        val current = metadataStore.state.first()
        val freed = payloadStore.cleanup(current.snapshots.values.mapTo(mutableSetOf()) {
            it.payloadPath
        })
        StorageCleanupResult(freed, storageStatus())
    }

    suspend fun sync(descriptor: StoreDescriptor): IndexSyncResult = syncMutex.withLock {
        val storeId = descriptor.id.value
        val previous = metadataStore.state.first().snapshots[storeId]
        val startedAt = now()
        var job = SyncJob(
            storeId = storeId,
            state = SyncState.RUNNING,
            startedAtEpochMillis = startedAt,
        )
        persistJob(job)

        if (descriptor.adapterType != AdapterType.FDROID_V2) {
            return@withLock fail(
                job = job,
                previous = previous,
                kind = SyncFailureKind.UNSUPPORTED,
                message = "This build can only sync F-Droid v2 indexes",
                state = SyncState.BLOCKED,
            )
        }

        val baseUrl = descriptor.indexUrl?.trim().orEmpty()
        if (baseUrl.isEmpty()) {
            return@withLock fail(
                job = job,
                previous = previous,
                kind = SyncFailureKind.UNSUPPORTED,
                message = "The store has no index URL",
                state = SyncState.BLOCKED,
            )
        }

        val policyFailure = checkHttpPolicy(baseUrl, descriptor.allowInsecureHttp)
        if (policyFailure != null) {
            return@withLock fail(
                job = job,
                previous = previous,
                kind = SyncFailureKind.HTTP_POLICY,
                message = policyFailure,
                state = SyncState.BLOCKED,
            )
        }

        val pinnedFingerprint = descriptor.pinnedFingerprintOrNull()
        if (
            previous?.expectedFingerprint != null &&
            previous.expectedFingerprint != pinnedFingerprint
        ) {
            return@withLock fail(
                job = job,
                previous = previous,
                kind = SyncFailureKind.FINGERPRINT_CHANGED,
                message = "The repository fingerprint changed. Review it before syncing again.",
                state = SyncState.BLOCKED,
            )
        }

        var entryJar: File? = null
        try {
            val entryUrl = joinRepoPath(baseUrl, "entry.jar")
            val entryPolicyFailure = checkHttpPolicy(entryUrl, descriptor.allowInsecureHttp)
            if (entryPolicyFailure != null) {
                return@withLock fail(
                    job = job,
                    previous = previous,
                    kind = SyncFailureKind.HTTP_POLICY,
                    message = entryPolicyFailure,
                    state = SyncState.BLOCKED,
                )
            }

            val targetDir = tempDir.apply { if (!exists()) mkdirs() }
            entryJar = File(
                targetDir,
                "entry-${safeName(storeId)}-${UUID.randomUUID()}.jar",
            )
            val entryBytes = downloadToFile(
                url = entryUrl,
                allowInsecureHttp = descriptor.allowInsecureHttp,
                target = entryJar,
                maxBytes = MAX_ENTRY_JAR_BYTES,
            )

            val verification = verifier.verifyEntry(
                EntryVerificationRequest(
                    storeId = storeId,
                    entryJar = entryJar,
                    expectedFingerprint = pinnedFingerprint,
                ),
            )
            persistTrust(
                TrustRecord(
                    storeId = storeId,
                    checkedAtEpochMillis = now(),
                    signatureStatus = verification.status,
                    expectedFingerprint = verification.expectedFingerprint,
                    signerFingerprint = verification.signerFingerprint,
                    sourceUrl = entryUrl,
                    failure = verification.failure,
                ),
            )
            if (!verification.activatable) {
                return@withLock fail(
                    job = job,
                    previous = previous,
                    kind = SyncFailureKind.VERIFICATION,
                    message = verification.note.ifBlank {
                        "The signed index could not be verified"
                    },
                )
            }

            val entry = parser.readEntry(entryJar)
            val expectedTargetSize = entry.index.size
            job = job.copy(
                bytesDownloaded = entryBytes,
                totalBytes = entryBytes + expectedTargetSize,
            )
            persistJob(job)
            persistTrust(
                TrustRecord(
                    storeId = storeId,
                    checkedAtEpochMillis = now(),
                    signatureStatus = verification.status,
                    expectedFingerprint = verification.expectedFingerprint,
                    signerFingerprint = verification.signerFingerprint,
                    sourceUrl = entryUrl,
                    indexVersion = entry.version,
                    indexTimestamp = entry.timestamp,
                    failure = verification.failure,
                ),
            )

            // The signed timestamp is an index identity. If it is unchanged,
            // there is nothing to download.
            if (
                previous != null &&
                previous.indexTimestamp == entry.timestamp &&
                previous.indexSha256 == entry.index.sha256 &&
                payloadStore.exists(previous.payloadPath)
            ) {
                val refreshed = previous.copy(
                    fetchedAtEpochMillis = now(),
                    signatureStatus = verification.status,
                    expectedFingerprint = verification.expectedFingerprint,
                    signerFingerprint = verification.signerFingerprint,
                    indexVersion = entry.version,
                    indexSha256 = entry.index.sha256,
                )
                val completed = job.copy(
                    state = SyncState.SUCCEEDED,
                    finishedAtEpochMillis = now(),
                    bytesDownloaded = entryBytes,
                    totalBytes = entryBytes,
                    usedConditionalRequest = false,
                )
                persistCompletion(refreshed, completed)
                return@withLock IndexSyncResult(completed, refreshed)
            }

            val budget = storageBudgetBytes()
            var status = StorageStatus(payloadStore.usageBytes(), budget)
            if (status.usageBytes - (previous?.payloadSizeBytes ?: 0L) >= budget) {
                payloadStore.cleanup(activePayloadPaths())
                status = StorageStatus(payloadStore.usageBytes(), budget)
                if (status.usageBytes - (previous?.payloadSizeBytes ?: 0L) >= budget) {
                    return@withLock fail(
                        job = job,
                        previous = previous,
                        kind = SyncFailureKind.STORAGE_LIMIT,
                        message = "Index storage is at its configured budget. " +
                            "Free space or raise the budget in Settings.",
                        state = SyncState.BLOCKED,
                    )
                }
            }
            val maxNewBytes =
                (budget - (status.usageBytes - (previous?.payloadSizeBytes ?: 0L)))
                    .coerceAtLeast(0L)

            val diffFile = previous?.indexTimestamp?.let { entry.diffs[it.toString()] }
            if (
                previous != null &&
                diffFile != null &&
                diffFile.size < entry.index.size &&
                payloadStore.exists(previous.payloadPath)
            ) {
                val diffResult = runCatching {
                    syncDiff(
                        descriptor = descriptor,
                        baseUrl = baseUrl,
                        entry = entry,
                        diffFile = diffFile,
                        previous = previous,
                        verification = verification,
                        job = job,
                        maxNewBytes = maxNewBytes,
                    )
                }
                val diffValue = diffResult.getOrNull()
                if (diffValue != null) {
                    return@withLock diffValue
                }
                val diffError = diffResult.exceptionOrNull()
                if (diffError is CancellationException) throw diffError
                if (diffError is IndexSyncException &&
                    diffError.kind == SyncFailureKind.STORAGE_LIMIT
                ) {
                    return@withLock fail(
                        job = job,
                        previous = previous,
                        kind = SyncFailureKind.STORAGE_LIMIT,
                        message = diffError.message.orEmpty(),
                        state = SyncState.BLOCKED,
                    )
                }
                // Missing/incomplete diff chain or a bad diff falls back to a
                // full index. The full path still has to verify before activation.
            }

            return@withLock syncFull(
                descriptor = descriptor,
                baseUrl = baseUrl,
                entry = entry,
                previous = previous,
                verification = verification,
                job = job,
                maxNewBytes = maxNewBytes,
            )
        } catch (cancellation: CancellationException) {
            withContext(NonCancellable) {
                persistJob(
                    job.copy(
                        state = SyncState.CANCELLED,
                        finishedAtEpochMillis = now(),
                        error = SyncFailure(SyncFailureKind.CANCELLED, "Sync cancelled"),
                    ),
                )
            }
            throw cancellation
        } catch (exception: IndexSyncException) {
            fail(
                job = job,
                previous = previous,
                kind = exception.kind,
                message = exception.message.orEmpty(),
            )
        } catch (exception: Exception) {
            fail(
                job = job,
                previous = previous,
                kind = SyncFailureKind.UNKNOWN,
                message = exception.message ?: "Index sync failed",
            )
        } finally {
            entryJar?.delete()
        }
    }

    private suspend fun syncDiff(
        descriptor: StoreDescriptor,
        baseUrl: String,
        entry: FdroidEntry,
        diffFile: FdroidEntryFile,
        previous: StoreSnapshot,
        verification: VerificationResult,
        job: SyncJob,
        maxNewBytes: Long,
    ): IndexSyncResult {
        val diffUrl = joinRepoPath(baseUrl, diffFile.name)
        checkHttpPolicy(diffUrl, descriptor.allowInsecureHttp)?.let { message ->
            throw IndexSyncException(SyncFailureKind.HTTP_POLICY, message)
        }
        val request = HttpRequest(
            url = diffUrl,
            allowInsecureHttp = descriptor.allowInsecureHttp,
        )
        val response = httpFetcher.get(request)
        try {
            if (!response.isSuccess || response.body == null) {
                throw IndexSyncException(
                    SyncFailureKind.HTTP_STATUS,
                    "Diff request returned HTTP ${response.status}",
                )
            }
            val counting = CountingInputStream(response.body)
            val digest = DigestInputStream(counting, MessageDigest.getInstance("SHA-256"))
            val diffs = try {
                parser.parseDiff(digest)
            } finally {
                digest.close()
            }
            val actualHash = digest.messageDigest.toHex()
            val integrity = verifier.verifyDigest(
                DigestVerificationRequest(
                    storeId = descriptor.id.value,
                    expectedSha256 = diffFile.sha256,
                    actualSha256 = actualHash,
                    expectedSize = diffFile.size,
                    actualSize = counting.count,
                ),
            )
            if (!integrity.activatable) {
                throw IndexSyncException(
                    SyncFailureKind.VERIFICATION,
                    integrity.note.ifBlank { "Diff integrity check failed" },
                )
            }

            val header = SnapshotPayloadHeader(
                storeId = descriptor.id.value,
                indexTimestamp = entry.timestamp,
                fetchedAtEpochMillis = now(),
            )
            val applied = linkedMapOf<String, StorePackage>()
            val writeResult = payloadStore.writePayload(
                storeId = descriptor.id.value,
                version = entry.timestamp,
                maxBytes = maxNewBytes,
            ) { sink ->
                sink.writeLine(json.encodeToString(SnapshotPayloadHeader.serializer(), header))
                payloadStore.forEachPackage(previous.payloadPath, parser) { previousPackage ->
                    val hasPatch = diffs.containsKey(previousPackage.packageName)
                    val patch = diffs[previousPackage.packageName]
                    val output = when {
                        !hasPatch -> previousPackage
                        patch == null -> null
                        else -> parser.applyDiff(previousPackage, patch)
                    }
                    if (output != null) {
                        sink.writeLine(parser.encodePackage(output))
                        applied[output.packageName] = output
                    }
                    true
                }
                diffs.forEach { (packageName, patch) ->
                    if (packageName !in applied && patch != null) {
                        val created = parser.applyDiff(
                            StorePackage(packageName = packageName),
                            patch,
                        )
                        sink.writeLine(parser.encodePackage(created))
                        applied[packageName] = created
                    }
                }
                val expectedCount = entry.index.numPackages
                if (expectedCount != null && applied.size != expectedCount) {
                    throw IndexSyncException(
                        SyncFailureKind.VERIFICATION,
                        "Diff result has ${applied.size} packages, expected $expectedCount",
                    )
                }
                applied.size
            }

            val fetchedAt = now()
            val snapshot = StoreSnapshot(
                storeId = descriptor.id.value,
                fetchedAtEpochMillis = fetchedAt,
                etag = null,
                lastModified = null,
                indexVersion = entry.version,
                indexTimestamp = entry.timestamp,
                indexSha256 = entry.index.sha256,
                packageCount = writeResult.value,
                payloadPath = writeResult.payload.path,
                payloadSizeBytes = writeResult.payload.sizeBytes,
                signatureStatus = verification.status,
                sourceUrl = diffUrl,
                expectedFingerprint = verification.expectedFingerprint,
                signerFingerprint = verification.signerFingerprint,
            )
            val completed = job.copy(
                state = SyncState.SUCCEEDED,
                finishedAtEpochMillis = fetchedAt,
                bytesDownloaded = job.bytesDownloaded + counting.count,
                totalBytes = job.bytesDownloaded + diffFile.size,
                usedDiff = true,
                usedConditionalRequest = false,
            )
            persistCompletion(snapshot, completed)
            payloadStore.delete(previous.payloadPath)
            payloadStore.cleanup(activePayloadPaths())
            return IndexSyncResult(completed, snapshot, usedDiff = true)
        } finally {
            runCatching { response.body?.close() }
        }
    }

    private suspend fun syncFull(
        descriptor: StoreDescriptor,
        baseUrl: String,
        entry: FdroidEntry,
        previous: StoreSnapshot?,
        verification: VerificationResult,
        job: SyncJob,
        maxNewBytes: Long,
    ): IndexSyncResult {
        val indexUrl = joinRepoPath(baseUrl, entry.index.name)
        checkHttpPolicy(indexUrl, descriptor.allowInsecureHttp)?.let { message ->
            throw IndexSyncException(SyncFailureKind.HTTP_POLICY, message)
        }

        var response = httpFetcher.get(
            HttpRequest(
                url = indexUrl,
                ifNoneMatch = previous?.etag,
                ifModifiedSince = previous?.lastModified,
                allowInsecureHttp = descriptor.allowInsecureHttp,
            ),
        )
        try {
            if (response.status == 304) {
                val sameIndex = previous != null &&
                    previous.indexSha256 != null &&
                    previous.indexSha256 == entry.index.sha256
                if (sameIndex) {
                    val refreshed = checkNotNull(previous).copy(
                        fetchedAtEpochMillis = now(),
                        etag = response.etag ?: previous.etag,
                        lastModified = response.lastModified ?: previous.lastModified,
                        signatureStatus = verification.status,
                        expectedFingerprint = verification.expectedFingerprint,
                        signerFingerprint = verification.signerFingerprint,
                        indexVersion = entry.version,
                        indexTimestamp = entry.timestamp,
                        indexSha256 = entry.index.sha256,
                    )
                    val completed = job.copy(
                        state = SyncState.SUCCEEDED,
                        finishedAtEpochMillis = now(),
                        bytesDownloaded = job.bytesDownloaded,
                        totalBytes = job.bytesDownloaded,
                        usedConditionalRequest = true,
                    )
                    persistCompletion(refreshed, completed)
                    return IndexSyncResult(completed, refreshed)
                }

                // The validator is stale relative to the signed entry. Retry
                // unconditionally rather than activating a payload that does
                // not correspond to the signed index.
                runCatching { response.body?.close() }
                response = httpFetcher.get(
                    HttpRequest(
                        url = indexUrl,
                        allowInsecureHttp = descriptor.allowInsecureHttp,
                    ),
                )
            }

            if (!response.isSuccess || response.body == null) {
                throw IndexSyncException(
                    SyncFailureKind.HTTP_STATUS,
                    "Index request returned HTTP ${response.status}",
                )
            }

            val counting = CountingInputStream(response.body)
            val digest = DigestInputStream(counting, MessageDigest.getInstance("SHA-256"))
            val header = SnapshotPayloadHeader(
                storeId = descriptor.id.value,
                indexTimestamp = entry.timestamp,
                fetchedAtEpochMillis = now(),
            )
            val writeResult = try {
                payloadStore.writePayload(
                    storeId = descriptor.id.value,
                    version = entry.timestamp,
                    maxBytes = maxNewBytes,
                ) { sink ->
                    sink.writeLine(
                        json.encodeToString(SnapshotPayloadHeader.serializer(), header),
                    )
                    val parsed = parser.parseIndex(digest) { packageRecord ->
                        sink.writeLine(parser.encodePackage(packageRecord))
                    }
                    val expectedCount = entry.index.numPackages
                    if (expectedCount != null && parsed.packageCount != expectedCount) {
                        throw IndexSyncException(
                            SyncFailureKind.VERIFICATION,
                            "Index contains ${parsed.packageCount} packages, " +
                                "expected $expectedCount",
                        )
                    }
                    val actualHash = digest.messageDigest.toHex()
                    val integrity = verifier.verifyDigest(
                        DigestVerificationRequest(
                            storeId = descriptor.id.value,
                            expectedSha256 = entry.index.sha256,
                            actualSha256 = actualHash,
                            expectedSize = entry.index.size,
                            actualSize = counting.count,
                        ),
                    )
                    if (!integrity.activatable) {
                        throw IndexSyncException(
                            SyncFailureKind.VERIFICATION,
                            integrity.note.ifBlank {
                                "Index integrity check failed"
                            },
                        )
                    }
                    parsed
                }
            } finally {
                digest.close()
            }

            val fetchedAt = now()
            val snapshot = StoreSnapshot(
                storeId = descriptor.id.value,
                fetchedAtEpochMillis = fetchedAt,
                etag = response.etag,
                lastModified = response.lastModified,
                indexVersion = entry.version,
                indexTimestamp = entry.timestamp,
                indexSha256 = entry.index.sha256,
                packageCount = writeResult.value.packageCount,
                payloadPath = writeResult.payload.path,
                payloadSizeBytes = writeResult.payload.sizeBytes,
                signatureStatus = verification.status,
                sourceUrl = indexUrl,
                expectedFingerprint = verification.expectedFingerprint,
                signerFingerprint = verification.signerFingerprint,
            )
            val completed = job.copy(
                state = SyncState.SUCCEEDED,
                finishedAtEpochMillis = fetchedAt,
                bytesDownloaded = job.bytesDownloaded + counting.count,
                totalBytes = job.bytesDownloaded + entry.index.size,
            )
            persistCompletion(snapshot, completed)
            previous?.let { payloadStore.delete(it.payloadPath) }
            payloadStore.cleanup(activePayloadPaths())
            return IndexSyncResult(completed, snapshot)
        } finally {
            runCatching { response.body?.close() }
        }
    }

    private suspend fun downloadToFile(
        url: String,
        allowInsecureHttp: Boolean,
        target: File,
        maxBytes: Long,
    ): Long {
        val response = httpFetcher.get(
            HttpRequest(url = url, allowInsecureHttp = allowInsecureHttp),
        )
        if (!response.isSuccess || response.body == null) {
            response.body?.close()
            throw IndexSyncException(
                SyncFailureKind.HTTP_STATUS,
                "Entry request returned HTTP ${response.status}",
            )
        }
        var total = 0L
        try {
            response.body.use { input ->
                FileOutputStream(target).use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val read = input.read(buffer)
                        if (read < 0) break
                        total += read
                        if (total > maxBytes) {
                            throw IndexSyncException(
                                SyncFailureKind.VERIFICATION,
                                "Signed entry is unexpectedly large",
                            )
                        }
                        output.write(buffer, 0, read)
                    }
                }
            }
        } catch (throwable: Throwable) {
            target.delete()
            throw throwable
        }
        return total
    }

    private suspend fun fail(
        job: SyncJob,
        previous: StoreSnapshot?,
        kind: SyncFailureKind,
        message: String,
        state: SyncState = SyncState.FAILED,
    ): IndexSyncResult {
        val failed = job.copy(
            state = state,
            finishedAtEpochMillis = now(),
            error = SyncFailure(kind, message),
        )
        persistJob(failed)
        return IndexSyncResult(failed, previous)
    }

    private suspend fun persistJob(job: SyncJob) {
        updateState { current ->
            current.copy(jobs = current.jobs + (job.storeId to job))
        }
    }

    private suspend fun persistTrust(record: TrustRecord) {
        updateState { current ->
            current.copy(trustRecords = current.trustRecords + (record.storeId to record))
        }
    }

    private suspend fun persistCompletion(snapshot: StoreSnapshot, job: SyncJob) {
        updateState { current ->
            current.copy(
                snapshots = current.snapshots + (snapshot.storeId to snapshot),
                jobs = current.jobs + (job.storeId to job),
            )
        }
    }

    private suspend fun updateState(transform: (IndexState) -> IndexState) {
        val current = metadataStore.state.first()
        metadataStore.save(transform(current))
    }

    private suspend fun activePayloadPaths(): Set<String> =
        metadataStore.state.first().snapshots.values
            .mapTo(mutableSetOf(), StoreSnapshot::payloadPath)

    private fun checkHttpPolicy(url: String, allowInsecureHttp: Boolean): String? {
        val uri = runCatching { URI(url) }.getOrNull()
            ?: return "The index URL is not valid"
        val scheme = uri.scheme?.lowercase()
        return when {
            scheme == "https" && !uri.host.isNullOrBlank() -> null
            scheme == "http" && allowInsecureHttp && !uri.host.isNullOrBlank() -> null
            scheme == "http" -> "Plain HTTP needs explicit approval before syncing this store"
            else -> "The index URL must use HTTPS"
        }
    }

    private fun joinRepoPath(baseUrl: String, path: String): String =
        baseUrl.trimEnd('/') + "/" + path.trimStart('/')

    private fun safeName(value: String): String =
        value.replace(Regex("[^A-Za-z0-9._-]"), "_")

    private class CountingInputStream(input: InputStream) : FilterInputStream(input) {
        var count: Long = 0
            private set

        override fun read(): Int {
            val value = super.read()
            if (value >= 0) count++
            return value
        }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            val read = super.read(buffer, offset, length)
            if (read > 0) count += read
            return read
        }
    }

    private fun MessageDigest.toHex(): String =
        digest().joinToString(separator = "") { byte -> "%02x".format(byte) }

    companion object {
        const val DEFAULT_STORAGE_BUDGET_BYTES: Long = 256L * 1024L * 1024L
        const val MAX_ENTRY_JAR_BYTES: Long = 8L * 1024L * 1024L
    }
}
