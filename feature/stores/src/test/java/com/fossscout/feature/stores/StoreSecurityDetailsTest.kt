package com.fossscout.feature.stores

import com.fossscout.core.index.SignatureStatus
import com.fossscout.core.index.StoreSnapshot
import com.fossscout.core.index.SyncFailure
import com.fossscout.core.index.SyncFailureKind
import com.fossscout.core.index.SyncJob
import com.fossscout.core.index.SyncState
import com.fossscout.core.index.TrustRecord
import com.fossscout.core.index.VerificationFailure
import com.fossscout.core.model.StoreId
import com.fossscout.core.store.AdapterType
import com.fossscout.core.store.StoreDescriptor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StoreSecurityDetailsTest {
    @Test
    fun `verified pinned store is described as verified`() {
        val pin = "a".repeat(64)
        val details = StoreSecurityDetails.from(
            descriptor = descriptor(fingerprint = pin),
            status = StoreSyncStatus(
                snapshot = snapshot(expected = pin, signer = pin),
                trust = trust(
                    status = SignatureStatus.VERIFIED,
                    expected = pin,
                    signer = pin,
                ),
            ),
        )

        assertEquals(StoreTrustState.VERIFIED, details.trustState)
        assertEquals(pin, details.pinnedFingerprint)
        assertNull(details.rotation)
    }

    @Test
    fun `valid signature without a pin is unverified not failed`() {
        val pin = "b".repeat(64)
        val details = StoreSecurityDetails.from(
            descriptor = descriptor(fingerprint = null),
            status = StoreSyncStatus(
                snapshot = snapshot(expected = null, signer = pin),
                trust = trust(
                    status = SignatureStatus.UNVERIFIED,
                    expected = null,
                    signer = pin,
                    failure = VerificationFailure.NO_PINNED_FINGERPRINT,
                ),
            ),
        )

        assertEquals(StoreTrustState.UNVERIFIED, details.trustState)
        assertNull(details.pinnedFingerprint)
        assertNull(details.rotation)
    }

    @Test
    fun `tampered index is failed`() {
        val pin = "c".repeat(64)
        val details = StoreSecurityDetails.from(
            descriptor = descriptor(fingerprint = pin),
            status = StoreSyncStatus(
                trust = trust(
                    status = SignatureStatus.INVALID,
                    expected = pin,
                    signer = null,
                    failure = VerificationFailure.TAMPERED,
                ),
            ),
        )

        assertEquals(StoreTrustState.FAILED, details.trustState)
        assertEquals(VerificationFailure.TAMPERED, details.verificationFailure)
        assertNull(details.rotation)
    }

    @Test
    fun `signer mismatch offers the new signer for review`() {
        val oldPin = "d".repeat(64)
        val newPin = "e".repeat(64)
        val details = StoreSecurityDetails.from(
            descriptor = descriptor(fingerprint = oldPin),
            status = StoreSyncStatus(
                trust = trust(
                    status = SignatureStatus.INVALID,
                    expected = oldPin,
                    signer = newPin,
                    failure = VerificationFailure.SIGNER_MISMATCH,
                ),
            ),
        )

        assertEquals(StoreTrustState.ROTATION_REQUIRED, details.trustState)
        assertEquals(oldPin, details.rotation?.currentFingerprint)
        assertEquals(newPin, details.rotation?.newFingerprint)
        assertEquals(
            FingerprintRotationReason.SIGNER_MISMATCH,
            details.rotation?.reason,
        )
    }

    @Test
    fun `edited pin requires review before sync resumes`() {
        val oldPin = "f".repeat(64)
        val newPin = "1".repeat(64)
        val details = StoreSecurityDetails.from(
            descriptor = descriptor(fingerprint = newPin),
            status = StoreSyncStatus(
                snapshot = snapshot(expected = oldPin, signer = oldPin),
                trust = trust(
                    status = SignatureStatus.VERIFIED,
                    expected = oldPin,
                    signer = oldPin,
                ),
            ),
        )

        assertEquals(StoreTrustState.ROTATION_REQUIRED, details.trustState)
        assertEquals(newPin, details.rotation?.newFingerprint)
        assertEquals(FingerprintRotationReason.PIN_CHANGED, details.rotation?.reason)
    }

    @Test
    fun `removing the pin requires review but cannot be approved as a new pin`() {
        val oldPin = "2".repeat(64)
        val details = StoreSecurityDetails.from(
            descriptor = descriptor(fingerprint = null),
            status = StoreSyncStatus(
                snapshot = snapshot(expected = oldPin, signer = oldPin),
                trust = trust(
                    status = SignatureStatus.VERIFIED,
                    expected = oldPin,
                    signer = oldPin,
                ),
            ),
        )

        assertEquals(StoreTrustState.ROTATION_REQUIRED, details.trustState)
        assertEquals(oldPin, details.rotation?.currentFingerprint)
        assertNull(details.rotation?.newFingerprint)
        assertEquals(FingerprintRotationReason.PIN_REMOVED, details.rotation?.reason)
    }

    @Test
    fun `HTTP opt-in is exposed separately from trust state`() {
        val details = StoreSecurityDetails.from(
            descriptor = descriptor(
                fingerprint = null,
                indexUrl = "http://insecure.example/repo",
                allowInsecureHttp = true,
            ),
            status = null,
        )

        assertTrue(details.httpInsecure)
        assertTrue(details.httpOptIn)
        assertEquals(StoreTrustState.NOT_CHECKED, details.trustState)
    }

    @Test
    fun `failed index verification is failed even when the entry signature was valid`() {
        val pin = "3".repeat(64)
        val details = StoreSecurityDetails.from(
            descriptor = descriptor(fingerprint = pin),
            status = StoreSyncStatus(
                snapshot = snapshot(expected = pin, signer = pin),
                trust = trust(
                    status = SignatureStatus.VERIFIED,
                    expected = pin,
                    signer = pin,
                ),
                job = SyncJob(
                    storeId = "store",
                    state = SyncState.FAILED,
                    error = SyncFailure(SyncFailureKind.VERIFICATION, "index hash mismatch"),
                ),
            ),
        )

        assertEquals(StoreTrustState.FAILED, details.trustState)
        assertEquals(SyncFailureKind.VERIFICATION, details.syncFailure)
    }

    @Test
    fun `unchecked store is not presented as verified or failed`() {
        val details = StoreSecurityDetails.from(
            descriptor = descriptor(fingerprint = null),
            status = null,
        )

        assertEquals(StoreTrustState.NOT_CHECKED, details.trustState)
        assertFalse(details.audit.isNotEmpty())
    }

    private fun descriptor(
        fingerprint: String?,
        indexUrl: String = "https://repo.example/repo",
        allowInsecureHttp: Boolean = false,
    ) = StoreDescriptor(
        id = StoreId("store"),
        name = "Store",
        homepage = "https://repo.example",
        adapterType = AdapterType.FDROID_V2,
        indexUrl = indexUrl,
        fingerprint = fingerprint,
        allowInsecureHttp = allowInsecureHttp,
    )

    private fun snapshot(
        expected: String?,
        signer: String?,
    ) = StoreSnapshot(
        storeId = "store",
        fetchedAtEpochMillis = 1_000,
        indexTimestamp = 900,
        indexSha256 = "9".repeat(64),
        packageCount = 1,
        payloadPath = "payload.ndjson",
        payloadSizeBytes = 10,
        signatureStatus = SignatureStatus.VERIFIED,
        sourceUrl = "https://repo.example/repo/index-v2.json",
        expectedFingerprint = expected,
        signerFingerprint = signer,
    )

    private fun trust(
        status: SignatureStatus,
        expected: String?,
        signer: String?,
        failure: VerificationFailure? = null,
    ) = TrustRecord(
        storeId = "store",
        checkedAtEpochMillis = 1_000,
        signatureStatus = status,
        expectedFingerprint = expected,
        signerFingerprint = signer,
        sourceUrl = "https://repo.example/repo/entry.jar",
        failure = failure,
    )
}
