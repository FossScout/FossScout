package com.fossscout.feature.contribution

import androidx.lifecycle.ViewModel
import com.fossscout.core.contribution.ContributionContext
import com.fossscout.core.contribution.ContributionEnvironment
import com.fossscout.core.contribution.ContributionRedactor
import com.fossscout.core.contribution.ContributionReportBuilder
import com.fossscout.core.contribution.ContributionRequest
import com.fossscout.core.contribution.ReportErrorClass
import com.fossscout.core.contribution.ReportKind
import com.fossscout.core.contribution.ReportOperation
import com.fossscout.core.contribution.ReportStoreInput
import com.fossscout.core.feature.FeatureId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Owns the contribution form and the review gate. A report can only be copied,
 * shared, or opened in the issue tracker after [previewReport] has built it from
 * the current draft. Editing invalidates the preview.
 */
class ContributionViewModel(
    private val environment: ContributionEnvironment,
    private val builder: ContributionReportBuilder = ContributionReportBuilder(),
    initialContext: ContributionContext = ContributionContext(),
    private val now: () -> Long = System::currentTimeMillis,
) : ViewModel() {
    private val _uiState = MutableStateFlow(
        ContributionUiState(draft = initialContext.toDraft()),
    )
    val uiState: StateFlow<ContributionUiState> = _uiState.asStateFlow()

    /** Replaces the form with a new prefilled context from another screen. */
    fun reset(context: ContributionContext) {
        _uiState.value = ContributionUiState(draft = context.toDraft())
    }

    fun selectKind(kind: ReportKind) {
        val current = _uiState.value.draft
        if (kind == current.kind) return
        val next = current.copy(
            kind = kind,
            featureId = kind.defaultFeature(),
            operation = kind.defaultOperation(),
            errorClass = kind.defaultErrorClass(),
            coverage = current.coverage.takeIf { kind == ReportKind.WRONG_MATCH },
        )
        val cleaned = when {
            kind == ReportKind.WRONG_MATCH -> next.copy(
                storeName = "",
                storeHomepage = "",
                indexUrl = "",
                apiUrl = "",
                repositoryUrl = "",
                hasPinnedFingerprint = false,
                adapterType = null,
                errorMessage = current.errorMessage.takeIf {
                    current.kind == ReportKind.WRONG_MATCH
                }.orEmpty(),
            )

            current.kind == ReportKind.WRONG_MATCH -> next.copy(
                packageName = "",
                expectedStore = "",
                observedResult = "",
                errorMessage = "",
            )

            else -> next
        }
        updateDraft(cleaned)
    }

    fun updateDraft(draft: ContributionDraft) {
        _uiState.update {
            it.copy(
                draft = draft,
                preview = null,
                clipboardRequest = null,
                shareRequest = null,
                issueRequest = null,
                error = null,
            )
        }
    }

    fun previewReport() {
        val draft = _uiState.value.draft
        if (!draft.isValid()) {
            _uiState.update {
                it.copy(
                    preview = null,
                    clipboardRequest = null,
                    shareRequest = null,
                    issueRequest = null,
                    error = ContributionError.INVALID_REPORT,
                    isPreparing = false,
                )
            }
            return
        }
        _uiState.update {
            it.copy(
                isPreparing = true,
                clipboardRequest = null,
                shareRequest = null,
                issueRequest = null,
                error = null,
            )
        }
        try {
            val report = builder.build(
                ContributionRequest(
                    environment = environment,
                    context = draft.toContext(),
                    createdAtEpochMillis = now(),
                    includeDebugDetails = draft.includeDebugDetails,
                ),
            )
            _uiState.update {
                it.copy(
                    preview = report,
                    isPreparing = false,
                    error = null,
                )
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            _uiState.update {
                it.copy(
                    preview = null,
                    isPreparing = false,
                    error = ContributionError.PREVIEW_FAILED,
                )
            }
        }
    }

    fun dismissPreview() {
        _uiState.update {
            it.copy(
                preview = null,
                clipboardRequest = null,
                shareRequest = null,
                issueRequest = null,
            )
        }
    }

    fun requestCopy() {
        val preview = _uiState.value.preview ?: return
        _uiState.update {
            it.copy(
                clipboardRequest = ContributionClipboardRequest(
                    label = "FossScout contribution report",
                    text = preview.body,
                ),
            )
        }
    }

    fun requestShare() {
        val preview = _uiState.value.preview ?: return
        _uiState.update {
            it.copy(
                shareRequest = ContributionShareRequest(
                    subject = preview.title,
                    text = preview.body,
                ),
            )
        }
    }

    fun requestOpenIssue() {
        val preview = _uiState.value.preview ?: return
        _uiState.update { it.copy(issueRequest = ContributionIssueRequest(preview.issueUrl)) }
    }

    fun requestCopyJson() {
        val preview = _uiState.value.preview ?: return
        if (preview.payload.debugDetails.isEmpty()) return
        _uiState.update {
            it.copy(
                clipboardRequest = ContributionClipboardRequest(
                    label = "FossScout debug bundle",
                    text = preview.json,
                ),
            )
        }
    }

    fun requestShareJson() {
        val preview = _uiState.value.preview ?: return
        if (preview.payload.debugDetails.isEmpty()) return
        _uiState.update {
            it.copy(
                shareRequest = ContributionShareRequest(
                    subject = "FossScout debug bundle",
                    text = preview.json,
                ),
            )
        }
    }

    fun consumeClipboardRequest() {
        _uiState.update { it.copy(clipboardRequest = null) }
    }

    fun consumeShareRequest() {
        _uiState.update { it.copy(shareRequest = null) }
    }

    fun consumeIssueRequest() {
        _uiState.update { it.copy(issueRequest = null) }
    }

    fun dismissError() {
        _uiState.update { it.copy(error = null) }
    }

    private fun ContributionDraft.isValid(): Boolean {
        val hasDetails = listOf(
            storeName,
            storeHomepage,
            indexUrl,
            apiUrl,
            repositoryUrl,
            errorMessage,
        ).any(String::isNotBlank)
        return when (kind) {
            ReportKind.WRONG_MATCH ->
                ContributionRedactor.packageName(packageName) != null

            ReportKind.MISSING_STORE -> hasDetails

            ReportKind.ADAPTER_REQUEST -> hasDetails && adapterType != null
        }
    }

    private fun ContributionDraft.toContext(): ContributionContext {
        val wrongMatch = kind == ReportKind.WRONG_MATCH
        val store = if (wrongMatch) {
            null
        } else if (
            listOf(storeName, storeHomepage, indexUrl, apiUrl, repositoryUrl)
                .any(String::isNotBlank)
        ) {
            ReportStoreInput(
                name = storeName,
                homepage = storeHomepage,
                adapterType = adapterType,
                indexUrl = indexUrl,
                apiUrl = apiUrl,
                repositoryUrl = repositoryUrl,
                hasPinnedFingerprint = hasPinnedFingerprint,
            )
        } else {
            null
        }
        return ContributionContext(
            kind = kind,
            featureId = featureId,
            operation = operation,
            store = store,
            adapterType = if (wrongMatch) null else adapterType,
            errorClass = errorClass,
            errorMessage = errorMessage.ifBlank { null },
            packageName = packageName.ifBlank { null }.takeIf { wrongMatch },
            expectedStore = expectedStore.ifBlank { null }.takeIf { wrongMatch },
            observedResult = observedResult.ifBlank { null }.takeIf { wrongMatch },
            coverage = coverage.takeIf { wrongMatch },
        )
    }

    private fun ContributionContext.toDraft(): ContributionDraft = ContributionDraft(
        kind = kind,
        featureId = featureId,
        operation = operation,
        storeName = store?.name.orEmpty(),
        storeHomepage = store?.homepage.orEmpty(),
        adapterType = adapterType ?: store?.adapterType,
        indexUrl = store?.indexUrl.orEmpty(),
        apiUrl = store?.apiUrl.orEmpty(),
        repositoryUrl = store?.repositoryUrl.orEmpty(),
        hasPinnedFingerprint = store?.hasPinnedFingerprint == true,
        errorClass = errorClass,
        errorMessage = errorMessage.orEmpty(),
        packageName = packageName.orEmpty(),
        expectedStore = expectedStore.orEmpty(),
        observedResult = observedResult.orEmpty(),
        coverage = coverage,
    )

    private fun ReportKind.defaultFeature(): FeatureId = when (this) {
        ReportKind.MISSING_STORE,
        ReportKind.ADAPTER_REQUEST,
        -> FeatureId.STORES

        ReportKind.WRONG_MATCH -> FeatureId.MATCHING
    }

    private fun ReportKind.defaultOperation(): ReportOperation = when (this) {
        ReportKind.MISSING_STORE -> ReportOperation.ADD_STORE
        ReportKind.WRONG_MATCH -> ReportOperation.MATCH_APP
        ReportKind.ADAPTER_REQUEST -> ReportOperation.REQUEST_ADAPTER
    }

    private fun ReportKind.defaultErrorClass(): ReportErrorClass = when (this) {
        ReportKind.MISSING_STORE -> ReportErrorClass.STORE_VALIDATION
        ReportKind.WRONG_MATCH -> ReportErrorClass.MATCH
        ReportKind.ADAPTER_REQUEST -> ReportErrorClass.ADAPTER_UNSUPPORTED
    }
}
