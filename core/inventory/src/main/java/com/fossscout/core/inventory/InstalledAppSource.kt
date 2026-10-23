package com.fossscout.core.inventory

/** Result of enumerating installed packages. */
data class InventoryEnumeration(
    val apps: List<InstalledApp> = emptyList(),
    val inaccessibleApps: Int = 0,
)

/**
 * Platform-independent inventory source. The Play profile uses [find] and
 * [search] for manual checks; profiles with broad visibility can also call
 * [enumerate]. Implementations must degrade to null/empty when package
 * visibility hides an app instead of throwing.
 */
interface InstalledAppSource {
    /** Whether this source is allowed and able to enumerate every package. */
    val supportsFullScan: Boolean

    /** Look up one package, or null when it is not installed or not visible. */
    suspend fun find(packageName: String): InstalledApp?

    /** Find visible apps by package name or label. Never enumerates broadly. */
    suspend fun search(query: String, limit: Int = 20): List<InstalledApp>

    /** Enumerate installed apps. Only valid when [supportsFullScan] is true. */
    suspend fun enumerate(): InventoryEnumeration
}
