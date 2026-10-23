package com.fossscout.core.inventory

import org.junit.Assert.assertEquals
import org.junit.Test

class InventoryFiltersTest {
    private val normal = entry("org.example.normal", InstalledApp("org.example.normal", "Normal"))
    private val system = entry(
        "org.example.system",
        InstalledApp("org.example.system", "System", isSystemApp = true),
    )
    private val disabled = entry(
        "org.example.disabled",
        InstalledApp("org.example.disabled", "Disabled", isEnabled = false),
    )
    private val work = entry(
        "org.example.work",
        InstalledApp(
            "org.example.work",
            "Work",
            profile = AppProfile(userId = 10, isWorkProfile = true),
        ),
    )
    private val unavailable = InventoryEntry("Some missing app", classification = Classification.UNAVAILABLE)

    private val snapshot = InventorySnapshot(
        scanMode = ScanMode.FULL,
        capturedAtEpochMillis = 1L,
        entries = listOf(normal, system, disabled, work, unavailable),
    )

    @Test
    fun `default filters keep every entry including unavailable lookups`() {
        val visible = snapshot.filteredEntries(InventoryFilters(), emptySet())

        assertEquals(listOf(normal, system, disabled, work, unavailable), visible)
    }

    @Test
    fun `system apps can be excluded`() {
        val visible = snapshot.filteredEntries(
            InventoryFilters(includeSystemApps = false),
            emptySet(),
        )

        assertEquals(listOf(normal, disabled, work, unavailable), visible)
    }

    @Test
    fun `disabled apps can be excluded`() {
        val visible = snapshot.filteredEntries(
            InventoryFilters(includeDisabledApps = false),
            emptySet(),
        )

        assertEquals(listOf(normal, system, work, unavailable), visible)
    }

    @Test
    fun `work-profile apps can be excluded`() {
        val visible = snapshot.filteredEntries(
            InventoryFilters(includeWorkProfileApps = false),
            emptySet(),
        )

        assertEquals(listOf(normal, system, disabled, unavailable), visible)
    }

    @Test
    fun `hidden apps are excluded unless hidden entries are shown`() {
        val hidden = setOf(normal.identity)

        assertEquals(
            listOf(system, disabled, work, unavailable),
            snapshot.filteredEntries(InventoryFilters(), hidden),
        )
        assertEquals(
            listOf(normal, system, disabled, work, unavailable),
            snapshot.filteredEntries(
                InventoryFilters(includeHiddenApps = true),
                hidden,
            ),
        )
    }

    private fun entry(packageName: String, app: InstalledApp) = InventoryEntry(
        identity = packageName,
        app = app,
        classification = Classification.UNKNOWN,
    )
}
