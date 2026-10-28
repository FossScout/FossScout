package com.fossscout.core.contribution

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Builds a redacted, reviewable contribution report. This class is pure JVM so
 * the privacy boundary can be tested without Android or network access.
 */
class ContributionReportBuilder(
    private val issueTrackerUrl: String = ContributionIssueUrl.DEFAULT_NEW_ISSUE_URL,
    private val json: Json = Json {
        prettyPrint = true
        encodeDefaults = true
    },
) {
    fun build(request: ContributionRequest): ContributionReport {
        require(request.createdAtEpochMillis >= 0) {
            "Created timestamp must not be negative"
        }
        val context = request.context
        val store = ContributionRedactor.redactStore(context.store)
        val adapterType = context.adapterType ?: store?.adapterType
        val errorMessage = ContributionRedactor.redactMessage(context.errorMessage)
        val packageName = ContributionRedactor.packageName(context.packageName)
        val expectedStore = ContributionRedactor.safeLabel(context.expectedStore)
        val observedResult = ContributionRedactor.observedResult(context.observedResult)
        val coverage = ContributionRedactor.coverage(context.coverage)

        when (context.kind) {
            ReportKind.WRONG_MATCH -> require(!packageName.isNullOrBlank()) {
                "A wrong-match report needs a valid package name"
            }

            ReportKind.MISSING_STORE -> require(
                store != null || !errorMessage.isNullOrBlank(),
            ) {
                "A missing-store report needs store details or an error"
            }

            ReportKind.ADAPTER_REQUEST -> require(adapterType != null) {
                "An adapter request needs an adapter type"
            }
        }

        val base = ReportPayload(
            kind = context.kind,
            appVersionName = request.environment.appVersionName.trim(),
            appVersionCode = request.environment.appVersionCode,
            profile = request.environment.profile.trim(),
            featureId = context.featureId.name,
            operation = context.operation,
            store = store,
            adapterType = adapterType,
            errorClass = context.errorClass,
            errorMessage = errorMessage,
            packageName = packageName,
            expectedStore = expectedStore,
            observedResult = observedResult,
            coverage = coverage,
            createdAtEpochMillis = request.createdAtEpochMillis,
        )
        val payload = if (request.includeDebugDetails) {
            base.copy(debugDetails = debugDetails(base))
        } else {
            base
        }
        val title = title(payload)
        val body = render(payload)
        return ContributionReport(
            payload = payload,
            title = title,
            body = body,
            json = json.encodeToString(ReportPayload.serializer(), payload),
            issueUrl = ContributionIssueUrl.build(
                baseUrl = issueTrackerUrl,
                title = title,
                body = body,
            ),
        )
    }

    private fun title(payload: ReportPayload): String {
        val storeName = payload.store?.name
        val adapterName = payload.adapterType?.name ?: storeName
        val raw = when (payload.kind) {
            ReportKind.MISSING_STORE ->
                "Missing store" + storeName?.let { ": $it" }.orEmpty()

            ReportKind.WRONG_MATCH ->
                "Wrong match" + payload.packageName?.let { ": $it" }.orEmpty()

            ReportKind.ADAPTER_REQUEST ->
                "Adapter request" + adapterName?.let { ": $it" }.orEmpty()
        }
        return raw.take(120)
    }

    private fun render(payload: ReportPayload): String = buildString {
        appendLine("## FossScout contribution report")
        appendLine()
        appendLine("**Report:** ${payload.kind.displayName()}")
        appendLine(
            "**App:** ${payload.appVersionName}" +
                payload.appVersionCode?.let { " ($it)" }.orEmpty(),
        )
        appendLine("**Profile:** ${payload.profile}")
        appendLine("**Feature:** ${payload.featureId}")
        appendLine("**Operation:** ${payload.operation.name}")
        appendLine("**Adapter type:** ${payload.adapterType?.name ?: "unknown"}")
        payload.store?.let { store ->
            store.name?.let { appendLine("**Store:** $it") }
            store.homepage?.let { appendLine("**Homepage:** $it") }
            store.indexUrl?.let { appendLine("**Index URL:** $it") }
            store.apiUrl?.let { appendLine("**API URL:** $it") }
            store.repositoryUrl?.let { appendLine("**Repository URL:** $it") }
            appendLine(
                "**Pinned fingerprint present:** " +
                    (if (store.hasPinnedFingerprint) "yes" else "no"),
            )
        }
        payload.errorClass?.let { appendLine("**Error type:** ${it.displayName()}") }
        payload.errorMessage?.let { appendLine("**Error/details:** $it") }
        payload.packageName?.let { appendLine("**Package:** $it") }
        payload.expectedStore?.let { appendLine("**Expected store:** $it") }
        payload.observedResult?.let { appendLine("**Observed result:** $it") }
        if (payload.coverage.isNotEmpty()) {
            appendLine("**Coverage:**")
            payload.coverage.forEach { coverage ->
                val reason = coverage.reason?.let { " ($it)" }.orEmpty()
                appendLine("- ${coverage.storeName}: ${coverage.status}$reason")
            }
        }
        appendLine("**Created (UTC):** ${formatUtc(payload.createdAtEpochMillis)}")
        appendLine()
        appendLine(
            "Privacy: this report is redacted and is not intended to include " +
                "installed-app inventory, notes, device identifiers, account data, " +
                "credentials, query tokens, or local paths.",
        )
        if (payload.debugDetails.isNotEmpty()) {
            appendLine()
            appendLine("### Debug details (opt-in, redacted)")
            payload.debugDetails.forEach { detail -> appendLine("- $detail") }
        }
    }

    private fun debugDetails(payload: ReportPayload): List<String> = buildList {
        add("App version code: ${payload.appVersionCode ?: "unknown"}")
        add("Build profile: ${payload.profile}")
        add("Feature/operation: ${payload.featureId}/${payload.operation.name}")
        add("Adapter: ${payload.adapterType?.name ?: "unknown"}")
        payload.errorClass?.let { add("Error class: ${it.name}") }
        if (payload.coverage.isNotEmpty()) {
            add(
                "Coverage: " + payload.coverage.joinToString { coverage ->
                    "${coverage.storeName}=${coverage.status}"
                },
            )
        }
        add(
            "No logcat, installed-app inventory, notes, device identifiers, " +
                "account data, credentials, query tokens, or local paths are included.",
        )
    }

    private fun formatUtc(epochMillis: Long): String =
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }.format(Date(epochMillis))

    private fun ReportKind.displayName(): String = when (this) {
        ReportKind.MISSING_STORE -> "Missing store"
        ReportKind.WRONG_MATCH -> "Wrong match"
        ReportKind.ADAPTER_REQUEST -> "Adapter request"
    }

    private fun ReportErrorClass.displayName(): String = when (this) {
        ReportErrorClass.STORE_VALIDATION -> "Store validation"
        ReportErrorClass.STORE_SYNC -> "Store sync"
        ReportErrorClass.MATCH -> "Match"
        ReportErrorClass.ADAPTER_UNSUPPORTED -> "Adapter unsupported"
        ReportErrorClass.OTHER -> "Other"
    }
}
