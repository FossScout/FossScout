package com.fossscout.core.contribution

import com.fossscout.core.matching.ConfidenceLabel
import com.fossscout.core.matching.CoverageReason
import com.fossscout.core.matching.CoverageSnapshot
import com.fossscout.core.matching.CoverageStatus
import com.fossscout.core.matching.MatchDecision
import com.fossscout.core.matching.MatchLevel
import com.fossscout.core.matching.MatchOutcome
import com.fossscout.core.matching.StoreCoverage
import com.fossscout.core.store.AdapterType
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ContributionReportBuilderTest {
    private val environment = ContributionEnvironment(
        appVersionName = "0.1.0",
        appVersionCode = 1,
        profile = "playstore",
    )
    private val builder = ContributionReportBuilder()

    @Test
    fun `missing store report includes version profile adapter error and redacted store`() {
        val context = ContributionContext.forMissingStore(
            store = ReportStoreInput(
                name = "Example",
                homepage = "https://user:pass@example.com/repo?token=secret",
                adapterType = AdapterType.FDROID_V2,
                indexUrl = "https://example.com/repo?token=index",
                hasPinnedFingerprint = true,
            ),
            errorMessage = "Invalid URL at /data/user/0/private token=secret",
        )

        val report = builder.build(
            ContributionRequest(
                environment = environment,
                context = context,
                createdAtEpochMillis = 1_700_000_000_000,
            ),
        )

        assertEquals("0.1.0", report.payload.appVersionName)
        assertEquals(1L, report.payload.appVersionCode)
        assertEquals("playstore", report.payload.profile)
        assertEquals("STORES", report.payload.featureId)
        assertEquals(ReportOperation.ADD_STORE, report.payload.operation)
        assertEquals(AdapterType.FDROID_V2, report.payload.adapterType)
        assertEquals(ReportErrorClass.STORE_VALIDATION, report.payload.errorClass)
        assertEquals("https://example.com/repo", report.payload.store?.homepage)
        assertEquals("https://example.com/repo", report.payload.store?.indexUrl)
        assertTrue(report.payload.store?.hasPinnedFingerprint == true)
        assertTrue(report.body.contains("**App:** 0.1.0 (1)"))
        assertTrue(report.body.contains("**Profile:** playstore"))
        assertTrue(report.body.contains("**Adapter type:** FDROID_V2"))
        assertTrue(report.body.contains("**Error type:** Store validation"))
        assertTrue(report.body.contains("<local-path>"))
        assertFalse(report.body.contains("token=secret"))
        assertFalse(report.body.contains("/data/user/0/private"))
        assertFalse(report.body.contains("user:pass"))
    }

    @Test
    fun `wrong match report includes package observed result and coverage status only`() {
        val decision = MatchDecision(
            appKey = "org.example.app",
            label = "Example",
            packageName = "org.example.app",
            level = MatchLevel.L1,
            outcome = MatchOutcome.MATCH,
            confidence = ConfidenceLabel.HIGH,
            coverage = CoverageSnapshot(
                checkedAtEpochMillis = 100,
                stores = listOf(
                    StoreCoverage(
                        storeId = "fdroid",
                        storeName = "F-Droid",
                        status = CoverageStatus.CHECKED,
                        detail = "token=secret",
                        lastSyncEpochMillis = 123,
                        packageCount = 999,
                    ),
                    StoreCoverage(
                        storeId = "custom",
                        storeName = "Custom",
                        status = CoverageStatus.FAILED,
                        reason = CoverageReason.READ_ERROR,
                        detail = "private note /data/user/0/private",
                    ),
                ),
            ),
            reasons = listOf("token=secret", "/data/user/0/private", "unrelated.package"),
        )

        val report = builder.build(
            ContributionRequest(
                environment = environment,
                context = ContributionContext.forWrongMatch(decision),
                createdAtEpochMillis = 100,
            ),
        )

        assertEquals(ReportKind.WRONG_MATCH, report.payload.kind)
        assertEquals("MATCHING", report.payload.featureId)
        assertEquals(ReportOperation.MATCH_APP, report.payload.operation)
        assertEquals("org.example.app", report.payload.packageName)
        assertEquals(
            "outcome=MATCH; level=L1; confidence=HIGH",
            report.payload.observedResult,
        )
        assertEquals(2, report.payload.coverage.size)
        assertEquals("Custom", report.payload.coverage[0].storeName)
        assertEquals("FAILED", report.payload.coverage[0].status)
        assertEquals("READ_ERROR", report.payload.coverage[0].reason)
        assertEquals("F-Droid", report.payload.coverage[1].storeName)
        assertNull(report.payload.coverage[1].reason)
        assertTrue(report.body.contains("**Adapter type:** unknown"))
        assertFalse(report.body.contains("unrelated.package"))
        assertFalse(report.body.contains("token=secret"))
        assertFalse(report.body.contains("private note"))
        assertFalse(report.body.contains("/data/user/0/private"))
        assertFalse(report.body.contains("999"))
        assertFalse(report.body.contains("123"))
    }

    @Test
    fun `adapter request keeps the requested adapter and operation`() {
        val report = builder.build(
            ContributionRequest(
                environment = environment,
                context = ContributionContext.forAdapterRequest(
                    store = ReportStoreInput(
                        name = "Example",
                        homepage = "https://example.com/repo",
                    ),
                    adapterType = AdapterType.GENERIC_JSON,
                    errorMessage = "Unknown index format",
                ),
                createdAtEpochMillis = 100,
            ),
        )

        assertEquals(ReportKind.ADAPTER_REQUEST, report.payload.kind)
        assertEquals("STORES", report.payload.featureId)
        assertEquals(ReportOperation.REQUEST_ADAPTER, report.payload.operation)
        assertEquals(AdapterType.GENERIC_JSON, report.payload.adapterType)
        assertEquals(ReportErrorClass.ADAPTER_UNSUPPORTED, report.payload.errorClass)
        assertTrue(report.title.startsWith("Adapter request"))
    }

    @Test
    fun `debug details are opt in and remain redacted`() {
        val context = ContributionContext.forMissingStore(
            store = ReportStoreInput(
                name = "Example",
                homepage = "https://example.com/repo",
                adapterType = AdapterType.FDROID_V2,
            ),
        )
        val request = ContributionRequest(
            environment = environment,
            context = context,
            createdAtEpochMillis = 100,
        )

        val plain = builder.build(request)
        val debug = builder.build(request.copy(includeDebugDetails = true))

        assertTrue(plain.payload.debugDetails.isEmpty())
        assertFalse(plain.body.contains("Debug details"))
        assertTrue(debug.payload.debugDetails.isNotEmpty())
        assertTrue(debug.body.contains("### Debug details (opt-in, redacted)"))
        assertTrue(debug.json.contains("\"debugDetails\""))
        assertFalse(debug.json.contains("\"trustNote\""))
        assertFalse(debug.json.contains("\"installedApps\""))
        assertFalse(debug.json.contains("\"notes\""))
        assertFalse(debug.json.contains("\"deviceId\""))
        assertFalse(debug.json.contains("token=secret"))
    }

    @Test
    fun `invalid kind specific reports fail closed`() {
        assertThrows(IllegalArgumentException::class.java) {
            builder.build(
                ContributionRequest(
                    environment = environment,
                    context = ContributionContext(
                        kind = ReportKind.WRONG_MATCH,
                        packageName = "not a package",
                    ),
                    createdAtEpochMillis = 100,
                ),
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            builder.build(
                ContributionRequest(
                    environment = environment,
                    context = ContributionContext(kind = ReportKind.MISSING_STORE),
                    createdAtEpochMillis = 100,
                ),
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            builder.build(
                ContributionRequest(
                    environment = environment,
                    context = ContributionContext(
                        kind = ReportKind.ADAPTER_REQUEST,
                        store = ReportStoreInput(name = "Example"),
                    ),
                    createdAtEpochMillis = 100,
                ),
            )
        }
    }

    @Test
    fun `issue url is prefilled with encoded title and body`() {
        val context = ContributionContext.forMissingStore(
            store = ReportStoreInput(
                name = "Example",
                homepage = "https://example.com/repo",
                adapterType = AdapterType.FDROID_V2,
            ),
        )
        val report = builder.build(
            ContributionRequest(
                environment = environment,
                context = context,
                createdAtEpochMillis = 100,
            ),
        )

        assertTrue(
            report.issueUrl.startsWith(
                "https://github.com/FossScout/FossScout/issues/new?title=",
            ),
        )
        val encodedBody = report.issueUrl.substringAfter("&body=")
        val decodedBody = URLDecoder.decode(encodedBody, StandardCharsets.UTF_8)
        assertEquals(report.body.trim(), decodedBody)
        assertFalse(report.issueUrl.contains(" "))
    }

    @Test
    fun `issue url rejects non http trackers and bounds long bodies`() {
        assertThrows(IllegalArgumentException::class.java) {
            ContributionIssueUrl.build("file:///tmp/issues", "title", "body")
        }

        val body = "x".repeat(ContributionIssueUrl.MAX_BODY_CHARS + 50)
        val url = ContributionIssueUrl.build(title = "title", body = body)
        val decoded = URLDecoder.decode(
            url.substringAfter("&body="),
            StandardCharsets.UTF_8,
        )
        assertTrue(decoded.length > ContributionIssueUrl.MAX_BODY_CHARS)
        assertTrue(decoded.contains("[Report truncated. Use Copy report for the full preview.]"))
    }

    @Test
    fun `issue url preserves existing query and drops fragment`() {
        val withQuery = ContributionIssueUrl.build(
            baseUrl = "https://example.com/issues/new?template=bug#ignored",
            title = "title",
            body = "body",
        )
        assertTrue(
            withQuery.startsWith(
                "https://example.com/issues/new?template=bug&title=",
            ),
        )
        assertFalse(withQuery.contains("#ignored"))

        val withTrailingQuestion = ContributionIssueUrl.build(
            baseUrl = "https://example.com/issues/new?",
            title = "title",
            body = "body",
        )
        assertTrue(
            withTrailingQuestion.startsWith(
                "https://example.com/issues/new?title=",
            ),
        )
        assertFalse(withTrailingQuestion.contains("?&"))
    }

    @Test
    fun `json payload uses stable serial names and no private fields`() {
        val context = ContributionContext.forMissingStore(
            store = ReportStoreInput(
                name = "Example",
                homepage = "https://example.com/repo",
                adapterType = AdapterType.FDROID_V2,
            ),
        )
        val report = builder.build(
            ContributionRequest(
                environment = environment,
                context = context,
                createdAtEpochMillis = 100,
            ),
        )

        assertTrue(report.json.contains("\"schemaVersion\": 1"))
        assertTrue(report.json.contains("\"kind\": \"missing_store\""))
        assertTrue(report.json.contains("\"operation\": \"add_store\""))
        assertFalse(report.json.contains("\"trustNote\""))
        assertFalse(report.json.contains("\"health\""))
        assertFalse(report.json.contains("\"inventory\""))
        assertFalse(report.json.contains("\"notes\""))
        assertFalse(report.json.contains("\"deviceId\""))
    }
}
