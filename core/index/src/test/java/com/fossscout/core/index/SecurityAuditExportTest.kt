package com.fossscout.core.index

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class SecurityAuditExportTest {
    private val builder = SecurityAuditExportBuilder()

    @Test
    fun `export contains timestamps source fingerprints and failure evidence`() {
        val export = builder.build(
            storeId = STORE_ID,
            records = listOf(
                record(
                    checkedAt = 2_000,
                    status = SignatureStatus.INVALID,
                    failure = VerificationFailure.TAMPERED,
                ),
                record(checkedAt = 1_000),
            ),
            exportedAtEpochMillis = EXPORTED_AT,
        )
        val json = builder.encode(export)

        assertEquals(SECURITY_AUDIT_SCHEMA_VERSION, export.schemaVersion)
        assertEquals(EXPORTED_AT, export.exportedAtEpochMillis)
        assertEquals("2023-11-14T22:13:20Z", export.exportedAtUtc)
        assertEquals(STORE_ID, export.storeId)
        assertEquals(listOf(1_000L, 2_000L), export.records.map { it.checkedAtEpochMillis })
        assertTrue(json.contains("\"exportedAtUtc\": \"2023-11-14T22:13:20Z\""))
        assertTrue(json.contains("\"checkedAtEpochMillis\""))
        assertTrue(json.contains("\"sourceUrl\": \"https://repo.example/entry.jar\""))
        assertTrue(json.contains("\"failure\": \"TAMPERED\""))
        assertTrue(json.contains("APK hash and signer continuity"))
        assertFalse(json.contains("trustNote"))
        assertFalse(json.contains("storeName"))
    }

    @Test
    fun `empty audit is rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            builder.build(
                storeId = STORE_ID,
                records = emptyList(),
                exportedAtEpochMillis = EXPORTED_AT,
            )
        }
    }

    @Test
    fun `records from another store are rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            builder.build(
                storeId = STORE_ID,
                records = listOf(record(checkedAt = 1_000, storeId = "other")),
                exportedAtEpochMillis = EXPORTED_AT,
            )
        }
    }

    @Test
    fun `negative export timestamp is rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            builder.build(
                storeId = STORE_ID,
                records = listOf(record(checkedAt = 1_000)),
                exportedAtEpochMillis = -1L,
            )
        }
    }

    private fun record(
        checkedAt: Long,
        status: SignatureStatus = SignatureStatus.VERIFIED,
        failure: VerificationFailure? = null,
        storeId: String = STORE_ID,
    ) = TrustRecord(
        storeId = storeId,
        checkedAtEpochMillis = checkedAt,
        signatureStatus = status,
        expectedFingerprint = "a".repeat(64),
        signerFingerprint = if (status == SignatureStatus.INVALID) null else "a".repeat(64),
        sourceUrl = "https://repo.example/entry.jar",
        failure = failure,
    )

    private companion object {
        const val STORE_ID = "store"
        const val EXPORTED_AT = 1_700_000_000_000L
    }
}
