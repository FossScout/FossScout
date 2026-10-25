package com.fossscout.core.data

import com.fossscout.core.index.INDEX_STATE_SCHEMA_VERSION
import com.fossscout.core.index.IndexState
import com.fossscout.core.index.MAX_TRUST_AUDIT_ENTRIES
import com.fossscout.core.index.SignatureStatus
import com.fossscout.core.index.StoreSnapshot
import com.fossscout.core.index.SyncFailure
import com.fossscout.core.index.SyncFailureKind
import com.fossscout.core.index.SyncJob
import com.fossscout.core.index.SyncState
import com.fossscout.core.index.TrustRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class IndexStateCodecTest {
    private val codec = IndexStateCodec()

    @Test
    fun `round trip preserves snapshots trust records and jobs`() {
        val state = IndexState(
            snapshots = mapOf(SNAPSHOT.storeId to SNAPSHOT),
            trustRecords = mapOf(TRUST.storeId to TRUST),
            jobs = mapOf(JOB.storeId to JOB),
            trustAudit = listOf(TRUST),
        )

        val decoded = codec.decode(codec.encode(state))

        assertEquals(INDEX_STATE_SCHEMA_VERSION, decoded.schemaVersion)
        assertEquals(state, decoded)
    }

    @Test
    fun `future schema version is rejected`() {
        val failure = assertThrows(IndexStateFormatException::class.java) {
            codec.decode("""{"schemaVersion":99,"snapshots":{}}""")
        }

        assertTrue(failure.message.orEmpty().contains("99"))
    }

    @Test
    fun `malformed metadata is rejected`() {
        assertThrows(IndexStateFormatException::class.java) {
            codec.decode("""{"schemaVersion":1,"snapshots":{""")
        }
    }

    @Test
    fun `snapshot key must match its store id`() {
        val encoded = """
            {
              "schemaVersion": 1,
              "snapshots": {
                "wrong-key": {
                  "storeId": "store",
                  "schemaVersion": 1,
                  "fetchedAtEpochMillis": 1000,
                  "packageCount": 1,
                  "payloadPath": "payload.ndjson",
                  "payloadSizeBytes": 10,
                  "signatureStatus": "VERIFIED",
                  "sourceUrl": "https://repo.example/index-v2.json"
                }
              }
            }
        """.trimIndent()

        val failure = assertThrows(IndexStateFormatException::class.java) {
            codec.decode(encoded)
        }

        assertTrue(failure.message.orEmpty().contains("wrong-key"))
    }

    @Test
    fun `unsigned snapshot cannot be decoded into active state`() {
        val encoded = """
            {
              "schemaVersion": 1,
              "snapshots": {
                "store": {
                  "storeId": "store",
                  "schemaVersion": 1,
                  "fetchedAtEpochMillis": 1000,
                  "packageCount": 1,
                  "payloadPath": "payload.ndjson",
                  "payloadSizeBytes": 10,
                  "signatureStatus": "UNSIGNED",
                  "sourceUrl": "https://repo.example/index-v2.json"
                }
              }
            }
        """.trimIndent()

        assertThrows(IndexStateFormatException::class.java) {
            codec.decode(encoded)
        }
    }

    @Test
    fun `encoding a mismatched snapshot key is rejected`() {
        val state = IndexState(snapshots = mapOf("wrong-key" to SNAPSHOT))

        assertThrows(IndexStateFormatException::class.java) {
            codec.encode(state)
        }
    }

    @Test
    fun `state without an audit trail decodes with an empty history`() {
        val decoded = codec.decode("""{"schemaVersion":1,"snapshots":{}}""")

        assertTrue(decoded.trustAudit.isEmpty())
    }

    @Test
    fun `oversized audit trail is rejected`() {
        val audit = (1..MAX_TRUST_AUDIT_ENTRIES + 1).map { index ->
            TRUST.copy(checkedAtEpochMillis = index.toLong())
        }
        val state = IndexState(trustAudit = audit)

        assertThrows(IndexStateFormatException::class.java) {
            codec.encode(state)
        }
    }

    private companion object {
        val SNAPSHOT = StoreSnapshot(
            storeId = "store",
            fetchedAtEpochMillis = 1_000,
            etag = "\"etag\"",
            lastModified = "Thu, 01 Jan 1970 00:00:01 GMT",
            indexVersion = 30_000,
            indexTimestamp = 900,
            indexSha256 = "a".repeat(64),
            packageCount = 2,
            payloadPath = "store-900.ndjson",
            payloadSizeBytes = 1_024,
            signatureStatus = SignatureStatus.VERIFIED,
            sourceUrl = "https://repo.example/repo/index-v2.json",
            expectedFingerprint = "b".repeat(64),
            signerFingerprint = "b".repeat(64),
        )

        val TRUST = TrustRecord(
            storeId = "store",
            checkedAtEpochMillis = 1_000,
            signatureStatus = SignatureStatus.VERIFIED,
            expectedFingerprint = "b".repeat(64),
            signerFingerprint = "b".repeat(64),
            sourceUrl = "https://repo.example/repo/entry.jar",
            indexVersion = 30_000,
            indexTimestamp = 900,
        )

        val JOB = SyncJob(
            storeId = "store",
            state = SyncState.FAILED,
            startedAtEpochMillis = 1_000,
            finishedAtEpochMillis = 2_000,
            bytesDownloaded = 512,
            totalBytes = 1_024,
            usedDiff = true,
            error = SyncFailure(SyncFailureKind.VERIFICATION, "bad signature"),
        )
    }
}
