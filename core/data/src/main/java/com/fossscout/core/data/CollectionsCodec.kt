package com.fossscout.core.data

import com.fossscout.core.model.AppCollection
import com.fossscout.core.model.COLLECTION_SCHEMA_VERSION
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Stored collection data was malformed or used an unsupported schema version. */
class CollectionsFormatException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

@Serializable
private data class CollectionsEnvelope(
    val schemaVersion: Int,
    val collections: List<AppCollection> = emptyList(),
)

/**
 * Versioned JSON persistence for collections. Decoding never returns an empty
 * list for unreadable data: the caller sees [CollectionsFormatException] and
 * must not overwrite data that could not be understood.
 */
class CollectionsCodec(
    private val json: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    },
) {
    fun encode(collections: List<AppCollection>): String {
        collections.forEach { collection ->
            require(collection.schemaVersion == COLLECTION_SCHEMA_VERSION) {
                "Unsupported collection schema version: ${collection.schemaVersion}"
            }
            collection.validate()
        }
        require(collections.map(AppCollection::id).toSet().size == collections.size) {
            "Duplicate collection IDs"
        }
        return json.encodeToString(
            CollectionsEnvelope(
                schemaVersion = COLLECTION_SCHEMA_VERSION,
                collections = collections,
            ),
        )
    }

    fun decode(encoded: String): List<AppCollection> {
        val envelope = try {
            json.decodeFromString<CollectionsEnvelope>(encoded)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (exception: Exception) {
            throw CollectionsFormatException("Malformed collections data", exception)
        }

        if (envelope.schemaVersion != COLLECTION_SCHEMA_VERSION) {
            throw CollectionsFormatException(
                "Unsupported collections schema version: ${envelope.schemaVersion}",
            )
        }
        envelope.collections.forEach { collection ->
            if (collection.schemaVersion != COLLECTION_SCHEMA_VERSION) {
                throw CollectionsFormatException(
                    "Unsupported collection schema version: ${collection.schemaVersion}",
                )
            }
            try {
                collection.validate()
            } catch (exception: IllegalArgumentException) {
                throw CollectionsFormatException(
                    "Invalid collection ${collection.id}",
                    exception,
                )
            }
        }
        if (envelope.collections.map(AppCollection::id).toSet().size !=
            envelope.collections.size
        ) {
            throw CollectionsFormatException("Duplicate collection IDs")
        }
        return envelope.collections
    }
}
