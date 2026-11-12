package com.fossscout.core.matching

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

const val PERMISSION_COMPARISON_AUDIT_SCHEMA_VERSION: Int = 1

/**
 * A local, shareable report of one permission comparison. It contains the
 * evidence shown on screen, not a privacy score or a runtime tracking claim.
 */
@Serializable
data class PermissionComparisonAudit(
    val schemaVersion: Int = PERMISSION_COMPARISON_AUDIT_SCHEMA_VERSION,
    val exportedAtEpochMillis: Long,
    val exportedAtUtc: String,
    val sources: PermissionComparisonAuditSources,
    val installed: PermissionComparisonAuditSet,
    val alternative: PermissionComparisonAuditSet,
    val relationship: String,
    val diff: PermissionComparisonAuditDiff? = null,
    val alternativeManifest: AlternativeApkManifest? = null,
    val tracker: TrackerComparisonAudit,
    val limitations: List<String> = emptyList(),
)

/** Stable source identifiers and links for the two comparison sides. */
@Serializable
data class PermissionComparisonAuditSources(
    val installed: String = "android_package_manager_and_local_apk_dex",
    val alternative: String = "cached_store_index",
    val alternativeUrl: String? = null,
)

/** One side of the comparison as recorded in the audit report. */
@Serializable
data class PermissionComparisonAuditSet(
    val label: String,
    val packageName: String,
    val versionName: String? = null,
    val storeName: String? = null,
    val permissionsKnown: Boolean,
    val permissions: List<PermissionComparisonAuditPermission>,
)

/** One permission with the readable labels shown in the comparison. */
@Serializable
data class PermissionComparisonAuditPermission(
    val name: String,
    val displayName: String,
    val category: String,
    val explanation: String? = null,
)

/** Permission names grouped exactly as shown in the comparison. */
@Serializable
data class PermissionComparisonAuditDiff(
    val installedOnly: List<String>,
    val alternativeOnly: List<String>,
    val shared: List<String>,
)

/** Tracker evidence and its dataset provenance, or an explicit no-result state. */
@Serializable
data class TrackerComparisonAudit(
    val dataset: TrackerDatasetAudit? = null,
    val scanStatus: String,
    val scanInProgress: Boolean,
    val scannedFiles: Int,
    val scannedBytes: Long,
    val matches: List<TrackerComparisonAuditMatch>,
    val limitation: String,
)

/** Dataset identity, license, and attribution copied into the report. */
@Serializable
data class TrackerDatasetAudit(
    val name: String,
    val version: String,
    val licenseName: String,
    val licenseUrl: String,
    val contentsLicenseUrl: String? = null,
    val sourceUrl: String,
    val retrievedAt: String? = null,
    val attribution: String? = null,
)

/** One static tracker-signature match, with the exact signatures found. */
@Serializable
data class TrackerComparisonAuditMatch(
    val name: String,
    val categories: List<String>,
    val website: String? = null,
    val confidence: String,
    val matchedSignatures: List<String>,
)

/** Builds and encodes the local comparison audit without any network access. */
class PermissionComparisonAuditBuilder(
    private val json: Json = Json {
        prettyPrint = true
        encodeDefaults = true
    },
) {
    fun build(
        comparison: PermissionComparison,
        exportedAtEpochMillis: Long,
    ): PermissionComparisonAudit {
        require(exportedAtEpochMillis >= 0) {
            "Exported timestamp must not be negative"
        }
        return PermissionComparisonAudit(
            exportedAtEpochMillis = exportedAtEpochMillis,
            exportedAtUtc = formatUtc(exportedAtEpochMillis),
            sources = PermissionComparisonAuditSources(
                alternative = if (comparison.alternativeManifest != null) {
                    "selected_local_apk_manifest"
                } else {
                    "cached_store_index"
                },
                alternativeUrl = comparison.alternativeSourceUrl,
            ),
            installed = comparison.installed.toAuditSet(),
            alternative = comparison.alternative.toAuditSet(),
            relationship = comparison.relationship.name,
            diff = comparison.diff?.toAuditDiff(),
            alternativeManifest = comparison.alternativeManifest,
            tracker = comparison.tracker.toAudit(
                scanInProgress = comparison.trackerScanInProgress,
            ),
            limitations = if (comparison.alternativeManifest != null) {
                LIMITATIONS + MANIFEST_LIMITATION
            } else {
                LIMITATIONS
            },
        )
    }

    fun encode(audit: PermissionComparisonAudit): String =
        json.encodeToString(PermissionComparisonAudit.serializer(), audit)

    private fun PermissionSet.toAuditSet(): PermissionComparisonAuditSet =
        PermissionComparisonAuditSet(
            label = label,
            packageName = packageName,
            versionName = versionName,
            storeName = storeName,
            permissionsKnown = permissionsKnown,
            permissions = permissions.map { permission ->
                PermissionComparisonAuditPermission(
                    name = permission.name,
                    displayName = permission.displayName,
                    category = permission.category.name,
                    explanation = permission.explanation,
                )
            },
        )

    private fun PermissionDiff.toAuditDiff(): PermissionComparisonAuditDiff =
        PermissionComparisonAuditDiff(
            installedOnly = installedOnly.map(PermissionDetail::name),
            alternativeOnly = alternativeOnly.map(PermissionDetail::name),
            shared = shared.map(PermissionDetail::name),
        )

    private fun TrackerReport?.toAudit(scanInProgress: Boolean): TrackerComparisonAudit {
        val report = this
        val installed = report?.installed
        return TrackerComparisonAudit(
            dataset = report?.dataset?.toAuditDataset(),
            scanStatus = when {
                scanInProgress -> "IN_PROGRESS"
                installed == null -> "NOT_RUN"
                else -> installed.status.name
            },
            scanInProgress = scanInProgress,
            scannedFiles = installed?.scannedFiles ?: 0,
            scannedBytes = installed?.scannedBytes ?: 0L,
            matches = installed?.matches.orEmpty().map { it.toAuditMatch() },
            limitation = TRACKER_LIMITATION,
        )
    }

    private fun TrackerDatasetInfo.toAuditDataset(): TrackerDatasetAudit =
        TrackerDatasetAudit(
            name = name,
            version = version,
            licenseName = licenseName,
            licenseUrl = licenseUrl,
            contentsLicenseUrl = contentsLicenseUrl,
            sourceUrl = sourceUrl,
            retrievedAt = retrievedAt,
            attribution = attribution,
        )

    private fun TrackerMatch.toAuditMatch(): TrackerComparisonAuditMatch =
        TrackerComparisonAuditMatch(
            name = name,
            categories = categories,
            website = website,
            confidence = confidence.name,
            matchedSignatures = matchedSignatures,
        )

    private fun formatUtc(epochMillis: Long): String =
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }.format(Date(epochMillis))

    private companion object {
        const val TRACKER_LIMITATION =
            "A DEX signature match is static evidence that a class is present, " +
                "not proof that it runs or sends data. A missing match is not " +
                "proof that the app has no trackers."

        const val MANIFEST_LIMITATION =
            "The selected APK manifest was read locally; the APK was not installed, " +
                "executed, or signature-verified."

        val LIMITATIONS = listOf(
            "The store alternative was not downloaded or scanned.",
            "Tracker results apply only to the installed app.",
            TRACKER_LIMITATION,
            "No permission or tracker data is uploaded by this export.",
        )
    }
}
