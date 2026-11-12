package com.fossscout.core.matching

import java.io.InputStream
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

/** Loads the bundled tracker snapshot without network access. */
object TrackerDatasetLoader {
    const val BUNDLED_PATH: String = "/trackers/exodus-etip.json"

    private val json = Json {
        ignoreUnknownKeys = true
    }

    fun decode(text: String): TrackerDataset = json.decodeFromString(text)

    fun load(input: InputStream): TrackerDataset = input.use { stream ->
        decode(stream.readBytes().decodeToString())
    }

    /** Returns null when the bundled resource is absent or cannot be parsed. */
    fun loadBundled(): TrackerDataset? = runCatching {
        TrackerDatasetLoader::class.java
            .getResourceAsStream(BUNDLED_PATH)
            ?.let(::load)
    }.getOrNull()
}
