package com.fossscout.core.inventory

import kotlinx.serialization.Serializable

const val INVENTORY_SCHEMA_VERSION = 1

/** How the entries in a snapshot were produced. */
@Serializable
enum class ScanMode { MANUAL, FULL }

/**
 * The four states required by F01-R4. A missing store or index entry is never
 * enough to claim [PROPRIETARY]; S05 owns the real classifier.
 */
@Serializable
enum class Classification { FOSS, PROPRIETARY, UNKNOWN, UNAVAILABLE }

/** Whether a completed scan saw every package it expected to see. */
@Serializable
enum class InventoryStatus { COMPLETE, PARTIAL }

/**
 * Local-only identity of the Android user/profile an app was observed under.
 * Values are best effort: the platform does not always expose a profile label.
 */
@Serializable
data class AppProfile(
    val userId: Int? = null,
    val label: String? = null,
    val isWorkProfile: Boolean = false,
)

/**
 * An installed application as reported by the platform. Optional fields stay
 * null when the platform or package visibility rules do not expose them.
 */
@Serializable
data class InstalledApp(
    val packageName: String,
    val label: String,
    val versionName: String? = null,
    val versionCode: Long = 0L,
    val installerPackage: String? = null,
    val signerSha256: String? = null,
    val isSystemApp: Boolean = false,
    val isEnabled: Boolean = true,
    val profile: AppProfile = AppProfile(),
) {
    init {
        require(packageName.isNotBlank()) { "Package name is blank" }
        require(label.isNotBlank()) { "Label is blank" }
    }
}

/**
 * One audited app. [identity] is the package name for installed apps and the
 * user's lookup text when the package is not installed or not visible.
 */
@Serializable
data class InventoryEntry(
    val identity: String,
    val app: InstalledApp? = null,
    val classification: Classification = Classification.UNKNOWN,
    val explanation: String? = null,
) {
    init {
        require(identity.isNotBlank()) { "Entry identity is blank" }
    }

    val packageName: String? get() = app?.packageName
    val label: String get() = app?.label ?: identity
}

/** Coverage information for a completed scan. */
@Serializable
data class InventoryCoverage(
    val visibleApps: Int = 0,
    val scannedApps: Int = 0,
    val inaccessibleApps: Int = 0,
) {
    val isPartial: Boolean get() = inaccessibleApps > 0
}

/**
 * A complete inventory result. A snapshot is only persisted after the whole
 * scan finished, so cancellation leaves the previous snapshot untouched.
 */
@Serializable
data class InventorySnapshot(
    val scanMode: ScanMode,
    val capturedAtEpochMillis: Long,
    val entries: List<InventoryEntry> = emptyList(),
    val status: InventoryStatus = InventoryStatus.COMPLETE,
    val coverage: InventoryCoverage = InventoryCoverage(),
    val schemaVersion: Int = INVENTORY_SCHEMA_VERSION,
) {
    fun isStale(
        nowEpochMillis: Long,
        staleAfterMillis: Long = DEFAULT_STALE_AFTER_MILLIS,
    ): Boolean = nowEpochMillis - capturedAtEpochMillis > staleAfterMillis
}

/** Persisted local inventory state: the last complete snapshot and hide list. */
@Serializable
data class InventoryState(
    val snapshot: InventorySnapshot? = null,
    val hiddenPackages: Set<String> = emptySet(),
)

/** Filter selections that only change the visible list, never rescan. */
data class InventoryFilters(
    val includeSystemApps: Boolean = true,
    val includeDisabledApps: Boolean = true,
    val includeWorkProfileApps: Boolean = true,
    val includeHiddenApps: Boolean = false,
)

fun InventorySnapshot.filteredEntries(
    filters: InventoryFilters,
    hiddenPackages: Set<String>,
): List<InventoryEntry> = entries.filter { entry ->
    val app = entry.app
    when {
        app == null -> true
        !filters.includeSystemApps && app.isSystemApp -> false
        !filters.includeDisabledApps && !app.isEnabled -> false
        !filters.includeWorkProfileApps && app.profile.isWorkProfile -> false
        !filters.includeHiddenApps && app.packageName in hiddenPackages -> false
        else -> true
    }
}

/** Default: data older than one day is visibly marked as stale. */
const val DEFAULT_STALE_AFTER_MILLIS: Long = 24L * 60L * 60L * 1_000L
