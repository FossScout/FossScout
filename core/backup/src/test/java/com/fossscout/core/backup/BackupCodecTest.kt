package com.fossscout.core.backup

import com.fossscout.core.store.BuiltInStores
import com.fossscout.core.store.StoreDescriptor
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupCodecTest {
    private val codec = BackupCodec()

    @Test
    fun `round trip preserves every profile section and notes`() {
        val envelope = sampleEnvelope()

        val decoded = codec.decode(codec.encode(envelope))

        assertEquals(envelope, decoded)
        assertEquals("private note", decoded.payload.favourites.single().note)
        assertEquals(
            "a".repeat(64),
            decoded.payload.customStores.single().fingerprint,
        )
        assertEquals(
            listOf(BackupSection.FAVOURITES, BackupSection.NOTES),
            decoded.manifest.included.take(2),
        )
        assertTrue(decoded.manifest.excluded.contains(BackupExclusion.APK_FILES))
    }

    @Test
    fun `match overrides round trip in the current manifest`() {
        val override = sampleMatchOverride()
        val envelope = sampleEnvelope(
            samplePayload(matchOverrides = listOf(override)),
        )

        val decoded = codec.decode(codec.encode(envelope))

        assertEquals(listOf(override), decoded.payload.matchOverrides)
        assertTrue(decoded.manifest.included.contains(BackupSection.MATCH_OVERRIDES))
        assertFalse(decoded.manifest.excluded.contains(BackupExclusion.MATCH_OVERRIDES))
    }

    @Test
    fun `legacy v1 backup migrates to the current manifest`() {
        val legacy = BackupEnvelope(
            schemaVersion = LEGACY_BACKUP_SCHEMA_VERSION,
            appVersion = "0.1.0",
            createdAtEpochMillis = 1_000,
            manifest = BackupManifest(
                included = BackupManifest.LEGACY_INCLUDED_SECTIONS,
                excluded = BackupManifest.LEGACY_EXCLUDED_SECTIONS,
            ),
            payload = samplePayload(),
            checksum = "",
        )
        val legacyJson = Json {
            ignoreUnknownKeys = false
            encodeDefaults = true
            explicitNulls = false
        }
        val encoded = legacyJson.encodeToString(
            legacy.copy(checksum = codec.checksumFor(legacy)),
        )

        val decoded = codec.decode(encoded)

        assertEquals(BACKUP_SCHEMA_VERSION, decoded.schemaVersion)
        assertTrue(decoded.manifest.isCurrent)
        assertEquals(legacy.payload, decoded.payload)
    }

    @Test
    fun `changing the payload invalidates the checksum`() {
        val encoded = codec.encode(sampleEnvelope())
        val tampered = encoded.replace("private note", "changed note")

        val error = assertThrows(BackupFormatException::class.java) {
            codec.decode(tampered)
        }
        assertEquals(BackupFormatReason.CHECKSUM_MISMATCH, error.reason)
    }

    @Test
    fun `changing metadata invalidates the checksum`() {
        val encoded = codec.encode(sampleEnvelope())
        val tampered = encoded.replace("\"appVersion\":\"0.1.0\"", "\"appVersion\":\"9.9.9\"")

        val error = assertThrows(BackupFormatException::class.java) {
            codec.decode(tampered)
        }
        assertEquals(BackupFormatReason.CHECKSUM_MISMATCH, error.reason)
    }

    @Test
    fun `newer schema fails closed`() {
        val encoded = codec.encode(sampleEnvelope())
        val tampered = encoded.replace("\"schemaVersion\":2", "\"schemaVersion\":3")

        val error = assertThrows(BackupFormatException::class.java) {
            codec.decode(tampered)
        }
        assertEquals(BackupFormatReason.UNSUPPORTED_VERSION, error.reason)
    }

    @Test
    fun `unsupported format fails closed`() {
        val encoded = codec.encode(sampleEnvelope())
        val tampered = encoded.replace(
            "\"format\":\"$BACKUP_FORMAT\"",
            "\"format\":\"some-other-format\"",
        )

        val error = assertThrows(BackupFormatException::class.java) {
            codec.decode(tampered)
        }
        assertEquals(BackupFormatReason.UNSUPPORTED_FORMAT, error.reason)
    }

    @Test
    fun `duplicate custom store ids fail closed`() {
        val payload = samplePayload(
            customStores = listOf(
                sampleStore(id = "duplicate"),
                sampleStore(id = "duplicate"),
            ),
        )

        assertThrows(IllegalArgumentException::class.java) {
            codec.create(
                appVersion = "0.1.0",
                createdAtEpochMillis = 1,
                payload = payload,
            )
        }
    }

    @Test
    fun `malformed json fails closed`() {
        val error = assertThrows(BackupFormatException::class.java) {
            codec.decode("{not-json")
        }
        assertEquals(BackupFormatReason.MALFORMED, error.reason)
    }

    @Test
    fun `built-in stores cannot be embedded as custom stores`() {
        val payload = samplePayload(
            customStores = listOf(
                sampleStore(id = BuiltInStores.FDROID_ID).copy(builtIn = true),
            ),
        )

        assertThrows(IllegalArgumentException::class.java) {
            codec.create(
                appVersion = "0.1.0",
                createdAtEpochMillis = 1,
                payload = payload,
            )
        }
    }

    @Test
    fun `custom store fingerprint survives a round trip`() {
        val fingerprint = "b".repeat(64)
        val payload = samplePayload(
            customStores = listOf(sampleStore(fingerprint = fingerprint)),
        )

        val decoded = codec.decode(codec.encode(sampleEnvelope(payload)))

        assertEquals(fingerprint, decoded.payload.customStores.single().fingerprint)
    }

    @Test
    fun `store with the built-in id but not built-in flag is rejected`() {
        val payload = samplePayload(
            customStores = listOf(
                StoreDescriptor(
                    id = com.fossscout.core.model.StoreId(BuiltInStores.FDROID_ID),
                    name = "Fake F-Droid",
                    homepage = "https://example.com",
                    adapterType = com.fossscout.core.store.AdapterType.FDROID_V2,
                    indexUrl = "https://example.com/repo",
                ),
            ),
        )

        assertThrows(IllegalArgumentException::class.java) {
            codec.create(
                appVersion = "0.1.0",
                createdAtEpochMillis = 1,
                payload = payload,
            )
        }
    }
}
