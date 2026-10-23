package com.fossscout.core.inventory

class FakeInstalledAppSource(
    var apps: List<InstalledApp> = emptyList(),
    override var supportsFullScan: Boolean = true,
    var findError: Throwable? = null,
    var searchError: Throwable? = null,
    var enumerateError: Throwable? = null,
    var inaccessibleApps: Int = 0,
) : InstalledAppSource {
    var findCalls: Int = 0
        private set
    var searchCalls: Int = 0
        private set
    var enumerateCalls: Int = 0
        private set

    override suspend fun find(packageName: String): InstalledApp? {
        findCalls += 1
        findError?.let { throw it }
        return apps.firstOrNull { it.packageName == packageName }
    }

    override suspend fun search(query: String, limit: Int): List<InstalledApp> {
        searchCalls += 1
        searchError?.let { throw it }
        return apps
            .filter { app ->
                app.packageName.contains(query, ignoreCase = true) ||
                    app.label.contains(query, ignoreCase = true)
            }
            .take(limit)
    }

    override suspend fun enumerate(): InventoryEnumeration {
        enumerateCalls += 1
        enumerateError?.let { throw it }
        return InventoryEnumeration(apps = apps, inaccessibleApps = inaccessibleApps)
    }
}
