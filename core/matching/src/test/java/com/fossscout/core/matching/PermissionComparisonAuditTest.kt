package com.fossscout.core.matching

import com.fossscout.core.inventory.InstalledApp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class PermissionComparisonAuditTest {
    private val builder = PermissionComparisonAuditBuilder()

    @Test
    fun `audit includes sources timestamp permissions and tracker evidence`() {
        val comparison = comparison(
            tracker = TrackerReport(
                dataset = datasetInfo(),
                installed = TrackerScanResult(
                    status = TrackerScanStatus.COMPLETE,
                    matches = listOf(
                        TrackerMatch(
                            name = "Analytics",
                            categories = listOf("Analytics"),
                            website = "https://tracker.example",
                            confidence = TrackerConfidence.KNOWN,
                            matchedSignatures = listOf("com.example.tracker."),
                        ),
                    ),
                    scannedFiles = 1,
                    scannedBytes = 512,
                ),
            ),
        )

        val audit = builder.build(comparison, EXPORTED_AT)
        val json = builder.encode(audit)

        assertEquals(PERMISSION_COMPARISON_AUDIT_SCHEMA_VERSION, audit.schemaVersion)
        assertEquals(EXPORTED_AT, audit.exportedAtEpochMillis)
        assertEquals("2023-11-14T22:13:20Z", audit.exportedAtUtc)
        assertEquals("https://store.example/app", audit.sources.alternativeUrl)
        assertEquals("DIFFERENT_APP", audit.relationship)
        assertEquals(1, audit.installed.permissions.size)
        assertEquals(2, audit.alternative.permissions.size)
        assertEquals(
            listOf("android.permission.CAMERA"),
            audit.diff?.shared,
        )
        assertEquals(
            listOf("android.permission.RECORD_AUDIO"),
            audit.diff?.alternativeOnly,
        )
        assertEquals("2026-11-10", audit.tracker.dataset?.version)
        assertEquals("COMPLETE", audit.tracker.scanStatus)
        assertEquals(1, audit.tracker.scannedFiles)
        assertEquals(512L, audit.tracker.scannedBytes)
        assertEquals("KNOWN", audit.tracker.matches.single().confidence)
        assertTrue(audit.limitations.any { it.contains("not proof") })
        assertTrue(json.contains("\"exportedAtUtc\""))
        assertTrue(json.contains("\"matchedSignatures\""))
        assertTrue(json.contains("com.example.tracker."))
        assertTrue(json.contains("\"alternativeUrl\""))
    }

    @Test
    fun `unknown alternative permissions and missing tracker stay explicit`() {
        val comparison = comparison(
            alternativePermissionsKnown = false,
            tracker = null,
        )

        val audit = builder.build(comparison, EXPORTED_AT)

        assertNull(audit.diff)
        assertFalse(audit.alternative.permissionsKnown)
        assertNull(audit.tracker.dataset)
        assertEquals("NOT_RUN", audit.tracker.scanStatus)
        assertTrue(audit.tracker.matches.isEmpty())
        assertTrue(audit.tracker.limitation.contains("not proof"))
        assertTrue(audit.limitations.any { it.contains("not downloaded or scanned") })
        assertTrue(audit.limitations.any { it.contains("not proof") })
    }

    @Test
    fun `in-progress tracker scan is recorded without claiming a result`() {
        val comparison = comparison(
            tracker = TrackerReport(
                dataset = datasetInfo(),
                installed = TrackerScanResult(),
            ),
            trackerScanInProgress = true,
        )

        val audit = builder.build(comparison, EXPORTED_AT)

        assertEquals("IN_PROGRESS", audit.tracker.scanStatus)
        assertTrue(audit.tracker.scanInProgress)
        assertTrue(audit.tracker.matches.isEmpty())
    }

    @Test
    fun `negative export timestamp fails closed`() {
        assertThrows(IllegalArgumentException::class.java) {
            builder.build(comparison(tracker = null), -1L)
        }
    }

    private fun comparison(
        alternativePermissionsKnown: Boolean = true,
        tracker: TrackerReport?,
        trackerScanInProgress: Boolean = false,
    ): PermissionComparison = comparePermissions(
        installed = InstalledApp(
            packageName = "org.example.old",
            label = "Old app",
            versionName = "1.0",
            requestedPermissions = listOf(CAMERA),
        ),
        alternative = PermissionAlternative(
            label = "New app",
            packageName = "org.example.new",
            permissions = listOf(CAMERA, RECORD_AUDIO),
            permissionsKnown = alternativePermissionsKnown,
            versionName = "2.0",
            storeName = "F-Droid",
            sourceUrl = "https://store.example/app",
            relationship = PermissionRelationship.DIFFERENT_APP,
        ),
        tracker = tracker,
        trackerScanInProgress = trackerScanInProgress,
    )

    private fun datasetInfo(): TrackerDatasetInfo = TrackerDatasetInfo(
        name = "Test dataset",
        version = "2026-11-10",
        licenseName = "ODbL 1.0 / DbCL 1.0",
        licenseUrl = "https://example.org/license",
        contentsLicenseUrl = "https://example.org/contents-license",
        sourceUrl = "https://example.org/source",
        retrievedAt = "2026-11-10",
        attribution = "Tracker data from Example",
    )

    private companion object {
        const val CAMERA = "android.permission.CAMERA"
        const val RECORD_AUDIO = "android.permission.RECORD_AUDIO"
        const val EXPORTED_AT = 1_700_000_000_000L
    }
}
