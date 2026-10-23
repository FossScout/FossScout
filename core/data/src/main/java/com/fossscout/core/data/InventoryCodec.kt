package com.fossscout.core.data

import com.fossscout.core.inventory.INVENTORY_SCHEMA_VERSION
import com.fossscout.core.inventory.InventoryState
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** The stored inventory blob could not be decoded or has an unknown version. */
class InventoryFormatException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

@Serializable
private data class InventoryEnvelope(
    val schemaVersion: Int,
    val state: InventoryState,
)

/**
 * Versioned local envelope for the last complete snapshot and hide list.
 * Malformed data is surfaced as [InventoryFormatException] instead of being
 * silently replaced, so a read error cannot destroy a valid prior snapshot.
 */
class InventoryCodec(
    private val json: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    },
) {
    fun encode(state: InventoryState): String =
        json.encodeToString(
            InventoryEnvelope(
                schemaVersion = INVENTORY_SCHEMA_VERSION,
                state = state,
            ),
        )

    fun decode(encoded: String): InventoryState {
        val envelope = try {
            json.decodeFromString<InventoryEnvelope>(encoded)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (exception: Exception) {
            throw InventoryFormatException("Malformed inventory data", exception)
        }

        if (envelope.schemaVersion != INVENTORY_SCHEMA_VERSION) {
            throw InventoryFormatException(
                "Unsupported inventory schema version: ${envelope.schemaVersion}",
            )
        }
        val snapshotVersion = envelope.state.snapshot?.schemaVersion
        if (snapshotVersion != null && snapshotVersion != INVENTORY_SCHEMA_VERSION) {
            throw InventoryFormatException(
                "Unsupported snapshot schema version: $snapshotVersion",
            )
        }
        return envelope.state
    }
}
