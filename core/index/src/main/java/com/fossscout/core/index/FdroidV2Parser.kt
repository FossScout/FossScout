package com.fossscout.core.index

import java.io.File
import java.io.InputStream
import java.util.jar.JarFile
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

@Serializable
data class FdroidEntryFile(
    val name: String,
    val sha256: String,
    val size: Long,
    val numPackages: Int? = null,
) {
    init {
        require(name.isNotBlank()) { "Index file name is blank" }
        require(SHA256_PATTERN.matches(sha256.lowercase())) {
            "Index file hash is not SHA-256"
        }
        require(size >= 0) { "Index file size must not be negative" }
    }
}

private val SHA256_PATTERN = Regex("^[0-9a-f]{64}$")

@Serializable
data class FdroidEntry(
    val timestamp: Long,
    val version: Int,
    val maxAge: Int? = null,
    val index: FdroidEntryFile,
    val diffs: Map<String, FdroidEntryFile> = emptyMap(),
)

data class ParsedIndex(
    val packageCount: Int,
    val repoAddress: String? = null,
    val repoName: String? = null,
)

/**
 * Streaming reader/writer for the F-Droid v2 index and diff formats.
 *
 * A full index is walked one package at a time; each package value is small
 * enough to decode individually. Diff files are also small and are decoded into
 * a map keyed by package name before being merged with the previous normalized
 * payload.
 */
class FdroidV2Parser(
    private val json: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        isLenient = false
    },
) {
    fun readEntry(entryJar: File): FdroidEntry {
        val payload = JarFile(entryJar, false).use { jar ->
            val entry = jar.getJarEntry(JarIndexVerifier.ENTRY_FILE_NAME)
                ?: throw IndexSyncException(
                    SyncFailureKind.VERIFICATION,
                    "Signed entry does not contain ${JarIndexVerifier.ENTRY_FILE_NAME}",
                )
            jar.getInputStream(entry).use { input ->
                input.readBytes().toString(Charsets.UTF_8)
            }
        }
        return try {
            json.decodeFromString(FdroidEntry.serializer(), payload)
        } catch (exception: Exception) {
            throw IndexSyncException(
                SyncFailureKind.PARSE,
                "Signed entry is malformed",
                exception,
            )
        }
    }

    /** Parses a full index, invoking [onPackage] once per package. */
    suspend fun parseIndex(
        input: InputStream,
        onPackage: suspend (StorePackage) -> Unit,
    ): ParsedIndex = try {
        var packages = 0
        var repoAddress: String? = null
        var repoName: String? = null

        JsonStreamReader(input.reader(Charsets.UTF_8)).use { reader ->
            reader.beginObject()
            while (true) {
                val key = reader.nextObjectKey() ?: break
                when (key) {
                    "repo" -> {
                        val repo = parseObject(reader.captureValue())
                        repoAddress = repo.string("address")
                        repoName = repo.localizedMap("name")["en-US"]
                            ?: repo.localizedMap("name").values.firstOrNull()
                    }

                    "packages" -> {
                        reader.beginObject()
                        while (true) {
                            val packageName = reader.nextObjectKey() ?: break
                            val value = parseObject(reader.captureValue())
                            onPackage(toPackage(packageName, value))
                            packages++
                        }
                    }

                    else -> reader.skipValue()
                }
            }
            reader.expectEnd()
        }
        ParsedIndex(
            packageCount = packages,
            repoAddress = repoAddress,
            repoName = repoName,
        )
    } catch (cancellation: kotlinx.coroutines.CancellationException) {
        throw cancellation
    } catch (exception: IndexSyncException) {
        throw exception
    } catch (exception: Exception) {
        throw IndexSyncException(SyncFailureKind.PARSE, "Index is malformed", exception)
    }

    /** Parses a diff into package-name keyed patches; `null` means removal. */
    fun parseDiff(input: InputStream): Map<String, JsonObject?> = try {
        val diffs = linkedMapOf<String, JsonObject?>()
        JsonStreamReader(input.reader(Charsets.UTF_8)).use { reader ->
            reader.beginObject()
            while (true) {
                val key = reader.nextObjectKey() ?: break
                when (key) {
                    "packages" -> {
                        reader.beginObject()
                        while (true) {
                            val packageName = reader.nextObjectKey() ?: break
                            val raw = reader.captureValue()
                            diffs[packageName] = if (raw == "null") {
                                null
                            } else {
                                parseObject(raw)
                            }
                        }
                    }

                    else -> reader.skipValue()
                }
            }
            reader.expectEnd()
        }
        diffs
    } catch (cancellation: kotlinx.coroutines.CancellationException) {
        throw cancellation
    } catch (exception: IndexSyncException) {
        throw exception
    } catch (exception: Exception) {
        throw IndexSyncException(SyncFailureKind.PARSE, "Index diff is malformed", exception)
    }

    /** Applies one diff patch to an existing package. */
    fun applyDiff(previous: StorePackage, patch: JsonObject): StorePackage {
        val metadata = patch["metadata"]?.takeUnless { it is JsonNull }?.jsonObject
        val versions = patch["versions"]?.takeUnless { it is JsonNull }?.jsonObject

        val merged = if (metadata == null) {
            previous
        } else {
            previous.copy(
                name = mergeLocalized(previous.name, metadata["name"]),
                summary = mergeLocalized(previous.summary, metadata["summary"]),
                description = mergeLocalized(previous.description, metadata["description"]),
                categories = mergeStringList(previous.categories, metadata["categories"]),
                license = mergeString(previous.license, metadata["license"]),
                sourceCode = mergeString(previous.sourceCode, metadata["sourceCode"]),
                website = mergeString(previous.website, metadata["webSite"]),
                issueTracker = mergeString(previous.issueTracker, metadata["issueTracker"]),
                authorName = mergeString(previous.authorName, metadata["authorName"]),
                addedAtEpochMillis = mergeLong(previous.addedAtEpochMillis, metadata["added"]),
                lastUpdatedEpochMillis = mergeLong(
                    previous.lastUpdatedEpochMillis,
                    metadata["lastUpdated"],
                ),
                antiFeatures = mergeAntiFeatures(previous.antiFeatures, metadata["antiFeatures"]),
            )
        }

        if (versions == null) return merged

        val mergedVersions = merged.versions.associateBy(StorePackageVersion::fileSha256)
            .toMutableMap()
        versions.forEach { (hash, element) ->
            if (element is JsonNull) {
                mergedVersions.remove(hash)
            } else if (element is JsonObject) {
                mergedVersions[hash] = toVersion(hash, element)
            }
        }
        return merged.copy(
            versions = mergedVersions.values.sortedByDescending(StorePackageVersion::versionCode),
        )
    }

    fun encodePackage(packageRecord: StorePackage): String =
        json.encodeToString(StorePackage.serializer(), packageRecord)

    fun decodePackage(encoded: String): StorePackage =
        json.decodeFromString(StorePackage.serializer(), encoded)

    fun encodeEntryForTests(entry: FdroidEntry): String =
        json.encodeToString(FdroidEntry.serializer(), entry)

    private fun toPackage(packageName: String, value: JsonObject): StorePackage {
        val metadata = value["metadata"]?.takeUnless { it is JsonNull }?.jsonObject
            ?: JsonObject(emptyMap())
        val versions = value["versions"]?.takeUnless { it is JsonNull }?.jsonObject
            ?: JsonObject(emptyMap())

        return StorePackage(
            packageName = packageName,
            name = metadata.localizedMap("name"),
            summary = metadata.localizedMap("summary"),
            description = metadata.localizedMap("description"),
            categories = metadata.stringList("categories"),
            license = metadata.string("license"),
            sourceCode = metadata.string("sourceCode"),
            website = metadata.string("webSite"),
            issueTracker = metadata.string("issueTracker"),
            authorName = metadata.string("authorName"),
            addedAtEpochMillis = metadata.long("added"),
            lastUpdatedEpochMillis = metadata.long("lastUpdated"),
            antiFeatures = metadata.antiFeatures(),
            versions = versions.mapNotNull { (hash, element) ->
                (element as? JsonObject)?.let { toVersion(hash, it) }
            }.sortedByDescending(StorePackageVersion::versionCode),
        )
    }

    private fun toVersion(hash: String, value: JsonObject): StorePackageVersion {
        val file = value["file"]?.takeUnless { it is JsonNull }?.jsonObject
        val manifest = value["manifest"]?.takeUnless { it is JsonNull }?.jsonObject
            ?: JsonObject(emptyMap())
        val usesSdk = manifest["usesSdk"]?.takeUnless { it is JsonNull }?.jsonObject
        val signer = manifest["signer"]?.takeUnless { it is JsonNull }?.jsonObject

        return StorePackageVersion(
            fileSha256 = file?.string("sha256")?.lowercase() ?: hash.lowercase(),
            versionCode = manifest.long("versionCode") ?: 0L,
            versionName = manifest.string("versionName").orEmpty(),
            fileName = file?.string("name"),
            sizeBytes = file?.long("size"),
            addedEpochMillis = value.long("added"),
            minSdk = usesSdk?.int("minSdkVersion"),
            targetSdk = usesSdk?.int("targetSdkVersion"),
            signerSha256 = signer?.stringList("sha256").orEmpty().map(String::lowercase),
            nativeCode = manifest.stringList("nativecode"),
            permissions = manifest["usesPermission"]
                ?.takeUnless { it is JsonNull }
                ?.jsonArray
                ?.mapNotNull { element ->
                    (element as? JsonObject)?.string("name")
                }
                .orEmpty(),
        )
    }

    private fun parseObject(raw: String): JsonObject = try {
        json.parseToJsonElement(raw).jsonObject
    } catch (exception: Exception) {
        throw IndexSyncException(
            SyncFailureKind.PARSE,
            "Index package entry is malformed",
            exception,
        )
    }

    private fun mergeLocalized(
        previous: Map<String, String>,
        element: JsonElement?,
    ): Map<String, String> {
        if (element == null) return previous
        if (element is JsonNull) return emptyMap()
        if (element !is JsonObject) return previous
        val merged = previous.toMutableMap()
        element.forEach { (locale, value) ->
            if (value is JsonNull) {
                merged.remove(locale)
            } else {
                (value as? JsonPrimitive)?.contentOrNull?.let { merged[locale] = it }
            }
        }
        return merged
    }

    private fun mergeAntiFeatures(
        previous: Map<String, Map<String, String>>,
        element: JsonElement?,
    ): Map<String, Map<String, String>> {
        if (element == null) return previous
        if (element is JsonNull) return emptyMap()
        if (element !is JsonObject) return previous
        val merged = previous.toMutableMap()
        element.forEach { (feature, value) ->
            if (value is JsonNull) {
                merged.remove(feature)
            } else if (value is JsonObject) {
                merged[feature] = mergeLocalized(merged[feature].orEmpty(), value)
            }
        }
        return merged
    }

    private fun mergeString(previous: String?, element: JsonElement?): String? = when {
        element == null -> previous
        element is JsonNull -> null
        else -> (element as? JsonPrimitive)?.contentOrNull ?: previous
    }

    private fun mergeLong(previous: Long?, element: JsonElement?): Long? = when {
        element == null -> previous
        element is JsonNull -> null
        else -> (element as? JsonPrimitive)?.longOrNull ?: previous
    }

    private fun mergeStringList(
        previous: List<String>,
        element: JsonElement?,
    ): List<String> = when {
        element == null -> previous
        element is JsonNull -> emptyList()
        element is JsonArray -> element.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
        else -> previous
    }

    private fun JsonObject.localizedMap(key: String): Map<String, String> =
        this[key]
            ?.takeUnless { it is JsonNull }
            ?.jsonObject
            ?.asLocalizedMap()
            .orEmpty()

    private fun JsonObject.asLocalizedMap(): Map<String, String> =
        mapNotNull { (locale, value) ->
            (value as? JsonPrimitive)?.contentOrNull?.let { locale to it }
        }.toMap()

    private fun JsonObject.antiFeatures(): Map<String, Map<String, String>> =
        this["antiFeatures"]
            ?.takeUnless { it is JsonNull }
            ?.jsonObject
            ?.mapNotNull { (feature, value) ->
                (value as? JsonObject)?.let { feature to it.asLocalizedMap() }
            }
            ?.toMap()
            .orEmpty()

    private fun JsonObject.string(key: String): String? =
        (this[key] as? JsonPrimitive)?.contentOrNull

    private fun JsonObject.long(key: String): Long? =
        (this[key] as? JsonPrimitive)?.longOrNull

    private fun JsonObject.int(key: String): Int? =
        (this[key] as? JsonPrimitive)?.intOrNull

    private fun JsonObject.stringList(key: String): List<String> =
        (this[key] as? JsonArray)
            ?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
            .orEmpty()
}
