package com.fossscout.core.data

import com.fossscout.core.index.INDEX_STATE_SCHEMA_VERSION
import com.fossscout.core.index.IndexState
import com.fossscout.core.index.MAX_TRUST_AUDIT_ENTRIES
import com.fossscout.core.index.StoreSnapshot
import com.fossscout.core.index.SyncJob
import com.fossscout.core.index.TrustRecord
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** The persisted index metadata could not be decoded or has a future version. */
class IndexStateFormatException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

/**
 * Versioned JSON codec for snapshot metadata, trust records, and the last sync
 * job per store. Malformed or future data is rejected instead of being
 * replaced with empty defaults.
 */
class IndexStateCodec(
    private val json: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    },
) {
    fun encode(state: IndexState): String {
        require(state.schemaVersion == INDEX_STATE_SCHEMA_VERSION) {
            "Unsupported index state schema version: ${state.schemaVersion}"
        }
        requireKeysMatch(state)
        return json.encodeToString(IndexState.serializer(), state)
    }

    fun decode(encoded: String): IndexState {
        val state = try {
            json.decodeFromString(IndexState.serializer(), encoded)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (exception: Exception) {
            throw IndexStateFormatException("Malformed index metadata", exception)
        }
        if (state.schemaVersion != INDEX_STATE_SCHEMA_VERSION) {
            throw IndexStateFormatException(
                "Unsupported index state schema version: ${state.schemaVersion}",
            )
        }
        requireKeysMatch(state)
        return state
    }

    private fun requireKeysMatch(state: IndexState) {
        requireSnapshotKeys(state.snapshots)
        requireKeys(state.trustRecords, "trust record") { it.storeId }
        requireKeys(state.jobs, "sync job") { it.storeId }
        if (state.trustAudit.size > MAX_TRUST_AUDIT_ENTRIES) {
            throw IndexStateFormatException(
                "Trust audit has ${state.trustAudit.size} entries; " +
                    "the maximum is $MAX_TRUST_AUDIT_ENTRIES",
            )
        }
    }

    private fun requireSnapshotKeys(snapshots: Map<String, StoreSnapshot>) {
        snapshots.forEach { (key, snapshot) ->
            if (key != snapshot.storeId) {
                throw IndexStateFormatException(
                    "Snapshot key $key does not match store ${snapshot.storeId}",
                )
            }
        }
    }

    private fun <T> requireKeys(
        values: Map<String, T>,
        description: String,
        keyOf: (T) -> String,
    ) {
        values.forEach { (key, value) ->
            if (key != keyOf(value)) {
                throw IndexStateFormatException(
                    "Index $description key $key does not match value ${keyOf(value)}",
                )
            }
        }
    }
}
