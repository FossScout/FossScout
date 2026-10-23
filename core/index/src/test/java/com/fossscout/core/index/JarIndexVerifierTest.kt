package com.fossscout.core.index

import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class JarIndexVerifierTest {
    private val signer = TestSigningKey()
    private val verifier = JarIndexVerifier()

    @Test
    fun `valid signed entry matches the pinned fingerprint`() = runTest {
        val signed = signer.sign("""{"timestamp":1}""")
        val jar = write("entry.jar", signed.jar)

        val result = verifier.verifyEntry(
            EntryVerificationRequest(
                storeId = "test",
                entryJar = jar,
                expectedFingerprint = signed.fingerprint,
            ),
        )

        assertEquals(SignatureStatus.VERIFIED, result.status)
        assertEquals(signed.fingerprint, result.signerFingerprint)
        assertEquals(signed.fingerprint, result.expectedFingerprint)
        assertTrue(result.activatable)
        assertNull(result.failure)
    }

    @Test
    fun `valid signed entry without a pin is unverified but activatable`() = runTest {
        val signed = signer.sign("""{"timestamp":1}""")
        val jar = write("entry.jar", signed.jar)

        val result = verifier.verifyEntry(
            EntryVerificationRequest("test", jar, expectedFingerprint = null),
        )

        assertEquals(SignatureStatus.UNVERIFIED, result.status)
        assertEquals(VerificationFailure.NO_PINNED_FINGERPRINT, result.failure)
        assertEquals(signed.fingerprint, result.signerFingerprint)
        assertTrue(result.activatable)
        assertFalse(result.verified)
    }

    @Test
    fun `unexpected signer fingerprint fails closed`() = runTest {
        val signed = signer.sign("""{"timestamp":1}""")
        val jar = write("entry.jar", signed.jar)

        val result = verifier.verifyEntry(
            EntryVerificationRequest("test", jar, "0".repeat(64)),
        )

        assertEquals(SignatureStatus.INVALID, result.status)
        assertEquals(VerificationFailure.SIGNER_MISMATCH, result.failure)
        assertFalse(result.activatable)
    }

    @Test
    fun `unsigned entry is never activatable`() = runTest {
        val jar = write("unsigned.jar", signer.unsigned("""{"timestamp":1}"""))

        val result = verifier.verifyEntry(
            EntryVerificationRequest("test", jar, signer.fingerprint),
        )

        assertEquals(SignatureStatus.UNSIGNED, result.status)
        assertEquals(VerificationFailure.NO_SIGNATURE, result.failure)
        assertFalse(result.activatable)
    }

    @Test
    fun `tampered signed entry is rejected`() = runTest {
        val signed = signer.sign("""{"timestamp":1}""")
        val tampered = tamperJar(
            signed.jar,
            """{"timestamp":999,"index":{"name":"/index-v2.json","sha256":"${"a".repeat(64)}","size":1}}""",
        )
        val jar = write("tampered.jar", tampered)

        val result = verifier.verifyEntry(
            EntryVerificationRequest("test", jar, signer.fingerprint),
        )

        assertEquals(SignatureStatus.INVALID, result.status)
        assertEquals(VerificationFailure.TAMPERED, result.failure)
        assertFalse(result.activatable)
    }

    @Test
    fun `missing entry JAR is rejected`() = runTest {
        val result = verifier.verifyEntry(
            EntryVerificationRequest(
                "test",
                File(signer.dir, "missing.jar"),
                signer.fingerprint,
            ),
        )

        assertEquals(SignatureStatus.INVALID, result.status)
        assertEquals(VerificationFailure.ENTRY_MISSING, result.failure)
    }

    @Test
    fun `digest verification distinguishes hash and size failures`() {
        val hash = "a".repeat(64)

        assertEquals(
            SignatureStatus.VERIFIED,
            verifier.verifyDigest(
                DigestVerificationRequest("test", hash, hash, expectedSize = 4, actualSize = 4),
            ).status,
        )
        assertEquals(
            VerificationFailure.HASH_MISMATCH,
            verifier.verifyDigest(
                DigestVerificationRequest("test", hash, "b".repeat(64)),
            ).failure,
        )
        assertEquals(
            VerificationFailure.SIZE_MISMATCH,
            verifier.verifyDigest(
                DigestVerificationRequest("test", hash, hash, expectedSize = 4, actualSize = 5),
            ).failure,
        )
    }

    private fun write(name: String, bytes: ByteArray): File {
        val file = File(signer.dir, name)
        file.writeBytes(bytes)
        return file
    }
}
