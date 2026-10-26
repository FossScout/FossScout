package com.fossscout.core.contribution

import com.fossscout.core.matching.CoverageReason
import com.fossscout.core.matching.CoverageSnapshot
import com.fossscout.core.matching.CoverageStatus
import com.fossscout.core.matching.StoreCoverage
import com.fossscout.core.model.StoreId
import com.fossscout.core.store.AdapterType
import com.fossscout.core.store.StoreDescriptor
import com.fossscout.core.store.StoreHealth
import com.fossscout.core.store.StoreHealthStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ContributionRedactorTest {
    @Test
    fun `url redaction keeps the useful location and drops credentials query and fragment`() {
        assertEquals(
            "https://example.com/repo",
            ContributionRedactor.redactUrl(
                "https://user:pass@example.com/repo?token=secret#fragment",
            ),
        )
        assertEquals(
            "http://example.com:8080/a/b",
            ContributionRedactor.redactUrl("http://example.com:8080/a/b?x=1"),
        )
        assertEquals(
            "https://example.com/Repo",
            ContributionRedactor.redactUrl("HTTPS://Example.COM/Repo"),
        )
        assertEquals(
            "https://example.com/repo/<redacted>",
            ContributionRedactor.redactUrl(
                "https://example.com/repo/0123456789abcdef0123456789abcdef",
            ),
        )
        assertEquals(
            "https://example.com/repo/<redacted>",
            ContributionRedactor.redactUrl("https://example.com/repo/0123456789abcdef"),
        )
        assertEquals(
            "https://example.com/repo/<redacted>",
            ContributionRedactor.redactUrl("https://example.com/repo/123456789012345"),
        )
        assertNull(ContributionRedactor.redactUrl("/data/user/0/private/index.jar"))
        assertNull(ContributionRedactor.redactUrl("file:///data/user/0/private"))
        assertNull(ContributionRedactor.redactUrl("example.com/repo"))
        assertNull(ContributionRedactor.redactUrl("https://example.com/a b"))
    }

    @Test
    fun `message redaction removes paths tokens emails and long identifiers`() {
        assertEquals(
            "Sync failed at <local-path>",
            ContributionRedactor.redactMessage(
                "Sync failed at /data/user/0/com.fossscout/cache/index.jar?token=abc",
            ),
        )
        assertEquals(
            "token=<redacted> password=<redacted>",
            ContributionRedactor.redactMessage("token=abc password=hunter2"),
        )
        assertEquals(
            "Contact <email> about <hex-identifier>",
            ContributionRedactor.redactMessage(
                "Contact me@example.com about 0123456789abcdef0123456789abcdef",
            ),
        )
        assertEquals(
            "Device <identifier>",
            ContributionRedactor.redactMessage("Device 123456789012345"),
        )
        assertEquals(
            "<local-path>",
            ContributionRedactor.redactMessage("file:///data/user/0/private"),
        )
        assertEquals(
            "<local-path>",
            ContributionRedactor.redactMessage("C:\\Users\\me\\secret.txt"),
        )
        assertEquals(
            "See https://example.com/repo for details",
            ContributionRedactor.redactMessage(
                "See https://user:pass@example.com/repo?token=abc for details",
            ),
        )
        assertEquals(
            "Authorization: Bearer <redacted>",
            ContributionRedactor.redactMessage(
                "Authorization: Bearer abcdefghijklmnopqrstuvwxyz123456",
            ),
        )
        assertEquals(
            "{\"token=<redacted>\"}",
            ContributionRedactor.redactMessage("""{"token":"abc123"}"""),
        )
        assertEquals(
            "<redacted-token>",
            ContributionRedactor.redactMessage("ghp_abcdefghijklmnopqrstuvwxyz012345"),
        )
        assertEquals(
            "<redacted-token>",
            ContributionRedactor.redactMessage("Z".repeat(48)),
        )
        assertEquals(
            "device <hex-identifier>",
            ContributionRedactor.redactMessage("device 0123456789abcdef"),
        )
        assertEquals(
            "Read <local-path>",
            ContributionRedactor.redactMessage("Read /etc/passwd"),
        )
        assertEquals(
            "<local-path>",
            ContributionRedactor.redactMessage("~/secret"),
        )
        assertEquals(
            "<local-path>",
            ContributionRedactor.redactMessage("content://media/external/images/1"),
        )
    }

    @Test
    fun `free text is bounded and blank input becomes null`() {
        assertNull(ContributionRedactor.redactMessage("   "))
        assertEquals(500, ContributionRedactor.redactMessage("word ".repeat(200))?.length)
        assertEquals("Example store", ContributionRedactor.safeLabel("  Example\nstore  "))
        assertEquals(
            "Store token=<redacted>",
            ContributionRedactor.safeLabel("Store token=secret"),
        )
        assertNull(ContributionRedactor.safeLabel("\n\t"))
    }

    @Test
    fun `only structurally valid package names survive`() {
        assertEquals("org.example.app", ContributionRedactor.packageName(" org.example.app "))
        assertEquals("org.example", ContributionRedactor.packageName("org.example"))
        assertEquals(
            "com.example.a123456789abcdef",
            ContributionRedactor.packageName("com.example.a123456789abcdef"),
        )
        assertNull(ContributionRedactor.packageName("my app"))
        assertNull(ContributionRedactor.packageName("org.example.app; rm -rf /"))
        assertNull(ContributionRedactor.packageName("org"))
    }

    @Test
    fun `store descriptor drops notes health fingerprints and query tokens`() {
        val descriptor = StoreDescriptor(
            id = StoreId("example"),
            name = "Example",
            homepage = "https://user:pass@example.com/?token=home",
            adapterType = AdapterType.FDROID_V2,
            indexUrl = "https://example.com/repo?token=index",
            apiUrl = "https://example.com/api?key=api",
            repositoryUrl = "http://example.com/repo?token=repo",
            fingerprint = "a".repeat(64),
            trustNote = "private note",
            health = StoreHealth(
                status = StoreHealthStatus.ERROR,
                error = "/data/user/0/private",
            ),
        )

        val redacted = ContributionRedactor.redactStore(descriptor.toReportStoreInput())

        assertEquals("Example", redacted?.name)
        assertEquals("https://example.com/", redacted?.homepage)
        assertEquals("https://example.com/repo", redacted?.indexUrl)
        assertEquals("https://example.com/api", redacted?.apiUrl)
        assertEquals("http://example.com/repo", redacted?.repositoryUrl)
        assertTrue(redacted?.hasPinnedFingerprint == true)
        assertFalse(redacted.toString().contains("private note"))
        assertFalse(redacted.toString().contains("/data/user/0/private"))
        assertFalse(redacted.toString().contains("a".repeat(64)))
    }

    @Test
    fun `coverage keeps store status only`() {
        val snapshot = CoverageSnapshot(
            checkedAtEpochMillis = 100,
            stores = listOf(
                StoreCoverage(
                    storeId = "fdroid",
                    storeName = "F-Droid",
                    status = CoverageStatus.CHECKED,
                    detail = "token=secret /data/user/0/private",
                    lastSyncEpochMillis = 123,
                    packageCount = 999,
                ),
                StoreCoverage(
                    storeId = "custom",
                    storeName = "Custom",
                    status = CoverageStatus.FAILED,
                    reason = CoverageReason.READ_ERROR,
                    detail = "my private note",
                ),
            ),
        )

        val coverage = ContributionRedactor.coverage(snapshot)

        assertEquals(2, coverage.size)
        assertEquals("Custom", coverage[0].storeName)
        assertEquals("FAILED", coverage[0].status)
        assertEquals("READ_ERROR", coverage[0].reason)
        assertEquals("F-Droid", coverage[1].storeName)
        assertEquals("CHECKED", coverage[1].status)
        assertNull(coverage[1].reason)
        assertFalse(coverage.toString().contains("token=secret"))
        assertFalse(coverage.toString().contains("private note"))
        assertFalse(coverage.toString().contains("999"))
        assertFalse(coverage.toString().contains("123"))
    }
}
