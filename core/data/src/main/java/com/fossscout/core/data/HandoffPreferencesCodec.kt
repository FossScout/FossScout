package com.fossscout.core.data

import com.fossscout.core.handoff.HANDOFF_PREFERENCES_SCHEMA_VERSION
import com.fossscout.core.handoff.HandoffPreferences
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** The stored handoff preferences could not be decoded or have an unknown version. */
class HandoffPreferencesFormatException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

@Serializable
private data class HandoffPreferencesEnvelope(
    val schemaVersion: Int,
    val preferredStores: Map<String, String> = emptyMap(),
)

/**
 * Versioned codec for local preferred-store choices. Malformed or future data
 * raises instead of silently discarding the user's selection.
 */
class HandoffPreferencesCodec(
    private val json: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    },
) {
    fun encode(preferences: HandoffPreferences): String {
        require(preferences.schemaVersion == HANDOFF_PREFERENCES_SCHEMA_VERSION) {
            "Unsupported handoff preferences schema version: ${preferences.schemaVersion}"
        }
        return json.encodeToString(
            HandoffPreferencesEnvelope(
                schemaVersion = preferences.schemaVersion,
                preferredStores = preferences.preferredStores,
            ),
        )
    }

    fun decode(encoded: String): HandoffPreferences {
        val envelope = try {
            json.decodeFromString<HandoffPreferencesEnvelope>(encoded)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (exception: Exception) {
            throw HandoffPreferencesFormatException(
                "Malformed handoff preferences data",
                exception,
            )
        }
        if (envelope.schemaVersion != HANDOFF_PREFERENCES_SCHEMA_VERSION) {
            throw HandoffPreferencesFormatException(
                "Unsupported handoff preferences schema version: ${envelope.schemaVersion}",
            )
        }
        return try {
            HandoffPreferences(
                schemaVersion = envelope.schemaVersion,
                preferredStores = envelope.preferredStores,
            )
        } catch (exception: IllegalArgumentException) {
            throw HandoffPreferencesFormatException(
                "Invalid handoff preferences data",
                exception,
            )
        }
    }
}
