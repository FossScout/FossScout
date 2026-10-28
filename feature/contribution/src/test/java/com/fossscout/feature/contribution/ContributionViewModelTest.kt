package com.fossscout.feature.contribution

import com.fossscout.core.contribution.ContributionContext
import com.fossscout.core.contribution.ContributionEnvironment
import com.fossscout.core.contribution.ReportKind
import com.fossscout.core.contribution.ReportOperation
import com.fossscout.core.contribution.ReportStoreInput
import com.fossscout.core.contribution.forAdapterRequest
import com.fossscout.core.contribution.forMissingStore
import com.fossscout.core.contribution.forWrongMatch
import com.fossscout.core.feature.FeatureId
import com.fossscout.core.matching.ConfidenceLabel
import com.fossscout.core.matching.CoverageReason
import com.fossscout.core.matching.CoverageSnapshot
import com.fossscout.core.matching.CoverageStatus
import com.fossscout.core.matching.MatchDecision
import com.fossscout.core.matching.MatchLevel
import com.fossscout.core.matching.MatchOutcome
import com.fossscout.core.matching.StoreCoverage
import com.fossscout.core.store.AdapterType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ContributionViewModelTest {
    private val environment = ContributionEnvironment(
        appVersionName = "0.1.0",
        appVersionCode = 1,
        profile = "playstore",
    )

    private fun viewModel(
        context: ContributionContext = ContributionContext.forMissingStore(
            store = ReportStoreInput(
                name = "Example",
                homepage = "https://example.com/repo",
                adapterType = AdapterType.FDROID_V2,
            ),
        ),
    ) = ContributionViewModel(
        environment = environment,
        initialContext = context,
        now = { 100 },
    )

    @Test
    fun `copy share and issue actions require a preview`() {
        val viewModel = viewModel()

        viewModel.requestCopy()
        viewModel.requestShare()
        viewModel.requestOpenIssue()

        assertNull(viewModel.uiState.value.clipboardRequest)
        assertNull(viewModel.uiState.value.shareRequest)
        assertNull(viewModel.uiState.value.issueRequest)

        viewModel.previewReport()
        val preview = viewModel.uiState.value.preview
        assertNotNull(preview)

        viewModel.requestCopy()
        assertEquals(preview?.body, viewModel.uiState.value.clipboardRequest?.text)
        viewModel.consumeClipboardRequest()
        assertNull(viewModel.uiState.value.clipboardRequest)

        viewModel.requestShare()
        assertEquals(preview?.title, viewModel.uiState.value.shareRequest?.subject)
        assertEquals(preview?.body, viewModel.uiState.value.shareRequest?.text)
        viewModel.consumeShareRequest()
        assertNull(viewModel.uiState.value.shareRequest)

        viewModel.requestOpenIssue()
        assertEquals(preview?.issueUrl, viewModel.uiState.value.issueRequest?.url)
        viewModel.consumeIssueRequest()
        assertNull(viewModel.uiState.value.issueRequest)
    }

    @Test
    fun `store prefill keeps api repository and fingerprint presence`() {
        val viewModel = viewModel(
            ContributionContext.forMissingStore(
                store = ReportStoreInput(
                    name = "Example",
                    homepage = "https://example.com/repo",
                    adapterType = AdapterType.LIVE_API,
                    indexUrl = "https://example.com/index.json",
                    apiUrl = "https://api.example.com/v1?token=secret",
                    repositoryUrl = "https://example.com/source",
                    hasPinnedFingerprint = true,
                ),
            ),
        )

        viewModel.previewReport()

        val store = viewModel.uiState.value.preview?.payload?.store
        assertNotNull(store)
        assertEquals("https://api.example.com/v1", store?.apiUrl)
        assertEquals("https://example.com/source", store?.repositoryUrl)
        assertEquals(true, store?.hasPinnedFingerprint)
        assertEquals(AdapterType.LIVE_API, store?.adapterType)
    }

    @Test
    fun `editing invalidates a previously reviewed report`() {
        val viewModel = viewModel()
        viewModel.previewReport()
        assertNotNull(viewModel.uiState.value.preview)

        viewModel.updateDraft(
            viewModel.uiState.value.draft.copy(storeName = "Changed"),
        )

        assertNull(viewModel.uiState.value.preview)
        viewModel.requestShare()
        assertNull(viewModel.uiState.value.shareRequest)
    }

    @Test
    fun `wrong match needs a structurally valid package`() {
        val viewModel = viewModel(
            ContributionContext(
                kind = ReportKind.WRONG_MATCH,
                featureId = FeatureId.MATCHING,
                operation = ReportOperation.MATCH_APP,
                packageName = "not a package",
            ),
        )

        viewModel.previewReport()

        assertEquals(ContributionError.INVALID_REPORT, viewModel.uiState.value.error)
        assertNull(viewModel.uiState.value.preview)
    }

    @Test
    fun `wrong match prefill includes package observed result and coverage`() {
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
                    ),
                    StoreCoverage(
                        storeId = "custom",
                        storeName = "Custom",
                        status = CoverageStatus.FAILED,
                        reason = CoverageReason.READ_ERROR,
                        detail = "token=secret /data/user/0/private",
                    ),
                ),
            ),
        )
        val viewModel = viewModel(ContributionContext.forWrongMatch(decision))

        assertEquals(ReportKind.WRONG_MATCH, viewModel.uiState.value.draft.kind)
        assertEquals(FeatureId.MATCHING, viewModel.uiState.value.draft.featureId)
        assertEquals(ReportOperation.MATCH_APP, viewModel.uiState.value.draft.operation)
        assertEquals("org.example.app", viewModel.uiState.value.draft.packageName)
        assertTrue(viewModel.uiState.value.draft.observedResult.contains("outcome=MATCH"))
        assertNotNull(viewModel.uiState.value.draft.coverage)

        viewModel.previewReport()

        val report = viewModel.uiState.value.preview
        assertNotNull(report)
        assertEquals("org.example.app", report?.payload?.packageName)
        assertEquals(2, report?.payload?.coverage?.size)
        assertFalse(report?.body?.contains("token=secret") == true)
        assertFalse(report?.body?.contains("/data/user/0/private") == true)
    }

    @Test
    fun `debug details are explicit and debug json export is gated`() {
        val viewModel = viewModel()
        viewModel.previewReport()
        viewModel.requestCopyJson()
        assertNull(viewModel.uiState.value.clipboardRequest)

        viewModel.dismissPreview()
        viewModel.updateDraft(
            viewModel.uiState.value.draft.copy(includeDebugDetails = true),
        )
        viewModel.previewReport()

        val preview = viewModel.uiState.value.preview
        assertNotNull(preview)
        assertTrue(preview?.payload?.debugDetails?.isNotEmpty() == true)

        viewModel.requestCopyJson()
        assertNotNull(viewModel.uiState.value.clipboardRequest)
        assertTrue(
            viewModel.uiState.value.clipboardRequest?.text?.contains("\"debugDetails\"") == true,
        )

        viewModel.consumeClipboardRequest()
        viewModel.requestShareJson()
        assertNotNull(viewModel.uiState.value.shareRequest)
        assertEquals("FossScout debug bundle", viewModel.uiState.value.shareRequest?.subject)
    }

    @Test
    fun `adapter request needs an adapter even with store details`() {
        val viewModel = viewModel(
            ContributionContext.forAdapterRequest(
                store = ReportStoreInput(
                    name = "Example",
                    homepage = "https://example.com/repo",
                ),
                errorMessage = "Unknown index format",
            ),
        )

        viewModel.previewReport()

        assertEquals(ContributionError.INVALID_REPORT, viewModel.uiState.value.error)
        assertNull(viewModel.uiState.value.preview)

        viewModel.updateDraft(
            viewModel.uiState.value.draft.copy(adapterType = AdapterType.GENERIC_JSON),
        )
        viewModel.previewReport()

        assertEquals(ReportKind.ADAPTER_REQUEST, viewModel.uiState.value.preview?.payload?.kind)
        assertEquals(
            AdapterType.GENERIC_JSON,
            viewModel.uiState.value.preview?.payload?.adapterType,
        )
    }

    @Test
    fun `switching kind clears fields from the other report family`() {
        val viewModel = viewModel(
            ContributionContext(
                kind = ReportKind.WRONG_MATCH,
                featureId = FeatureId.MATCHING,
                operation = ReportOperation.MATCH_APP,
                packageName = "org.example.app",
                expectedStore = "F-Droid",
                observedResult = "outcome=MATCH",
                coverage = CoverageSnapshot(
                    checkedAtEpochMillis = 100,
                    stores = listOf(
                        StoreCoverage(
                            storeId = "fdroid",
                            storeName = "F-Droid",
                            status = CoverageStatus.CHECKED,
                        ),
                    ),
                ),
                errorMessage = "match details",
            ),
        )

        viewModel.selectKind(ReportKind.MISSING_STORE)

        assertEquals("", viewModel.uiState.value.draft.packageName)
        assertEquals("", viewModel.uiState.value.draft.expectedStore)
        assertEquals("", viewModel.uiState.value.draft.observedResult)
        assertEquals("", viewModel.uiState.value.draft.errorMessage)
        assertNull(viewModel.uiState.value.draft.coverage)

        viewModel.updateDraft(
            viewModel.uiState.value.draft.copy(
                storeName = "Example",
                storeHomepage = "https://example.com/repo",
                adapterType = AdapterType.FDROID_V2,
                indexUrl = "https://example.com/index.json",
                apiUrl = "https://example.com/api",
                repositoryUrl = "https://example.com/source",
                hasPinnedFingerprint = true,
                errorMessage = "store details",
            ),
        )
        viewModel.selectKind(ReportKind.WRONG_MATCH)

        assertEquals("", viewModel.uiState.value.draft.storeName)
        assertEquals("", viewModel.uiState.value.draft.storeHomepage)
        assertEquals("", viewModel.uiState.value.draft.indexUrl)
        assertEquals("", viewModel.uiState.value.draft.apiUrl)
        assertEquals("", viewModel.uiState.value.draft.repositoryUrl)
        assertFalse(viewModel.uiState.value.draft.hasPinnedFingerprint)
        assertEquals("", viewModel.uiState.value.draft.errorMessage)
        assertNull(viewModel.uiState.value.draft.adapterType)
    }

    @Test
    fun `reset replaces the draft and clears the review state`() {
        val viewModel = viewModel()
        viewModel.previewReport()
        assertNotNull(viewModel.uiState.value.preview)

        viewModel.reset(
            ContributionContext.forMissingStore(
                store = ReportStoreInput(
                    name = "Another",
                    homepage = "https://another.example/repo",
                    adapterType = AdapterType.GENERIC_JSON,
                ),
            ),
        )

        assertEquals("Another", viewModel.uiState.value.draft.storeName)
        assertNull(viewModel.uiState.value.preview)
        assertNull(viewModel.uiState.value.clipboardRequest)
        assertNull(viewModel.uiState.value.shareRequest)
        assertNull(viewModel.uiState.value.issueRequest)
        assertNull(viewModel.uiState.value.error)
    }
}
