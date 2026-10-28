package com.fossscout.core.backup

import java.security.MessageDigest
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

enum class BackupFormatReason {
    MALFORMED,
    UNSUPPORTED_VERSION,
    UNSUPPORTED_FORMAT,
    CHECKSUM_MISMATCH,
}

/** A backup could not be decoded, validated, or verified. */
class BackupFormatException(
    val reason: BackupFormatReason,
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

/**
 * JSON codec for the full-profile backup. The checksum covers the manifest,
 * app/schema metadata, and payload, so changing any field is detected before a
 * restore plan is built.
 */
class BackupCodec(
    private val json: Json = Json {
        ignoreUnknownKeys = false
        encodeDefaults = true
        explicitNulls = false
    },
) {
    fun create(
        appVersion: String,
        createdAtEpochMillis: Long,
        payload: BackupPayload,
    ): BackupEnvelope {
        payload.validate()
        val base = BackupEnvelope(
            appVersion = appVersion,
            createdAtEpochMillis = createdAtEpochMillis,
            manifest = BackupManifest.forPayload(),
            payload = payload,
            checksum = "",
        )
        return base.copy(checksum = checksumFor(base))
    }

    fun encode(envelope: BackupEnvelope): String {
        validateCurrentOrThrow(envelope)
        verifyChecksum(envelope)
        return json.encodeToString(envelope)
    }

    fun decode(encoded: String): BackupEnvelope {
        val envelope = try {
            json.decodeFromString<BackupEnvelope>(encoded)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (exception: Exception) {
            throw BackupFormatException(
                BackupFormatReason.MALFORMED,
                "Malformed backup file",
                exception,
            )
        }

        return when (envelope.schemaVersion) {
            BACKUP_SCHEMA_VERSION -> {
                validateCurrentOrThrow(envelope)
                verifyChecksum(envelope)
                envelope
            }

            LEGACY_BACKUP_SCHEMA_VERSION -> {
                validateLegacyOrThrow(envelope)
                verifyChecksum(envelope)
                migrateLegacy(envelope)
            }

            else -> throw BackupFormatException(
                BackupFormatReason.UNSUPPORTED_VERSION,
                "Unsupported backup schema version: ${envelope.schemaVersion}",
            )
        }
    }

    fun checksumFor(envelope: BackupEnvelope): String {
        val canonical = json.encodeToString(
            ChecksumInput(
                format = envelope.format,
                schemaVersion = envelope.schemaVersion,
                appVersion = envelope.appVersion,
                createdAtEpochMillis = envelope.createdAtEpochMillis,
                manifest = envelope.manifest,
                payload = envelope.payload,
            ),
        )
        return MessageDigest.getInstance("SHA-256")
            .digest(canonical.toByteArray(Charsets.UTF_8))
            .joinToString(separator = "") { byte -> "%02x".format(byte.toInt() and 0xff) }
    }

    private fun validateCurrentOrThrow(envelope: BackupEnvelope) {
        validateFormat(envelope)
        if (envelope.schemaVersion != BACKUP_SCHEMA_VERSION) {
            throw BackupFormatException(
                BackupFormatReason.UNSUPPORTED_VERSION,
                "Unsupported backup schema version: ${envelope.schemaVersion}",
            )
        }
        try {
            envelope.validateStructure()
        } catch (exception: IllegalArgumentException) {
            throw BackupFormatException(
                BackupFormatReason.MALFORMED,
                exception.message ?: "Invalid backup data",
                exception,
            )
        }
    }

    private fun validateLegacyOrThrow(envelope: BackupEnvelope) {
        validateFormat(envelope)
        try {
            require(envelope.appVersion.isNotBlank()) { "Backup app version is blank" }
            require(envelope.createdAtEpochMillis >= 0) { "Backup creation time is negative" }
            require(BackupEnvelope.CHECKSUM_PATTERN.matches(envelope.checksum)) {
                "Backup checksum must be 64 lowercase hex characters"
            }
            require(envelope.manifest.isLegacy) {
                "Legacy backup manifest does not match the v1 profile"
            }
            envelope.payload.validate()
        } catch (exception: IllegalArgumentException) {
            throw BackupFormatException(
                BackupFormatReason.MALFORMED,
                exception.message ?: "Invalid backup data",
                exception,
            )
        }
    }

    private fun validateFormat(envelope: BackupEnvelope) {
        if (envelope.format != BACKUP_FORMAT) {
            throw BackupFormatException(
                BackupFormatReason.UNSUPPORTED_FORMAT,
                "Unsupported backup format: ${envelope.format}",
            )
        }
    }

    private fun verifyChecksum(envelope: BackupEnvelope) {
        val expected = checksumFor(envelope)
        if (envelope.checksum != expected) {
            throw BackupFormatException(
                BackupFormatReason.CHECKSUM_MISMATCH,
                "Backup checksum does not match its contents",
            )
        }
    }

    private fun migrateLegacy(envelope: BackupEnvelope): BackupEnvelope {
        val migrated = envelope.copy(
            schemaVersion = BACKUP_SCHEMA_VERSION,
            manifest = BackupManifest.forPayload(),
            checksum = "",
        )
        return migrated.copy(checksum = checksumFor(migrated))
    }

    @Serializable
    private data class ChecksumInput(
        val format: String,
        val schemaVersion: Int,
        val appVersion: String,
        val createdAtEpochMillis: Long,
        val manifest: BackupManifest,
        val payload: BackupPayload,
    )
}
