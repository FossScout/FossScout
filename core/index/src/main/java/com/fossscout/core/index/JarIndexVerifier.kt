package com.fossscout.core.index

import java.io.File
import java.security.MessageDigest
import java.security.cert.X509Certificate
import java.util.jar.JarFile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Verifies the F-Droid v2 entry point. F-Droid signs `entry.jar` with a JAR
 * signature (the default method); the signature's certificate fingerprint is
 * the repository fingerprint published by the store. The signed entry then
 * carries the SHA-256 of every index and diff file, so verifying the entry once
 * authenticates the whole sync.
 *
 * This verifier only covers repository/index trust. APK hashes and signer
 * continuity belong to F09 and are deliberately not claimed here.
 */
class JarIndexVerifier : IndexVerifier {
    override suspend fun verifyEntry(
        request: EntryVerificationRequest,
    ): VerificationResult = withContext(Dispatchers.IO) {
        if (!request.entryJar.isFile) {
            return@withContext VerificationResult(
                status = SignatureStatus.INVALID,
                expectedFingerprint = request.expectedFingerprint,
                failure = VerificationFailure.ENTRY_MISSING,
                note = "Signed entry file is missing",
            )
        }

        try {
            JarFile(request.entryJar, true).use { jar ->
                val entry = jar.getJarEntry(ENTRY_FILE_NAME)
                    ?: return@withContext VerificationResult(
                        status = SignatureStatus.INVALID,
                        expectedFingerprint = request.expectedFingerprint,
                        failure = VerificationFailure.ENTRY_MISSING,
                        note = "Signed entry does not contain $ENTRY_FILE_NAME",
                    )

                // JAR verification is lazy: the entry must be fully read before
                // its certificates are populated and any tampering is detected.
                jar.getInputStream(entry).use { input ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (input.read(buffer) >= 0) {
                        // Drain the entry to trigger signature verification.
                    }
                }

                val certificates = entry.certificates
                if (certificates.isNullOrEmpty()) {
                    return@withContext VerificationResult(
                        status = SignatureStatus.UNSIGNED,
                        expectedFingerprint = request.expectedFingerprint,
                        failure = VerificationFailure.NO_SIGNATURE,
                        note = "Entry is not JAR-signed",
                    )
                }

                val signer = certificates.filterIsInstance<X509Certificate>().firstOrNull()
                    ?: return@withContext VerificationResult(
                        status = SignatureStatus.INVALID,
                        expectedFingerprint = request.expectedFingerprint,
                        failure = VerificationFailure.NO_SIGNATURE,
                        note = "Entry signer is not an X.509 certificate",
                    )

                val signerFingerprint = sha256Hex(signer.encoded)
                val expected = request.expectedFingerprint
                    ?.trim()
                    ?.lowercase()
                    ?.replace(":", "")
                    ?.replace(" ", "")
                    ?.takeIf(String::isNotEmpty)

                when {
                    expected == null -> VerificationResult(
                        status = SignatureStatus.UNVERIFIED,
                        expectedFingerprint = null,
                        signerFingerprint = signerFingerprint,
                        failure = VerificationFailure.NO_PINNED_FINGERPRINT,
                        note = "Signature is valid but no repository fingerprint is pinned",
                    )

                    expected != signerFingerprint -> VerificationResult(
                        status = SignatureStatus.INVALID,
                        expectedFingerprint = expected,
                        signerFingerprint = signerFingerprint,
                        failure = VerificationFailure.SIGNER_MISMATCH,
                        note = "Entry signer does not match the pinned fingerprint",
                    )

                    else -> VerificationResult(
                        status = SignatureStatus.VERIFIED,
                        expectedFingerprint = expected,
                        signerFingerprint = signerFingerprint,
                        note = "Signed entry matches the pinned fingerprint",
                    )
                }
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (security: SecurityException) {
            VerificationResult(
                status = SignatureStatus.INVALID,
                expectedFingerprint = request.expectedFingerprint,
                failure = VerificationFailure.TAMPERED,
                note = "Entry JAR signature is invalid",
            )
        } catch (exception: Exception) {
            VerificationResult(
                status = SignatureStatus.INVALID,
                expectedFingerprint = request.expectedFingerprint,
                failure = VerificationFailure.ENTRY_MALFORMED,
                note = "Entry JAR could not be verified: ${exception.message.orEmpty()}",
            )
        }
    }

    override fun verifyDigest(request: DigestVerificationRequest): VerificationResult {
        val expected = request.expectedSha256.trim().lowercase()
        val actual = request.actualSha256.trim().lowercase()
        if (!SHA256_PATTERN.matches(expected)) {
            return VerificationResult(
                status = SignatureStatus.INVALID,
                failure = VerificationFailure.ENTRY_MALFORMED,
                note = "Signed entry has an invalid SHA-256 value",
            )
        }
        if (expected != actual) {
            return VerificationResult(
                status = SignatureStatus.INVALID,
                failure = VerificationFailure.HASH_MISMATCH,
                note = "Downloaded index hash does not match the signed entry",
            )
        }
        if (
            request.expectedSize != null &&
            request.actualSize != null &&
            request.expectedSize != request.actualSize
        ) {
            return VerificationResult(
                status = SignatureStatus.INVALID,
                failure = VerificationFailure.SIZE_MISMATCH,
                note = "Downloaded index size does not match the signed entry",
            )
        }
        return VerificationResult(
            status = SignatureStatus.VERIFIED,
            note = "Downloaded index matches the signed entry hash",
        )
    }

    companion object {
        const val ENTRY_FILE_NAME = "entry.json"
        private val SHA256_PATTERN = Regex("^[0-9a-f]{64}$")

        fun sha256Hex(bytes: ByteArray): String =
            MessageDigest.getInstance("SHA-256")
                .digest(bytes)
                .joinToString(separator = "") { byte -> "%02x".format(byte) }
    }
}
