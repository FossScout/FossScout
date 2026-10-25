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
        validateOrThrow(envelope)
        val expected = checksumFor(envelope)
        if (envelope.checksum != expected) {
            throw BackupFormatException(
                BackupFormatReason.CHECKSUM_MISMATCH,
                "Backup checksum does not match its contents",
            )
        }
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

        validateOrThrow(envelope)
        val expected = checksumFor(envelope)
        if (envelope.checksum != expected) {
            throw BackupFormatException(
                BackupFormatReason.CHECKSUM_MISMATCH,
                "Backup checksum does not match its contents",
            )
        }
        return envelope
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

    private fun validateOrThrow(envelope: BackupEnvelope) {
        if (envelope.format != BACKUP_FORMAT) {
            throw BackupFormatException(
                BackupFormatReason.UNSUPPORTED_FORMAT,
                "Unsupported backup format: ${envelope.format}",
            )
        }
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
