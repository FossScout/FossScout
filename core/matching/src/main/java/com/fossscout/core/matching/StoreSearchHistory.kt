package com.fossscout.core.matching

import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

const val STORE_SEARCH_HISTORY_LIMIT = 5
const val STORE_SEARCH_HISTORY_SCHEMA_VERSION = 1

/** Local-only recent store-search queries and whether the user allows saving them. */
data class StoreSearchHistory(
    val enabled: Boolean = true,
    val queries: List<String> = emptyList(),
)

/** The stored search history could not be decoded or has an unknown version. */
class StoreSearchHistoryFormatException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

@Serializable
private data class StoreSearchHistoryEnvelope(
    val schemaVersion: Int,
    val queries: List<String> = emptyList(),
)

/** Versioned codec for the local recent-query list. */
class StoreSearchHistoryCodec(
    private val json: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    },
) {
    fun encode(queries: List<String>): String = json.encodeToString(
        StoreSearchHistoryEnvelope(
            schemaVersion = STORE_SEARCH_HISTORY_SCHEMA_VERSION,
            queries = queries,
        ),
    )

    fun decode(encoded: String): List<String> {
        val envelope = try {
            json.decodeFromString<StoreSearchHistoryEnvelope>(encoded)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (exception: Exception) {
            throw StoreSearchHistoryFormatException(
                "Malformed store search history",
                exception,
            )
        }
        if (envelope.schemaVersion != STORE_SEARCH_HISTORY_SCHEMA_VERSION) {
            throw StoreSearchHistoryFormatException(
                "Unsupported store search history schema version: " +
                    envelope.schemaVersion,
            )
        }
        return envelope.queries
    }
}

/** Persistence boundary for local recent store-search queries. */
interface StoreSearchHistoryStore {
    val history: Flow<StoreSearchHistory>

    suspend fun save(history: StoreSearchHistory)

    suspend fun clearQueries()
}

/**
 * Bounds and deduplicates recent queries. History is local-only and can be
 * disabled or cleared by the user.
 */
class StoreSearchHistoryRepository(
    private val store: StoreSearchHistoryStore,
    private val limit: Int = STORE_SEARCH_HISTORY_LIMIT,
) {
    init {
        require(limit > 0) { "Search history limit must be positive" }
    }

    private val mutex = Mutex()

    val history: Flow<StoreSearchHistory> = store.history.map(::normalized)

    suspend fun record(query: String) {
        val normalizedQuery = query.trim()
        if (normalizedQuery.isEmpty()) return
        mutex.withLock {
            val current = history.first()
            if (!current.enabled) return@withLock
            val next = (listOf(normalizedQuery) + current.queries.filterNot {
                it.equals(normalizedQuery, ignoreCase = true)
            }).take(limit)
            if (next != current.queries) {
                store.save(current.copy(queries = next))
            }
        }
    }

    suspend fun setEnabled(enabled: Boolean) {
        mutex.withLock {
            val current = history.first()
            val shouldSave = current.enabled != enabled ||
                (!enabled && current.queries.isNotEmpty())
            if (!shouldSave) return@withLock
            store.save(
                current.copy(
                    enabled = enabled,
                    queries = if (enabled) current.queries else emptyList(),
                ),
            )
        }
    }

    suspend fun clear() {
        mutex.withLock {
            store.clearQueries()
        }
    }

    private fun normalized(history: StoreSearchHistory): StoreSearchHistory {
        val queries = history.queries.asSequence()
            .map(String::trim)
            .filter(String::isNotBlank)
            .distinctBy { it.lowercase(Locale.ROOT) }
            .take(limit)
            .toList()
        return history.copy(
            queries = if (history.enabled) queries else emptyList(),
        )
    }
}
