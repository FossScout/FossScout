package com.fossscout.core.data

import com.fossscout.core.model.AppKey
import com.fossscout.core.model.SHARE_SCHEMA_VERSION
import com.fossscout.core.model.ShareEnvelope
import com.fossscout.core.model.ShareItem
import com.fossscout.core.model.ShareType
import com.fossscout.core.model.StoreId
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive

enum class ShareFormatReason {
    MALFORMED,
    UNSUPPORTED_VERSION,
}

/** A shared list could not be parsed or was written by a newer schema. */
class ShareFormatException(
    val reason: ShareFormatReason,
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

/**
 * JSON and human-readable text codecs for the offline sharing contract.
 *
 * JSON is the lossless exchange format. Plain text is readable without
 * FossScout and round-trips the same fields for lists exported by this app.
 * Parsing checks the schema before decoding the payload so a future version
 * produces a clear unsupported-version error instead of a crash.
 */
class ShareCodec(
    private val json: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    },
) {
    fun encodeJson(envelope: ShareEnvelope): String {
        require(envelope.schemaVersion == SHARE_SCHEMA_VERSION) {
            "Unsupported share schema version: ${envelope.schemaVersion}"
        }
        envelope.validate()
        return json.encodeToString(envelope)
    }

    fun decodeJson(encoded: String): ShareEnvelope {
        val root = try {
            Json.parseToJsonElement(encoded) as? JsonObject
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (exception: Exception) {
            throw malformed("Malformed share JSON", exception)
        } ?: throw malformed("Malformed share JSON")

        val version = root["schemaVersion"]
            ?.jsonPrimitive
            ?.intOrNull
            ?: throw malformed("Share JSON is missing its schema version")
        if (version != SHARE_SCHEMA_VERSION) {
            throw unsupported(version)
        }

        val envelope = try {
            json.decodeFromString<ShareEnvelope>(encoded)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (exception: Exception) {
            throw malformed("Malformed share JSON", exception)
        }
        return validate(envelope)
    }

    fun encodeText(envelope: ShareEnvelope): String {
        require(envelope.schemaVersion == SHARE_SCHEMA_VERSION) {
            "Unsupported share schema version: ${envelope.schemaVersion}"
        }
        envelope.validate()
        return buildString {
            appendLine("FossScout shared list")
            appendLine("Schema: $SHARE_SCHEMA_VERSION")
            appendLine("Type: ${envelope.type.wireName()}")
            appendLine("Name: ${escapeText(envelope.name)}")
            if (envelope.description.isNotBlank()) {
                appendLine("Description: ${escapeText(envelope.description)}")
            }
            envelope.collectionId?.let { appendLine("Collection ID: ${escapeText(it)}") }
            envelope.exportedAtEpochMillis?.let { appendLine("Exported at: $it") }
            appendLine("Items: ${envelope.items.size}")
            appendLine()
            envelope.items.forEachIndexed { index, item ->
                appendLine("${index + 1}. ${escapeText(item.displayName)}")
                appendLine("   App: ${item.appKey.value}")
                item.preferredStoreId?.let { appendLine("   Store: ${it.value}") }
                if (item.tags.isNotEmpty()) {
                    appendLine("   Tags: ${item.tags.joinToString(", ")}")
                }
                item.note?.takeIf(String::isNotBlank)?.let {
                    appendLine("   Note: ${escapeText(it)}")
                }
                appendLine()
            }
        }.trimEnd() + "\n"
    }

    /** Detects JSON or plain text and decodes either format. */
    fun decode(encoded: String): ShareEnvelope {
        val trimmed = encoded.trimStart()
        return if (trimmed.startsWith("{")) {
            decodeJson(encoded)
        } else {
            decodeText(encoded)
        }
    }

    fun decodeText(encoded: String): ShareEnvelope {
        val lines = encoded.lineSequence().toList()
        var version: Int? = null
        var type: ShareType? = null
        var name: String? = null
        var description = ""
        var collectionId: String? = null
        var exportedAt: Long? = null
        var itemCount: Int? = null
        val items = mutableListOf<ShareItem>()

        var currentName: String? = null
        var currentKey: AppKey? = null
        var currentStore: StoreId? = null
        val currentTags = linkedSetOf<String>()
        var currentNote: String? = null

        fun flushItem() {
            val itemName = currentName ?: return
            val appKey = currentKey
                ?: throw malformed("A shared text item is missing its app key")
            items += ShareItem(
                appKey = appKey,
                displayName = itemName,
                preferredStoreId = currentStore,
                tags = currentTags.toSet(),
                note = currentNote,
            )
            currentName = null
            currentKey = null
            currentStore = null
            currentTags.clear()
            currentNote = null
        }

        for (rawLine in lines) {
            val line = rawLine.trim()
            if (line.isEmpty()) {
                flushItem()
                continue
            }
            val numbered = NUMBERED_ITEM.matchEntire(line)
            if (numbered != null) {
                flushItem()
                currentName = unescapeText(numbered.groupValues[1].trim())
                continue
            }
            val field = FIELD.matchEntire(line)
            if (currentName == null) {
                field ?: continue
                val key = field.groupValues[1].lowercase()
                val value = field.groupValues[2].trim()
                when (key) {
                    "schema" -> version = value.toIntOrNull()
                        ?: throw malformed("Invalid share schema version")
                    "type" -> type = typeFromWire(value)
                    "name" -> name = unescapeText(value)
                    "description" -> description = unescapeText(value)
                    "collection id" -> collectionId = unescapeText(value)
                    "exported at" -> exportedAt = value.toLongOrNull()
                    "items" -> itemCount = value.toIntOrNull()
                }
            } else {
                field ?: continue
                when (field.groupValues[1].lowercase()) {
                    "app" -> currentKey = AppKey.from(unescapeText(field.groupValues[2]))
                    "store" -> currentStore = StoreId(unescapeText(field.groupValues[2]))
                    "tags" -> currentTags += field.groupValues[2]
                        .split(',')
                        .map(String::trim)
                        .filter(String::isNotEmpty)
                    "note" -> currentNote = unescapeText(field.groupValues[2])
                }
            }
        }
        flushItem()

        val schema = version ?: throw malformed("Share text is missing its schema version")
        if (schema != SHARE_SCHEMA_VERSION) {
            throw unsupported(schema)
        }
        val shareType = type ?: throw malformed("Share text is missing its type")
        val shareName = name?.takeIf(String::isNotBlank)
            ?: throw malformed("Share text is missing its name")
        if (itemCount != null && itemCount != items.size) {
            throw malformed("Share text item count does not match its items")
        }

        return validate(
            ShareEnvelope(
                schemaVersion = schema,
                type = shareType,
                name = shareName,
                description = description,
                collectionId = collectionId,
                items = items,
                exportedAtEpochMillis = exportedAt,
            ),
        )
    }

    private fun validate(envelope: ShareEnvelope): ShareEnvelope {
        try {
            envelope.validate()
        } catch (exception: IllegalArgumentException) {
            throw malformed("Invalid shared list", exception)
        }
        return envelope
    }

    private fun typeFromWire(value: String): ShareType? =
        ShareType.entries.firstOrNull { type ->
            type.wireName().equals(value, ignoreCase = true) ||
                type.name.equals(value, ignoreCase = true)
        } ?: throw malformed("Unknown share type: $value")

    private fun malformed(message: String, cause: Throwable? = null) =
        ShareFormatException(ShareFormatReason.MALFORMED, message, cause)

    private fun unsupported(version: Int) = ShareFormatException(
        ShareFormatReason.UNSUPPORTED_VERSION,
        "Unsupported share schema version: $version",
    )

    private fun escapeText(value: String): String = value
        .replace("\\", "\\\\")
        .replace("\r", "")
        .replace("\n", "\\n")

    private fun unescapeText(value: String): String = buildString {
        var escaped = false
        value.forEach { character ->
            when {
                escaped -> {
                    append(
                        when (character) {
                            'n' -> '\n'
                            'r' -> '\r'
                            else -> character
                        },
                    )
                    escaped = false
                }

                character == '\\' -> escaped = true
                else -> append(character)
            }
        }
        if (escaped) append('\\')
    }

    private fun ShareType.wireName(): String = when (this) {
        ShareType.SINGLE_FAVOURITE -> "single_favourite"
        ShareType.COLLECTION -> "collection"
        ShareType.ALL_FAVOURITES -> "all_favourites"
    }

    private companion object {
        val NUMBERED_ITEM = Regex("""^\d+\.\s+(.+)$""")
        val FIELD = Regex("""^([^:]+):\s*(.*)$""")
    }
}
