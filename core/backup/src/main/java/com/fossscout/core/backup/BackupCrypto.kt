package com.fossscout.core.backup

import java.security.SecureRandom
import java.util.Base64
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

enum class BackupEncryptionReason {
    MALFORMED,
    UNSUPPORTED_VERSION,
    WRONG_PASSPHRASE_OR_TAMPERED,
}

/** An encrypted backup could not be decrypted or authenticated. */
class BackupEncryptionException(
    val reason: BackupEncryptionReason,
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

/**
 * Optional passphrase protection using standard JCA primitives:
 * PBKDF2WithHmacSHA256 (210,000 iterations, 256-bit key) and AES-256-GCM.
 *
 * The salt and nonce are random for every export. The KDF/cipher parameters are
 * versioned and pinned; a file cannot request a different iteration count or
 * cipher. A wrong passphrase and a modified ciphertext both fail authentication.
 */
class BackupCrypto(
    private val random: SecureRandom = SecureRandom(),
) {
    fun isEncrypted(encoded: String): Boolean = runCatching {
        json.parseToJsonElement(encoded)
            .jsonObject["format"]
            ?.jsonPrimitive
            ?.content == ENCRYPTED_BACKUP_FORMAT
    }.getOrDefault(false)

    fun encrypt(plaintext: String, passphrase: CharArray): String {
        require(passphrase.isNotEmpty()) { "Passphrase is empty" }
        val salt = ByteArray(SALT_BYTES).also(random::nextBytes)
        val nonce = ByteArray(NONCE_BYTES).also(random::nextBytes)
        val key = deriveKey(passphrase, salt)
        val cipher = Cipher.getInstance(CIPHER)
        cipher.init(
            Cipher.ENCRYPT_MODE,
            SecretKeySpec(key, "AES"),
            GCMParameterSpec(TAG_BITS, nonce),
        )
        cipher.updateAAD(AAD)
        val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        return json.encodeToString(
            EncryptedBackupEnvelope(
                salt = base64(salt),
                nonce = base64(nonce),
                ciphertext = base64(ciphertext),
            ),
        )
    }

    fun decrypt(encoded: String, passphrase: CharArray): String {
        require(passphrase.isNotEmpty()) { "Passphrase is empty" }
        val envelope = try {
            json.decodeFromString<EncryptedBackupEnvelope>(encoded)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (exception: Exception) {
            throw BackupEncryptionException(
                BackupEncryptionReason.MALFORMED,
                "Malformed encrypted backup",
                exception,
            )
        }

        validateEnvelope(envelope)
        val salt = try {
            Base64.getDecoder().decode(envelope.salt)
        } catch (exception: IllegalArgumentException) {
            throw BackupEncryptionException(
                BackupEncryptionReason.MALFORMED,
                "Invalid encryption salt",
                exception,
            )
        }
        val nonce = try {
            Base64.getDecoder().decode(envelope.nonce)
        } catch (exception: IllegalArgumentException) {
            throw BackupEncryptionException(
                BackupEncryptionReason.MALFORMED,
                "Invalid encryption nonce",
                exception,
            )
        }
        val ciphertext = try {
            Base64.getDecoder().decode(envelope.ciphertext)
        } catch (exception: IllegalArgumentException) {
            throw BackupEncryptionException(
                BackupEncryptionReason.MALFORMED,
                "Invalid encrypted payload",
                exception,
            )
        }
        if (salt.size != SALT_BYTES || nonce.size != NONCE_BYTES ||
            ciphertext.size < TAG_BITS / 8
        ) {
            throw BackupEncryptionException(
                BackupEncryptionReason.MALFORMED,
                "Invalid encryption parameters",
            )
        }

        val key = deriveKey(passphrase, salt)
        val cipher = Cipher.getInstance(CIPHER)
        cipher.init(
            Cipher.DECRYPT_MODE,
            SecretKeySpec(key, "AES"),
            GCMParameterSpec(TAG_BITS, nonce),
        )
        cipher.updateAAD(AAD)
        val plaintext = try {
            cipher.doFinal(ciphertext)
        } catch (exception: AEADBadTagException) {
            throw BackupEncryptionException(
                BackupEncryptionReason.WRONG_PASSPHRASE_OR_TAMPERED,
                "Wrong passphrase or modified backup",
                exception,
            )
        } catch (exception: Exception) {
            throw BackupEncryptionException(
                BackupEncryptionReason.MALFORMED,
                "Encrypted backup could not be opened",
                exception,
            )
        }
        return String(plaintext, Charsets.UTF_8)
    }

    private fun validateEnvelope(envelope: EncryptedBackupEnvelope) {
        if (envelope.format != ENCRYPTED_BACKUP_FORMAT) {
            throw BackupEncryptionException(
                BackupEncryptionReason.MALFORMED,
                "Unsupported encrypted backup format",
            )
        }
        if (envelope.schemaVersion != ENCRYPTION_SCHEMA_VERSION) {
            throw BackupEncryptionException(
                BackupEncryptionReason.UNSUPPORTED_VERSION,
                "Unsupported encryption schema version: ${envelope.schemaVersion}",
            )
        }
        if (envelope.kdf != KDF || envelope.iterations != KDF_ITERATIONS ||
            envelope.keyLengthBits != KEY_BITS || envelope.cipher != CIPHER
        ) {
            throw BackupEncryptionException(
                BackupEncryptionReason.MALFORMED,
                "Unsupported encryption parameters",
            )
        }
    }

    private fun deriveKey(passphrase: CharArray, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(passphrase, salt, KDF_ITERATIONS, KEY_BITS)
        return try {
            SecretKeyFactory.getInstance(KDF).generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    private fun base64(value: ByteArray): String = Base64.getEncoder().encodeToString(value)

    @Serializable
    private data class EncryptedBackupEnvelope(
        val format: String = ENCRYPTED_BACKUP_FORMAT,
        val schemaVersion: Int = ENCRYPTION_SCHEMA_VERSION,
        val kdf: String = KDF,
        val iterations: Int = KDF_ITERATIONS,
        val keyLengthBits: Int = KEY_BITS,
        val cipher: String = CIPHER,
        val salt: String,
        val nonce: String,
        val ciphertext: String,
    )

    private companion object {
        const val ENCRYPTION_SCHEMA_VERSION = 1
        const val KDF = "PBKDF2WithHmacSHA256"
        const val CIPHER = "AES/GCM/NoPadding"
        const val KDF_ITERATIONS = 210_000
        const val KEY_BITS = 256
        const val TAG_BITS = 128
        const val SALT_BYTES = 16
        const val NONCE_BYTES = 12
        val AAD = "FossScout backup v1".toByteArray(Charsets.UTF_8)

        val json = Json {
            ignoreUnknownKeys = false
            encodeDefaults = true
            explicitNulls = false
        }
    }
}
