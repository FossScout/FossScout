package com.fossscout.core.matching

import kotlinx.serialization.Serializable

/** Schema version for the bundled tracker-signature snapshot. */
const val TRACKER_DATASET_SCHEMA_VERSION: Int = 1

/** Attribution and licensing for a bundled tracker dataset. */
@Serializable
data class TrackerDatasetInfo(
    val name: String,
    val version: String,
    val licenseName: String,
    val licenseUrl: String,
    val contentsLicenseUrl: String? = null,
    val sourceUrl: String,
    val retrievedAt: String? = null,
    val attribution: String? = null,
)

/**
 * A versioned tracker dataset. Signatures are class-name prefixes in dot form;
 * [TrackerSignatureMatcher] converts them to DEX descriptor form at runtime.
 */
@Serializable
data class TrackerDataset(
    val schemaVersion: Int = TRACKER_DATASET_SCHEMA_VERSION,
    val dataset: TrackerDatasetInfo,
    val trackers: List<TrackerDefinition> = emptyList(),
)

@Serializable
data class TrackerDefinition(
    val name: String,
    val categories: List<String> = emptyList(),
    val website: String? = null,
    val signatures: List<TrackerSignature> = emptyList(),
)

@Serializable
data class TrackerSignature(
    val value: String,
    val confidence: TrackerConfidence,
)

/**
 * Confidence in the dataset entry, not in runtime behaviour. KNOWN entries are
 * in the Exodus tracker list; CANDIDATE entries come from ETIP but are not
 * listed by Exodus or are marked for rework.
 */
@Serializable
enum class TrackerConfidence {
    KNOWN,
    CANDIDATE,
}

/** One tracker whose code signature was found in the scanned DEX files. */
data class TrackerMatch(
    val name: String,
    val categories: List<String> = emptyList(),
    val website: String? = null,
    val confidence: TrackerConfidence = TrackerConfidence.CANDIDATE,
    val matchedSignatures: List<String> = emptyList(),
)

enum class TrackerScanStatus {
    COMPLETE,
    TRUNCATED,
    UNAVAILABLE,
}

/** Local result of scanning one installed app's DEX files. */
data class TrackerScanResult(
    val status: TrackerScanStatus = TrackerScanStatus.UNAVAILABLE,
    val matches: List<TrackerMatch> = emptyList(),
    val scannedFiles: Int = 0,
    val scannedBytes: Long = 0L,
)

/** Tracker evidence attached to a permission comparison. */
data class TrackerReport(
    val dataset: TrackerDatasetInfo?,
    val installed: TrackerScanResult,
)

/** Resolves and scans the selected installed app; implementations stay local. */
interface TrackerScanSource {
    val datasetInfo: TrackerDatasetInfo?

    suspend fun scan(packageName: String): TrackerScanResult
}
