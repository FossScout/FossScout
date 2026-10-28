package com.fossscout.core.data

import com.fossscout.core.model.MATCH_OVERRIDE_SCHEMA_VERSION
import com.fossscout.core.model.MatchOverride
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Stored match overrides were malformed or used an unsupported version. */
class MatchOverridesFormatException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

@Serializable
private data class MatchOverridesEnvelope(
    val schemaVersion: Int,
    val overrides: List<MatchOverride> = emptyList(),
)

/**
 * Versioned JSON persistence for match overrides. Unreadable data raises an
 * error instead of looking like "no overrides", so a corrupt file cannot silently
 * re-enable a match the user rejected.
 */
class MatchOverridesCodec(
    private val json: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    },
) {
    fun encode(overrides: List<MatchOverride>): String {
        overrides.forEach(MatchOverride::validate)
        require(overrides.map(MatchOverride::installedAppKey).toSet().size == overrides.size) {
            "Duplicate match override app keys"
        }
        return json.encodeToString(
            MatchOverridesEnvelope(
                schemaVersion = MATCH_OVERRIDE_SCHEMA_VERSION,
                overrides = overrides,
            ),
        )
    }

    fun decode(encoded: String): List<MatchOverride> {
        val envelope = try {
            json.decodeFromString<MatchOverridesEnvelope>(encoded)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (exception: Exception) {
            throw MatchOverridesFormatException("Malformed match overrides", exception)
        }

        if (envelope.schemaVersion != MATCH_OVERRIDE_SCHEMA_VERSION) {
            throw MatchOverridesFormatException(
                "Unsupported match overrides schema version: ${envelope.schemaVersion}",
            )
        }
        envelope.overrides.forEach { override ->
            try {
                override.validate()
            } catch (exception: IllegalArgumentException) {
                throw MatchOverridesFormatException(
                    "Invalid match override for ${override.installedAppKey.value}",
                    exception,
                )
            }
        }
        if (envelope.overrides.map(MatchOverride::installedAppKey).toSet().size !=
            envelope.overrides.size
        ) {
            throw MatchOverridesFormatException("Duplicate match override app keys")
        }
        return envelope.overrides
    }
}
