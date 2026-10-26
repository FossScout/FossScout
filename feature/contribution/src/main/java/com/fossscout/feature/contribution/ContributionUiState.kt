package com.fossscout.feature.contribution

import com.fossscout.core.contribution.ContributionReport
import com.fossscout.core.contribution.ReportErrorClass
import com.fossscout.core.contribution.ReportKind
import com.fossscout.core.contribution.ReportOperation
import com.fossscout.core.feature.FeatureId
import com.fossscout.core.matching.CoverageSnapshot
import com.fossscout.core.store.AdapterType

enum class ContributionError {
    INVALID_REPORT,
    PREVIEW_FAILED,
}

/** Editable form state. The builder receives a copy only after validation. */
data class ContributionDraft(
    val kind: ReportKind = ReportKind.MISSING_STORE,
    val featureId: FeatureId = FeatureId.STORES,
    val operation: ReportOperation = ReportOperation.ADD_STORE,
    val storeName: String = "",
    val storeHomepage: String = "",
    val adapterType: AdapterType? = null,
    val indexUrl: String = "",
    val apiUrl: String = "",
    val repositoryUrl: String = "",
    val hasPinnedFingerprint: Boolean = false,
    val errorClass: ReportErrorClass? = ReportErrorClass.STORE_VALIDATION,
    val errorMessage: String = "",
    val packageName: String = "",
    val expectedStore: String = "",
    val observedResult: String = "",
    val coverage: CoverageSnapshot? = null,
    val includeDebugDetails: Boolean = false,
)

data class ContributionClipboardRequest(
    val label: String,
    val text: String,
)

data class ContributionShareRequest(
    val subject: String,
    val text: String,
)

data class ContributionIssueRequest(
    val url: String,
)

data class ContributionUiState(
    val draft: ContributionDraft = ContributionDraft(),
    val preview: ContributionReport? = null,
    val clipboardRequest: ContributionClipboardRequest? = null,
    val shareRequest: ContributionShareRequest? = null,
    val issueRequest: ContributionIssueRequest? = null,
    val error: ContributionError? = null,
    val isPreparing: Boolean = false,
) {
    /** Copy/share/open are only available after a preview has been built. */
    val canShare: Boolean get() = preview != null && !isPreparing
}
