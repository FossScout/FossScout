package com.fossscout.core.inventory

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InventoryRepositoryTest {
    private val example = installedApp("org.example.app", "Example")
    private val other = installedApp("org.example.other", "Other")

    @Test
    fun `manual check persists a one-app snapshot with its timestamp`() = runTest {
        val store = FakeInventoryStore()
        val source = FakeInstalledAppSource(apps = listOf(example))
        val repository = InventoryRepository(store = store, source = source, now = { 1234L })

        val snapshot = repository.checkPackage("org.example.app")

        assertEquals(ScanMode.MANUAL, snapshot.scanMode)
        assertEquals(1234L, snapshot.capturedAtEpochMillis)
        assertEquals(1, snapshot.entries.size)
        assertEquals(Classification.UNKNOWN, snapshot.entries.single().classification)
        assertEquals(example, snapshot.entries.single().app)
        assertEquals(snapshot, store.current().snapshot)
        assertEquals(1, store.saveCalls)
    }

    @Test
    fun `manual check of a hidden or missing package records unavailable`() = runTest {
        val store = FakeInventoryStore()
        val repository = InventoryRepository(
            store = store,
            source = FakeInstalledAppSource(),
            now = { 10L },
        )

        val snapshot = repository.checkPackage("org.example.gone")

        assertEquals(ScanMode.MANUAL, snapshot.scanMode)
        assertNull(snapshot.entries.single().app)
        assertEquals(Classification.UNAVAILABLE, snapshot.entries.single().classification)
        assertEquals(1, snapshot.coverage.inaccessibleApps)
        assertEquals(snapshot, store.current().snapshot)
    }

    @Test
    fun `manual check degrades instead of crashing when package visibility blocks`() = runTest {
        val store = FakeInventoryStore()
        val source = FakeInstalledAppSource(findError = SecurityException("not visible"))
        val repository = InventoryRepository(store = store, source = source, now = { 10L })

        val snapshot = repository.checkPackage("org.example.secret")

        assertEquals(Classification.UNAVAILABLE, snapshot.entries.single().classification)
        assertEquals(snapshot, store.current().snapshot)
    }

    @Test
    fun `manual check records a free-text lookup that could not be resolved`() = runTest {
        val store = FakeInventoryStore()
        val repository = InventoryRepository(
            store = store,
            source = FakeInstalledAppSource(),
            now = { 10L },
        )

        val snapshot = repository.recordUnavailable("Some App")

        assertEquals("Some App", snapshot.entries.single().identity)
        assertEquals(Classification.UNAVAILABLE, snapshot.entries.single().classification)
        assertEquals(snapshot, store.current().snapshot)
    }

    @Test
    fun `classifier labels are copied into entries`() = runTest {
        val store = FakeInventoryStore()
        val repository = InventoryRepository(
            store = store,
            source = FakeInstalledAppSource(apps = listOf(example)),
            classifier = object : ClassificationSource {
                override suspend fun classify(app: InstalledApp) =
                    ClassificationResult(Classification.PROPRIETARY, "Known vendor")
            },
            now = { 10L },
        )

        val snapshot = repository.checkPackage(example.packageName)

        assertEquals(Classification.PROPRIETARY, snapshot.entries.single().classification)
        assertEquals("Known vendor", snapshot.entries.single().explanation)
    }

    @Test
    fun `classifier failure falls back to unknown`() = runTest {
        val store = FakeInventoryStore()
        val repository = InventoryRepository(
            store = store,
            source = FakeInstalledAppSource(apps = listOf(example)),
            classifier = object : ClassificationSource {
                override suspend fun classify(app: InstalledApp): ClassificationResult =
                    throw IllegalStateException("classifier offline")
            },
            now = { 10L },
        )

        val snapshot = repository.checkPackage(example.packageName)

        assertEquals(Classification.UNKNOWN, snapshot.entries.single().classification)
    }

    @Test
    fun `full scan persists every app and reports partial coverage`() = runTest {
        val store = FakeInventoryStore()
        val source = FakeInstalledAppSource(
            apps = listOf(example, other),
            inaccessibleApps = 2,
        )
        val repository = InventoryRepository(
            store = store,
            source = source,
            allowFullScan = true,
            now = { 999L },
        )

        val snapshot = repository.scanFull()

        assertEquals(ScanMode.FULL, snapshot.scanMode)
        assertEquals(999L, snapshot.capturedAtEpochMillis)
        assertEquals(listOf(example, other), snapshot.entries.map { it.app })
        assertEquals(InventoryStatus.PARTIAL, snapshot.status)
        assertEquals(InventoryCoverage(2, 2, 2), snapshot.coverage)
        assertEquals(snapshot, store.current().snapshot)
        assertEquals(1, source.enumerateCalls)
    }

    @Test
    fun `full scan is rejected when the profile lacks broad visibility`() = runTest {
        val store = FakeInventoryStore()
        val source = FakeInstalledAppSource(apps = listOf(example), supportsFullScan = false)
        val repository = InventoryRepository(
            store = store,
            source = source,
            allowFullScan = false,
            now = { 1L },
        )

        val failure = runCatching { repository.scanFull() }.exceptionOrNull()

        assertTrue(failure is IllegalStateException)
        assertFalse(repository.canFullScan)
        assertEquals(0, source.enumerateCalls)
        assertEquals(0, store.saveCalls)
        assertNull(store.current().snapshot)
    }

    @Test
    fun `cancelled full scan preserves the last complete snapshot`() = runTest {
        val previous = InventorySnapshot(
            scanMode = ScanMode.FULL,
            capturedAtEpochMillis = 100L,
            entries = listOf(InventoryEntry(example.packageName, example, Classification.FOSS)),
        )
        val store = FakeInventoryStore(InventoryState(snapshot = previous))
        val source = FakeInstalledAppSource(
            apps = listOf(example),
            enumerateError = CancellationException("user cancelled"),
        )
        val repository = InventoryRepository(
            store = store,
            source = source,
            allowFullScan = true,
            now = { 200L },
        )

        val failure = runCatching { repository.scanFull() }.exceptionOrNull()

        assertTrue(failure is CancellationException)
        assertEquals(previous, store.current().snapshot)
        assertEquals(0, store.saveCalls)
    }

    @Test
    fun `cancelled classification preserves the last complete snapshot`() = runTest {
        val previous = InventorySnapshot(
            scanMode = ScanMode.FULL,
            capturedAtEpochMillis = 100L,
            entries = listOf(InventoryEntry(example.packageName, example, Classification.FOSS)),
        )
        val store = FakeInventoryStore(InventoryState(snapshot = previous))
        val repository = InventoryRepository(
            store = store,
            source = FakeInstalledAppSource(apps = listOf(example)),
            classifier = object : ClassificationSource {
                override suspend fun classify(app: InstalledApp): ClassificationResult =
                    throw CancellationException("user cancelled")
            },
            allowFullScan = true,
            now = { 200L },
        )

        val failure = runCatching { repository.scanFull() }.exceptionOrNull()

        assertTrue(failure is CancellationException)
        assertEquals(previous, store.current().snapshot)
        assertEquals(0, store.saveCalls)
    }

    @Test
    fun `hidden apps persist across repository instances and are filtered`() = runTest {
        val store = FakeInventoryStore()
        val source = FakeInstalledAppSource(apps = listOf(example, other))
        val first = InventoryRepository(store, source, now = { 1L })
        first.scanFull()
        first.hide(example.packageName)

        val second = InventoryRepository(store, source, now = { 2L })
        val snapshot = second.snapshot.first()!!
        val hidden = second.hiddenPackages.first()

        assertEquals(setOf(example.packageName), hidden)
        assertEquals(
            listOf(other.packageName),
            snapshot.filteredEntries(InventoryFilters(), hidden).map { it.identity },
        )
        assertEquals(
            listOf(example.packageName, other.packageName),
            snapshot.filteredEntries(
                InventoryFilters(includeHiddenApps = true),
                hidden,
            ).map { it.identity },
        )

        second.unhide(example.packageName)
        assertEquals(emptySet<String>(), second.hiddenPackages.first())
    }

    @Test
    fun `snapshots become stale after the configured age`() = runTest {
        val repository = InventoryRepository(
            store = FakeInventoryStore(),
            source = FakeInstalledAppSource(),
            now = { 0L },
            staleAfterMillis = 1_000L,
        )
        val snapshot = InventorySnapshot(
            scanMode = ScanMode.MANUAL,
            capturedAtEpochMillis = 0L,
        )

        assertFalse(repository.isStale(snapshot))
        assertEquals(
            false,
            snapshot.isStale(nowEpochMillis = 1_000L, staleAfterMillis = 1_000L),
        )
        assertEquals(
            true,
            snapshot.isStale(nowEpochMillis = 1_001L, staleAfterMillis = 1_000L),
        )
    }

    private fun installedApp(
        packageName: String,
        label: String,
        isSystem: Boolean = false,
        isEnabled: Boolean = true,
        workProfile: Boolean = false,
    ) = InstalledApp(
        packageName = packageName,
        label = label,
        versionName = "1.0",
        versionCode = 1L,
        installerPackage = "com.example.store",
        signerSha256 = "abc123",
        isSystemApp = isSystem,
        isEnabled = isEnabled,
        profile = AppProfile(userId = 0, isWorkProfile = workProfile),
    )
}
