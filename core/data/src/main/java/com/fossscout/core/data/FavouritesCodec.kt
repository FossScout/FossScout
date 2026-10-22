package com.fossscout.core.data

import com.fossscout.core.model.FAVOURITES_SCHEMA_VERSION
import com.fossscout.core.model.Favourite
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** The stored favourites blob could not be decoded or has an unsupported version. */
class FavouritesFormatException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

@Serializable
private data class FavouritesEnvelope(
    val schemaVersion: Int,
    val favourites: List<Favourite>,
)

/**
 * Encodes favourites inside a versioned envelope. Decoding never falls back to an
 * empty list: malformed data or an unknown schema version raises
 * [FavouritesFormatException] so callers can show an error instead of overwriting
 * data they could not read.
 */
class FavouritesCodec(
    private val json: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    },
) {
    fun encode(favourites: List<Favourite>): String =
        json.encodeToString(
            FavouritesEnvelope(
                schemaVersion = FAVOURITES_SCHEMA_VERSION,
                favourites = favourites,
            ),
        )

    fun decode(encoded: String): List<Favourite> {
        val envelope = try {
            json.decodeFromString<FavouritesEnvelope>(encoded)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (exception: Exception) {
            throw FavouritesFormatException("Malformed favourites data", exception)
        }

        if (envelope.schemaVersion != FAVOURITES_SCHEMA_VERSION) {
            throw FavouritesFormatException(
                "Unsupported favourites schema version: ${envelope.schemaVersion}",
            )
        }
        envelope.favourites.forEach { favourite ->
            if (favourite.schemaVersion != FAVOURITES_SCHEMA_VERSION) {
                throw FavouritesFormatException(
                    "Unsupported favourite schema version: ${favourite.schemaVersion}",
                )
            }
        }
        return envelope.favourites
    }
}
