package com.fossscout.core.inventory

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Orchestrates local inventory scans. A snapshot is saved only after a scan
 * finishes, so a cancelled full scan leaves the previous complete snapshot in
 * place. No operation performs a network request.
 */
class InventoryRepository(
    private val store: InventoryStore,
    private val source: InstalledAppSource,
    private val classifier: ClassificationSource = UnknownClassificationSource,
    private val allowFullScan: Boolean = source.supportsFullScan,
    private val now: () -> Long = System::currentTimeMillis,
    private val staleAfterMillis: Long = DEFAULT_STALE_AFTER_MILLIS,
) {
    private val mutex = Mutex()

    val state: Flow<InventoryState> = store.state
    val snapshot: Flow<InventorySnapshot?> = state.map { it.snapshot }
    val hiddenPackages: Flow<Set<String>> = state.map { it.hiddenPackages }

    /** Full scan requires both the profile capability and a capable source. */
    val canFullScan: Boolean = allowFullScan && source.supportsFullScan

    fun isStale(snapshot: InventorySnapshot): Boolean =
        snapshot.isStale(now(), staleAfterMillis)

    /** Visible apps matching a label or package fragment. */
    suspend fun search(query: String, limit: Int = 20): List<InstalledApp> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return emptyList()
        return source.search(trimmed, limit)
    }

    /** Manual check of one package; unavailable/unknown results are persisted. */
    suspend fun checkPackage(packageName: String): InventorySnapshot = mutex.withLock {
        val identity = packageName.trim()
        require(identity.isNotEmpty()) { "Package name is blank" }
        val app = findSafely(identity)
        val entry = if (app == null) {
            unavailableEntry(identity)
        } else {
            classifySafely(app)
        }
        persist(
            InventorySnapshot(
                scanMode = ScanMode.MANUAL,
                capturedAtEpochMillis = now(),
                entries = listOf(entry),
                status = if (app == null) {
                    InventoryStatus.PARTIAL
                } else {
                    InventoryStatus.COMPLETE
                },
                coverage = InventoryCoverage(
                    visibleApps = if (app == null) 0 else 1,
                    scannedApps = if (app == null) 0 else 1,
                    inaccessibleApps = if (app == null) 1 else 0,
                ),
            ),
        )
    }

    /** Record a lookup that could not even be resolved to a visible package. */
    suspend fun recordUnavailable(lookup: String): InventorySnapshot = mutex.withLock {
        val identity = lookup.trim()
        require(identity.isNotEmpty()) { "Lookup is blank" }
        persist(
            InventorySnapshot(
                scanMode = ScanMode.MANUAL,
                capturedAtEpochMillis = now(),
                entries = listOf(unavailableEntry(identity)),
                status = InventoryStatus.PARTIAL,
                coverage = InventoryCoverage(scannedApps = 0, inaccessibleApps = 1),
            ),
        )
    }

    /**
     * Enumerate every visible app. Only call when [canFullScan] is true.
     * Cancellation propagates without writing a snapshot.
     */
    suspend fun scanFull(): InventorySnapshot {
        check(canFullScan) { "Full scan is not available in this profile" }
        return mutex.withLock {
            val enumeration = source.enumerate()
            currentCoroutineContext().ensureActive()
            val entries = enumeration.apps.map { classifySafely(it) }
            currentCoroutineContext().ensureActive()
            val coverage = InventoryCoverage(
                visibleApps = enumeration.apps.size,
                scannedApps = enumeration.apps.size,
                inaccessibleApps = enumeration.inaccessibleApps,
            )
            persist(
                InventorySnapshot(
                    scanMode = ScanMode.FULL,
                    capturedAtEpochMillis = now(),
                    entries = entries,
                    status = if (coverage.isPartial) {
                        InventoryStatus.PARTIAL
                    } else {
                        InventoryStatus.COMPLETE
                    },
                    coverage = coverage,
                ),
            )
        }
    }

    suspend fun hide(packageName: String) = mutex.withLock {
        val identity = packageName.trim()
        require(identity.isNotEmpty()) { "Package name is blank" }
        val current = store.state.first()
        if (identity !in current.hiddenPackages) {
            store.save(current.copy(hiddenPackages = current.hiddenPackages + identity))
        }
    }

    suspend fun unhide(packageName: String) = mutex.withLock {
        val identity = packageName.trim()
        val current = store.state.first()
        if (identity in current.hiddenPackages) {
            store.save(current.copy(hiddenPackages = current.hiddenPackages - identity))
        }
    }

    private suspend fun persist(snapshot: InventorySnapshot): InventorySnapshot {
        val current = store.state.first()
        store.save(current.copy(snapshot = snapshot))
        return snapshot
    }

    private suspend fun findSafely(packageName: String): InstalledApp? = try {
        source.find(packageName)
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (_: Exception) {
        null
    }

    private suspend fun classifySafely(app: InstalledApp): InventoryEntry = try {
        val result = classifier.classify(app)
        InventoryEntry(
            identity = app.packageName,
            app = app,
            classification = result.classification,
            explanation = result.explanation,
        )
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (_: Exception) {
        InventoryEntry(
            identity = app.packageName,
            app = app,
            classification = Classification.UNKNOWN,
        )
    }

    private fun unavailableEntry(identity: String): InventoryEntry = InventoryEntry(
        identity = identity,
        classification = Classification.UNAVAILABLE,
    )
}
