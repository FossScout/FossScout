package com.fossscout.core.index

import com.fossscout.core.model.StoreId
import com.fossscout.core.store.AdapterType
import com.fossscout.core.store.StoreArtifactRequest
import com.fossscout.core.store.StoreAdapterResult
import com.fossscout.core.store.StoreDescriptor
import com.fossscout.core.store.StoreListingRequest
import com.fossscout.core.store.StoreSearchRequest
import com.fossscout.core.store.StoreVersionsRequest
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IndexSyncRepositoryTest {
    private val signer = TestSigningKey()
    private val parser = FdroidV2Parser()
    private val tempDirs = mutableListOf<File>()

    @After
    fun tearDown() {
        tempDirs.forEach { dir -> dir.deleteRecursively() }
    }

    @Test
    fun `signed index is verified stored and exposed as trust evidence`() = runTest {
        val indexJson = TestIndexFixtures.indexJson()
        val signed = signedEntry(timestamp = 1_000, indexJson = indexJson)
        val harness = harness()

        harness.respond { request ->
            when {
                request.url.endsWith("/entry.jar") -> fakeResponse(request.url, signed.jar)
                request.url.endsWith("/index-v2.json") -> fakeResponse(
                    url = request.url,
                    bytes = indexJson.toByteArray(),
                    etag = "\"index-v1\"",
                    lastModified = "Thu, 01 Jan 1970 00:02:03 GMT",
                )

                else -> error("Unexpected request ${request.url}")
            }
        }

        val result = harness.repository.sync(harness.descriptor)

        assertEquals(SyncState.SUCCEEDED, result.job.state)
        assertFalse(result.usedDiff)
        val snapshot = requireNotNull(result.snapshot)
        assertEquals(SignatureStatus.VERIFIED, snapshot.signatureStatus)
        assertEquals(2, snapshot.packageCount)
        assertEquals(TestIndexFixtures.sha256(indexJson.toByteArray()), snapshot.indexSha256)
        assertEquals("\"index-v1\"", snapshot.etag)
        assertEquals(signer.fingerprint, snapshot.signerFingerprint)
        assertTrue(harness.payloads.exists(snapshot.payloadPath))

        val state = harness.metadata.mutableState.value
        assertEquals(snapshot, state.snapshots[harness.descriptor.id.value])
        val trust = requireNotNull(state.trustRecords[harness.descriptor.id.value])
        assertEquals(SignatureStatus.VERIFIED, trust.signatureStatus)
        assertEquals(signer.fingerprint, trust.expectedFingerprint)
        assertEquals(signer.fingerprint, trust.signerFingerprint)
        assertEquals(1_000L, trust.indexTimestamp)
        assertTrue(trust.sourceUrl.endsWith("/entry.jar"))
        assertEquals(listOf(trust), state.trustAudit)

        val packages = decodedPackages(harness.payloads, snapshot)
        assertEquals(
            listOf(TestIndexFixtures.PACKAGE_A, TestIndexFixtures.PACKAGE_B),
            packages.map(StorePackage::packageName),
        )
        val alpha = packages.first()
        assertEquals("Alpha", alpha.localizedName())
        assertEquals("A longer alpha description", alpha.description["en-US"])
        assertEquals("GPL-3.0-or-later", alpha.license)
        assertEquals("https://example.org/alpha", alpha.sourceCode)
        assertEquals(listOf("Tools"), alpha.categories)
        assertTrue(alpha.antiFeatures.containsKey("NonFreeNet"))
        val version = requireNotNull(alpha.latestVersion())
        assertEquals(1L, version.versionCode)
        assertEquals("1.0", version.versionName)
        assertEquals(24, version.minSdk)
        assertEquals(35, version.targetSdk)
        assertEquals(listOf("android.permission.INTERNET"), version.permissions)
        assertEquals(1_234L, version.sizeBytes)
    }

    @Test
    fun `tampered entry never replaces the active snapshot`() = runTest {
        val harness = harness()
        harness.serveFullIndex(1_000, TestIndexFixtures.indexJson())
        val first = harness.repository.sync(harness.descriptor)
        val previous = requireNotNull(first.snapshot)

        val replacementIndex = TestIndexFixtures.indexJson(alphaVersionName = "9.0", alphaVersionCode = 9)
        val signed = signedEntry(timestamp = 2_000, indexJson = replacementIndex)
        val tampered = tamperJar(
            signed.jar,
            TestIndexFixtures.entryJson(
                timestamp = 2_000,
                indexJson = replacementIndex,
            ).replace("\"timestamp\": 2000", "\"timestamp\": 2001"),
        )
        harness.respond { request ->
            if (request.url.endsWith("/entry.jar")) {
                fakeResponse(request.url, tampered)
            } else {
                error("Tampered entry must not trigger an index download")
            }
        }

        val result = harness.repository.sync(harness.descriptor)

        assertEquals(SyncState.FAILED, result.job.state)
        assertEquals(SyncFailureKind.VERIFICATION, result.job.error?.kind)
        assertEquals(previous, result.snapshot)
        assertEquals(previous, harness.metadata.mutableState.value.snapshots[harness.descriptor.id.value])
        assertTrue(harness.payloads.exists(previous.payloadPath))
        assertEquals(
            SignatureStatus.INVALID,
            harness.metadata.mutableState.value.trustRecords[harness.descriptor.id.value]
                ?.signatureStatus,
        )
        val audit = harness.metadata.mutableState.value.trustAudit
        assertEquals(2, audit.size)
        assertEquals(SignatureStatus.INVALID, audit.last().signatureStatus)
        assertEquals(VerificationFailure.TAMPERED, audit.last().failure)
    }

    @Test
    fun `corrupt index bytes never replace the active snapshot`() = runTest {
        val harness = harness()
        harness.serveFullIndex(1_000, TestIndexFixtures.indexJson())
        val first = harness.repository.sync(harness.descriptor)
        val previous = requireNotNull(first.snapshot)

        val replacementIndex = TestIndexFixtures.indexJson(
            includeBeta = false,
            alphaVersionName = "9.0",
            alphaVersionCode = 9,
        )
        val signed = signedEntry(
            timestamp = 2_000,
            indexJson = replacementIndex,
            numPackages = 1,
        )
        harness.respond { request ->
            when {
                request.url.endsWith("/entry.jar") -> fakeResponse(request.url, signed.jar)
                request.url.endsWith("/index-v2.json") -> fakeResponse(
                    request.url,
                    replacementIndex.replace("9.0", "9.9").toByteArray(),
                )

                else -> error("Unexpected request ${request.url}")
            }
        }

        val result = harness.repository.sync(harness.descriptor)

        assertEquals(SyncState.FAILED, result.job.state)
        assertEquals(SyncFailureKind.VERIFICATION, result.job.error?.kind)
        assertEquals(previous, result.snapshot)
        assertEquals(previous, harness.metadata.mutableState.value.snapshots[harness.descriptor.id.value])
        val trust = requireNotNull(
            harness.metadata.mutableState.value.trustRecords[harness.descriptor.id.value],
        )
        assertEquals(SignatureStatus.VERIFIED, trust.signatureStatus)
        assertNull(trust.failure)
        assertEquals(2, decodedPackages(harness.payloads, previous).size)
    }

    @Test
    fun `unsigned entry is rejected and leaves a previous snapshot intact`() = runTest {
        val harness = harness()
        harness.serveFullIndex(1_000, TestIndexFixtures.indexJson())
        val previous = requireNotNull(harness.repository.sync(harness.descriptor).snapshot)

        val indexJson = TestIndexFixtures.indexJson(alphaVersionName = "2.0", alphaVersionCode = 2)
        val entryJson = TestIndexFixtures.entryJson(timestamp = 2_000, indexJson = indexJson)
        harness.respond { request ->
            if (request.url.endsWith("/entry.jar")) {
                fakeResponse(request.url, signer.unsigned(entryJson))
            } else {
                error("Unsigned entry must not trigger an index download")
            }
        }

        val result = harness.repository.sync(harness.descriptor)

        assertEquals(SyncState.FAILED, result.job.state)
        assertEquals(SyncFailureKind.VERIFICATION, result.job.error?.kind)
        assertEquals(previous, harness.metadata.mutableState.value.snapshots[harness.descriptor.id.value])
        val trust = requireNotNull(
            harness.metadata.mutableState.value.trustRecords[harness.descriptor.id.value],
        )
        assertEquals(SignatureStatus.UNSIGNED, trust.signatureStatus)
        assertEquals(VerificationFailure.NO_SIGNATURE, trust.failure)
    }

    @Test
    fun `signed index without a pinned fingerprint is activatable but unverified`() = runTest {
        val indexJson = TestIndexFixtures.indexJson()
        val signed = signedEntry(timestamp = 1_000, indexJson = indexJson)
        val harness = harness(descriptor = descriptor(fingerprint = null))
        harness.respond { request ->
            when {
                request.url.endsWith("/entry.jar") -> fakeResponse(request.url, signed.jar)
                request.url.endsWith("/index-v2.json") -> fakeResponse(
                    request.url,
                    indexJson.toByteArray(),
                )

                else -> error("Unexpected request ${request.url}")
            }
        }

        val result = harness.repository.sync(harness.descriptor)

        assertEquals(SyncState.SUCCEEDED, result.job.state)
        val snapshot = requireNotNull(result.snapshot)
        assertEquals(SignatureStatus.UNVERIFIED, snapshot.signatureStatus)
        assertNull(snapshot.expectedFingerprint)
        assertEquals(signer.fingerprint, snapshot.signerFingerprint)
        val trust = requireNotNull(
            harness.metadata.mutableState.value.trustRecords[harness.descriptor.id.value],
        )
        assertEquals(VerificationFailure.NO_PINNED_FINGERPRINT, trust.failure)
    }

    @Test
    fun `changed repository fingerprint blocks sync before any request`() = runTest {
        val previousPin = "a".repeat(64)
        val previousSnapshot = StoreSnapshot(
            storeId = "test-store",
            fetchedAtEpochMillis = 500,
            indexTimestamp = 1_000,
            indexSha256 = "b".repeat(64),
            packageCount = 1,
            payloadPath = "old.ndjson",
            payloadSizeBytes = 10,
            signatureStatus = SignatureStatus.VERIFIED,
            sourceUrl = "https://repo.example/repo/index-v2.json",
            expectedFingerprint = previousPin,
            signerFingerprint = previousPin,
        )
        val harness = harness(
            initial = IndexState(snapshots = mapOf("test-store" to previousSnapshot)),
            descriptor = descriptor(fingerprint = "c".repeat(64)),
        )
        harness.respond { error("Fingerprint change must block before any HTTP request") }

        val result = harness.repository.sync(harness.descriptor)

        assertEquals(SyncState.BLOCKED, result.job.state)
        assertEquals(SyncFailureKind.FINGERPRINT_CHANGED, result.job.error?.kind)
        assertEquals(previousSnapshot, result.snapshot)
        assertEquals(previousSnapshot, harness.metadata.mutableState.value.snapshots["test-store"])
        assertTrue(harness.http.requests.isEmpty())
    }

    @Test
    fun `plain HTTP is blocked without opt-in and allowed with it`() = runTest {
        val indexJson = TestIndexFixtures.indexJson()
        val signed = signedEntry(timestamp = 1_000, indexJson = indexJson)
        val insecure = harness(
            descriptor = descriptor(
                indexUrl = "http://repo.example/repo",
                fingerprint = signer.fingerprint,
            ),
        )
        insecure.respond { error("Plain HTTP must be blocked before any request") }

        val blocked = insecure.repository.sync(insecure.descriptor)

        assertEquals(SyncState.BLOCKED, blocked.job.state)
        assertEquals(SyncFailureKind.HTTP_POLICY, blocked.job.error?.kind)
        assertTrue(insecure.http.requests.isEmpty())

        val optedIn = harness(
            descriptor = descriptor(
                indexUrl = "http://repo.example/repo",
                fingerprint = signer.fingerprint,
                allowInsecureHttp = true,
            ),
        )
        optedIn.respond { request ->
            when {
                request.url.endsWith("/entry.jar") -> fakeResponse(request.url, signed.jar)
                request.url.endsWith("/index-v2.json") -> fakeResponse(
                    request.url,
                    indexJson.toByteArray(),
                )

                else -> error("Unexpected request ${request.url}")
            }
        }

        val allowed = optedIn.repository.sync(optedIn.descriptor)

        assertEquals(SyncState.SUCCEEDED, allowed.job.state)
        assertTrue(optedIn.http.requests.all { it.allowInsecureHttp })
    }

    @Test
    fun `second sync uses a diff and transfers less than a full index`() = runTest {
        val harness = harness()
        harness.serveFullIndex(1_000, TestIndexFixtures.indexJson())
        val first = harness.repository.sync(harness.descriptor)

        val targetIndex = TestIndexFixtures.indexJson(
            includeBeta = false,
            alphaVersionName = "3.0",
            alphaVersionCode = 3,
            alphaHash = TestIndexFixtures.VERSION_HASH_A2,
        )
        val diffJson = TestIndexFixtures.diffJson()
        val signed = signedEntry(
            timestamp = 2_000,
            indexJson = targetIndex,
            diffs = mapOf(1_000L to diffJson),
            numPackages = 1,
        )
        val diffBytes = diffJson.toByteArray()
        assertTrue(diffBytes.size < targetIndex.toByteArray().size)
        harness.http.requests.clear()
        harness.respond { request ->
            when {
                request.url.endsWith("/entry.jar") -> fakeResponse(request.url, signed.jar)
                request.url.endsWith("/diff/1000.json") -> fakeResponse(request.url, diffBytes)
                else -> error("Diff sync must not request the full index: ${request.url}")
            }
        }

        val second = harness.repository.sync(harness.descriptor)

        assertEquals(SyncState.SUCCEEDED, second.job.state)
        assertTrue(second.usedDiff)
        assertTrue(requireNotNull(second.job.usedDiff))
        assertFalse(second.job.usedConditionalRequest)
        assertTrue(second.job.bytesDownloaded < first.job.bytesDownloaded)
        assertTrue(harness.http.requests.any { it.url.endsWith("/diff/1000.json") })
        assertFalse(harness.http.requests.any { it.url.endsWith("/index-v2.json") })

        val snapshot = requireNotNull(second.snapshot)
        assertEquals(1, snapshot.packageCount)
        assertEquals(2_000L, snapshot.indexTimestamp)
        val packages = decodedPackages(harness.payloads, snapshot)
        assertEquals(listOf(TestIndexFixtures.PACKAGE_A), packages.map(StorePackage::packageName))
        val alpha = packages.single()
        assertEquals("Updated alpha", alpha.summary["en-US"])
        assertEquals(listOf(3L), alpha.versions.map(StorePackageVersion::versionCode))
        assertFalse(harness.payloads.exists(requireNotNull(first.snapshot).payloadPath))
    }

    @Test
    fun `unchanged signed timestamp reuses the payload without an index request`() = runTest {
        val harness = harness()
        harness.serveFullIndex(1_000, TestIndexFixtures.indexJson())
        val first = harness.repository.sync(harness.descriptor)
        val previous = requireNotNull(first.snapshot)

        val signed = signedEntry(timestamp = 1_000, indexJson = TestIndexFixtures.indexJson())
        harness.http.requests.clear()
        harness.respond { request ->
            if (request.url.endsWith("/entry.jar")) {
                fakeResponse(request.url, signed.jar)
            } else {
                error("An unchanged timestamp must not download the index")
            }
        }

        val second = harness.repository.sync(harness.descriptor)

        assertEquals(SyncState.SUCCEEDED, second.job.state)
        assertEquals(listOf("https://repo.example/repo/entry.jar"), harness.http.requests.map { it.url })
        assertFalse(requireNotNull(second.job.usedConditionalRequest))
        assertEquals(previous.payloadPath, requireNotNull(second.snapshot).payloadPath)
        assertTrue(harness.payloads.exists(previous.payloadPath))
    }

    @Test
    fun `matching ETag reuses the active payload without downloading it`() = runTest {
        val harness = harness()
        harness.serveFullIndex(1_000, TestIndexFixtures.indexJson())
        val first = harness.repository.sync(harness.descriptor)
        val previous = requireNotNull(first.snapshot)
        assertEquals("\"etag-1000\"", previous.etag)
        assertEquals(
            "\"etag-1000\"",
            harness.metadata.mutableState.value.snapshots[harness.descriptor.id.value]?.etag,
        )
        harness.clock += 60_000

        val signed = signedEntry(
            timestamp = 2_000,
            indexJson = TestIndexFixtures.indexJson(),
        )
        harness.respond { request ->
            when {
                request.url.endsWith("/entry.jar") -> fakeResponse(request.url, signed.jar)
                request.url.endsWith("/index-v2.json") -> {
                    assertEquals("\"etag-1000\"", request.ifNoneMatch)
                    fakeResponse(request.url, ByteArray(0), status = 304)
                }

                else -> error("Unexpected request ${request.url}")
            }
        }

        val second = harness.repository.sync(harness.descriptor)

        assertEquals(SyncState.SUCCEEDED, second.job.state)
        assertTrue(requireNotNull(second.job.usedConditionalRequest))
        val snapshot = requireNotNull(second.snapshot)
        assertEquals(previous.payloadPath, snapshot.payloadPath)
        assertEquals(2_000L, snapshot.indexTimestamp)
        assertEquals(previous.indexSha256, snapshot.indexSha256)
        assertEquals(1_060_000L, snapshot.fetchedAtEpochMillis)
        assertEquals(SnapshotFreshness.FRESH, harness.repository.freshness(snapshot))
        assertTrue(harness.payloads.exists(previous.payloadPath))
    }

    @Test
    fun `stale validator triggers an unconditional retry`() = runTest {
        val harness = harness()
        harness.serveFullIndex(1_000, TestIndexFixtures.indexJson())
        val first = harness.repository.sync(harness.descriptor)
        val previous = requireNotNull(first.snapshot)

        val replacement = TestIndexFixtures.indexJson(
            includeBeta = false,
            alphaVersionName = "4.0",
            alphaVersionCode = 4,
            alphaHash = TestIndexFixtures.VERSION_HASH_A2,
        )
        val signed = signedEntry(timestamp = 2_000, indexJson = replacement, numPackages = 1)
        var indexRequests = 0
        harness.respond { request ->
            when {
                request.url.endsWith("/entry.jar") -> fakeResponse(request.url, signed.jar)
                request.url.endsWith("/index-v2.json") -> {
                    indexRequests++
                    if (indexRequests == 1) {
                        fakeResponse(request.url, ByteArray(0), status = 304)
                    } else {
                        fakeResponse(request.url, replacement.toByteArray())
                    }
                }

                else -> error("Unexpected request ${request.url}")
            }
        }

        val second = harness.repository.sync(harness.descriptor)

        assertEquals(SyncState.SUCCEEDED, second.job.state)
        assertEquals(2, indexRequests)
        assertFalse(requireNotNull(second.job.usedConditionalRequest))
        val snapshot = requireNotNull(second.snapshot)
        assertFalse(previous.payloadPath == snapshot.payloadPath)
        assertEquals(1, snapshot.packageCount)
        assertEquals(4L, decodedPackages(harness.payloads, snapshot).single().versions.single().versionCode)
    }

    @Test
    fun `cancellation keeps the previous snapshot and records a cancelled job`() = runTest {
        val harness = harness()
        harness.serveFullIndex(1_000, TestIndexFixtures.indexJson())
        val previous = requireNotNull(harness.repository.sync(harness.descriptor).snapshot)

        val started = CompletableDeferred<Unit>()
        harness.respond { request ->
            if (request.url.endsWith("/entry.jar")) {
                started.complete(Unit)
                awaitCancellation()
            } else {
                error("Cancellation must happen before the index download")
            }
        }

        val job = launch {
            try {
                harness.repository.sync(harness.descriptor)
            } catch (_: CancellationException) {
                // Expected: the repository rethrows cancellation.
            }
        }
        started.await()
        job.cancel()
        job.join()

        val state = harness.metadata.mutableState.value
        assertEquals(previous, state.snapshots[harness.descriptor.id.value])
        assertTrue(harness.payloads.exists(previous.payloadPath))
        assertEquals(SyncState.CANCELLED, state.jobs[harness.descriptor.id.value]?.state)
        assertEquals(SyncFailureKind.CANCELLED, state.jobs[harness.descriptor.id.value]?.error?.kind)
    }

    @Test
    fun `storage budget blocks an oversized payload and leaves no snapshot`() = runTest {
        val indexJson = TestIndexFixtures.indexJson()
        val signed = signedEntry(timestamp = 1_000, indexJson = indexJson)
        val harness = harness(budgetBytes = 8)
        harness.respond { request ->
            when {
                request.url.endsWith("/entry.jar") -> fakeResponse(request.url, signed.jar)
                request.url.endsWith("/index-v2.json") -> fakeResponse(
                    request.url,
                    indexJson.toByteArray(),
                )

                else -> error("Unexpected request ${request.url}")
            }
        }

        val result = harness.repository.sync(harness.descriptor)

        assertEquals(SyncState.FAILED, result.job.state)
        assertEquals(SyncFailureKind.STORAGE_LIMIT, result.job.error?.kind)
        assertNull(result.snapshot)
        assertTrue(harness.payloads.payloads.isEmpty())
        assertTrue(harness.metadata.mutableState.value.snapshots.isEmpty())
    }

    @Test
    fun `cleanup removes unreferenced payloads and reports freed bytes`() = runTest {
        val harness = harness()
        harness.serveFullIndex(1_000, TestIndexFixtures.indexJson())
        val snapshot = requireNotNull(harness.repository.sync(harness.descriptor).snapshot)
        harness.payloads.payloads["orphan.ndjson"] = listOf("orphan-line")

        val cleanup = harness.repository.cleanupStorage()

        assertTrue(cleanup.freedBytes > 0)
        assertNull(harness.payloads.payloads["orphan.ndjson"])
        assertTrue(harness.payloads.exists(snapshot.payloadPath))
        assertEquals(harness.payloads.usage, cleanup.status.usageBytes)
        assertTrue(cleanup.status.usageBytes <= cleanup.status.budgetBytes)
    }

    @Test
    fun `synced user added repository is usable through the adapter`() = runTest {
        val indexJson = TestIndexFixtures.indexJson()
        val signed = signedEntry(timestamp = 1_000, indexJson = indexJson)
        val harness = harness()
        harness.respond { request ->
            when {
                request.url.endsWith("/entry.jar") -> fakeResponse(request.url, signed.jar)
                request.url.endsWith("/index-v2.json") -> fakeResponse(
                    request.url,
                    indexJson.toByteArray(),
                )

                else -> error("Unexpected request ${request.url}")
            }
        }
        harness.repository.sync(harness.descriptor)
        val adapter = FdroidV2StoreAdapter(
            descriptor = harness.descriptor,
            metadataStore = harness.metadata,
            payloadStore = harness.payloads,
            syncRepository = harness.repository,
            parser = parser,
        )

        val search = adapter.search(StoreSearchRequest(query = "Alpha", limit = 10))
        val searchValue = (search as StoreAdapterResult.Success).value
        assertEquals(listOf(TestIndexFixtures.PACKAGE_A), searchValue.map { it.packageName })

        val page = (
            adapter.list(StoreListingRequest(offset = 0, limit = 1))
                as StoreAdapterResult.Success
            ).value
        assertEquals(1, page.items.size)
        assertEquals(TestIndexFixtures.PACKAGE_A, page.items.single().packageName)
        assertEquals(1, page.nextOffset)

        val versions = (
            adapter.versions(StoreVersionsRequest(TestIndexFixtures.PACKAGE_A))
                as StoreAdapterResult.Success
            ).value
        assertEquals(1, versions.size)
        assertEquals(1L, versions.single().versionCode)
        assertEquals(
            "https://repo.example/repo/alpha-1.apk",
            versions.single().downloadUrl,
        )

        val artifact = (
            adapter.resolveArtifact(
                StoreArtifactRequest(TestIndexFixtures.PACKAGE_A, versionCode = 1),
            ) as StoreAdapterResult.Success
            ).value
        assertEquals(1_234L, artifact.sizeBytes)
        assertEquals(TestIndexFixtures.VERSION_HASH_A, artifact.sha256)
        assertEquals("https://repo.example/repo/alpha-1.apk", artifact.downloadUrl)
    }

    @Test
    fun `verification audit is bounded to the newest entries`() = runTest {
        val oldAudit = (1..MAX_TRUST_AUDIT_ENTRIES).map { index ->
            TrustRecord(
                storeId = "test-store",
                checkedAtEpochMillis = index.toLong(),
                signatureStatus = SignatureStatus.VERIFIED,
                sourceUrl = "https://old.example/entry.jar",
            )
        }
        val harness = harness(initial = IndexState(trustAudit = oldAudit))
        harness.serveFullIndex(1_000, TestIndexFixtures.indexJson())

        val result = harness.repository.sync(harness.descriptor)

        assertEquals(SyncState.SUCCEEDED, result.job.state)
        val audit = harness.metadata.mutableState.value.trustAudit
        assertEquals(MAX_TRUST_AUDIT_ENTRIES, audit.size)
        assertEquals(signer.fingerprint, audit.last().signerFingerprint)
        assertFalse(audit.any { it.checkedAtEpochMillis == 1L })
    }

    @Test
    fun `rotation approval clears active trust and payload but keeps audit history`() = runTest {
        val harness = harness()
        harness.serveFullIndex(1_000, TestIndexFixtures.indexJson())
        val snapshot = requireNotNull(harness.repository.sync(harness.descriptor).snapshot)
        val auditBefore = harness.metadata.mutableState.value.trustAudit

        harness.repository.clearStoreTrustForRotation(harness.descriptor.id.value)

        val state = harness.metadata.mutableState.value
        assertNull(state.snapshots[harness.descriptor.id.value])
        assertNull(state.trustRecords[harness.descriptor.id.value])
        assertNull(state.jobs[harness.descriptor.id.value])
        assertEquals(auditBefore, state.trustAudit)
        assertFalse(harness.payloads.exists(snapshot.payloadPath))
    }

    @Test
    fun `sync resumes with a new pin after rotation approval`() = runTest {
        val previousPin = "a".repeat(64)
        val previousSnapshot = StoreSnapshot(
            storeId = "test-store",
            fetchedAtEpochMillis = 500,
            indexTimestamp = 1_000,
            indexSha256 = "b".repeat(64),
            packageCount = 1,
            payloadPath = "old.ndjson",
            payloadSizeBytes = 10,
            signatureStatus = SignatureStatus.VERIFIED,
            sourceUrl = "https://repo.example/repo/index-v2.json",
            expectedFingerprint = previousPin,
            signerFingerprint = previousPin,
        )
        val harness = harness(
            initial = IndexState(snapshots = mapOf("test-store" to previousSnapshot)),
        )
        harness.serveFullIndex(1_000, TestIndexFixtures.indexJson())

        val blocked = harness.repository.sync(harness.descriptor)
        assertEquals(SyncState.BLOCKED, blocked.job.state)
        assertEquals(SyncFailureKind.FINGERPRINT_CHANGED, blocked.job.error?.kind)

        harness.repository.clearStoreTrustForRotation(harness.descriptor.id.value)
        val approved = harness.repository.sync(harness.descriptor)

        assertEquals(SyncState.SUCCEEDED, approved.job.state)
        assertEquals(signer.fingerprint, approved.snapshot?.expectedFingerprint)
    }

    private fun signedEntry(
        timestamp: Long,
        indexJson: String,
        diffs: Map<Long, String> = emptyMap(),
        numPackages: Int = 2,
    ): SignedEntry = signer.sign(
        TestIndexFixtures.entryJson(
            timestamp = timestamp,
            indexJson = indexJson,
            diffs = diffs,
            numPackages = numPackages,
        ),
    )

    private fun harness(
        initial: IndexState = IndexState(),
        descriptor: StoreDescriptor = descriptor(),
        budgetBytes: Long = 4L * 1024L * 1024L,
    ): Harness {
        val tempDir = Files.createTempDirectory("fossscout-index-sync").toFile()
        tempDirs += tempDir
        return Harness(
            descriptor = descriptor,
            metadata = FakeMetadataStore(initial),
            payloads = InMemorySnapshotPayloadStore(),
            http = FakeHttpFetcher { request -> harnessHandler(request) },
            tempDir = tempDir,
            budgetBytes = budgetBytes,
        )
    }

    // Replaced by each harness; the fetcher delegates through this indirection
    // so a test can change the upstream response between syncs.
    private var harnessHandler: suspend (HttpRequest) -> HttpResponse = {
        error("No response configured for ${it.url}")
    }

    private fun Harness.respond(handler: suspend (HttpRequest) -> HttpResponse) {
        harnessHandler = handler
    }

    private fun Harness.serveFullIndex(timestamp: Long, indexJson: String, etag: String = "\"etag-$timestamp\"") {
        val signed = signedEntry(timestamp = timestamp, indexJson = indexJson)
        respond { request ->
            when {
                request.url.endsWith("/entry.jar") -> fakeResponse(request.url, signed.jar)
                request.url.endsWith("/index-v2.json") -> fakeResponse(
                    url = request.url,
                    bytes = indexJson.toByteArray(),
                    etag = etag,
                )

                else -> error("Unexpected request ${request.url}")
            }
        }
    }

    private fun descriptor(
        indexUrl: String = "https://repo.example/repo",
        fingerprint: String? = signer.fingerprint,
        allowInsecureHttp: Boolean = false,
    ): StoreDescriptor = StoreDescriptor(
        id = StoreId("test-store"),
        name = "Test store",
        homepage = "https://repo.example",
        adapterType = AdapterType.FDROID_V2,
        indexUrl = indexUrl,
        fingerprint = fingerprint,
        allowInsecureHttp = allowInsecureHttp,
    )

    private fun decodedPackages(
        payloads: InMemorySnapshotPayloadStore,
        snapshot: StoreSnapshot,
    ): List<StorePackage> = requireNotNull(payloads.payloads[snapshot.payloadPath])
        .drop(1)
        .filter(String::isNotBlank)
        .map(parser::decodePackage)

    private class Harness(
        val descriptor: StoreDescriptor,
        val metadata: FakeMetadataStore,
        val payloads: InMemorySnapshotPayloadStore,
        val http: FakeHttpFetcher,
        tempDir: File,
        budgetBytes: Long,
    ) {
        var clock: Long = 1_000_000

        val repository = IndexSyncRepository(
            metadataStore = metadata,
            payloadStore = payloads,
            httpFetcher = http,
            tempDir = tempDir,
            parser = FdroidV2Parser(),
            storageBudgetBytes = { budgetBytes },
            now = { clock },
        )
    }
}
