package com.fossscout.core.contribution

import com.fossscout.core.feature.FeatureId
import com.fossscout.core.matching.CoverageSnapshot
import com.fossscout.core.store.AdapterType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

const val CONTRIBUTION_SCHEMA_VERSION = 1

/** The user-facing reason for a contribution report. */
@Serializable
enum class ReportKind {
    @SerialName("missing_store")
    MISSING_STORE,

    @SerialName("wrong_match")
    WRONG_MATCH,

    @SerialName("adapter_request")
    ADAPTER_REQUEST,
}

/** The operation that produced the report. Kept separate from [ReportKind]. */
@Serializable
enum class ReportOperation {
    @SerialName("add_store")
    ADD_STORE,

    @SerialName("match_app")
    MATCH_APP,

    @SerialName("request_adapter")
    REQUEST_ADAPTER,
}

/** Stable, safe error classes. Free-form stack traces are never included. */
@Serializable
enum class ReportErrorClass {
    @SerialName("store_validation")
    STORE_VALIDATION,

    @SerialName("store_sync")
    STORE_SYNC,

    @SerialName("match")
    MATCH,

    @SerialName("adapter_unsupported")
    ADAPTER_UNSUPPORTED,

    @SerialName("other")
    OTHER,
}

/** Build-time information attached to every report. */
data class ContributionEnvironment(
    val appVersionName: String,
    val appVersionCode: Long? = null,
    val profile: String,
) {
    init {
        require(appVersionName.isNotBlank()) { "App version is blank" }
        require(profile.isNotBlank()) { "Build profile is blank" }
        require(appVersionCode == null || appVersionCode >= 0) {
            "App version code must not be negative"
        }
    }
}

/**
 * User-entered store details before redaction. There is deliberately no
 * fingerprint, trust note, health record, or free-form inventory field here.
 */
data class ReportStoreInput(
    val name: String = "",
    val homepage: String = "",
    val adapterType: AdapterType? = null,
    val indexUrl: String = "",
    val apiUrl: String = "",
    val repositoryUrl: String = "",
    val hasPinnedFingerprint: Boolean = false,
)

/**
 * Context used to prefill the contribution form. The app may build this from a
 * match decision or a store editor, but it must never include notes or inventory.
 */
data class ContributionContext(
    val kind: ReportKind = ReportKind.MISSING_STORE,
    val featureId: FeatureId = FeatureId.STORES,
    val operation: ReportOperation = ReportOperation.ADD_STORE,
    val store: ReportStoreInput? = null,
    val adapterType: AdapterType? = null,
    val errorClass: ReportErrorClass? = ReportErrorClass.STORE_VALIDATION,
    val errorMessage: String? = null,
    val packageName: String? = null,
    val expectedStore: String? = null,
    val observedResult: String? = null,
    val coverage: CoverageSnapshot? = null,
) {
    companion object
}

/** A store descriptor after credentials, tokens, and private fields are removed. */
@Serializable
data class ReportStoreDescriptor(
    val name: String? = null,
    val homepage: String? = null,
    val adapterType: AdapterType? = null,
    val indexUrl: String? = null,
    val apiUrl: String? = null,
    val repositoryUrl: String? = null,
    val hasPinnedFingerprint: Boolean = false,
) {
    val isEmpty: Boolean
        get() = adapterType == null &&
            listOf(name, homepage, indexUrl, apiUrl, repositoryUrl).all { it.isNullOrBlank() }
}

/** Store status only: no package counts, timestamps, or free-form detail. */
@Serializable
data class ReportCoverageSummary(
    val storeName: String,
    val status: String,
    val reason: String? = null,
)

/**
 * The complete payload that may leave the device. It intentionally has no
 * inventory, notes, account, device identifier, or raw error fields.
 */
@Serializable
data class ReportPayload(
    val schemaVersion: Int = CONTRIBUTION_SCHEMA_VERSION,
    val kind: ReportKind,
    val appVersionName: String,
    val appVersionCode: Long? = null,
    val profile: String,
    val featureId: String,
    val operation: ReportOperation,
    val store: ReportStoreDescriptor? = null,
    val adapterType: AdapterType? = null,
    val errorClass: ReportErrorClass? = null,
    val errorMessage: String? = null,
    val packageName: String? = null,
    val expectedStore: String? = null,
    val observedResult: String? = null,
    val coverage: List<ReportCoverageSummary> = emptyList(),
    val createdAtEpochMillis: Long,
    val debugDetails: List<String> = emptyList(),
) {
    init {
        require(schemaVersion == CONTRIBUTION_SCHEMA_VERSION) {
            "Unsupported contribution schema version: $schemaVersion"
        }
        require(appVersionName.isNotBlank()) { "App version is blank" }
        require(profile.isNotBlank()) { "Build profile is blank" }
        require(featureId.isNotBlank()) { "Feature id is blank" }
        require(createdAtEpochMillis >= 0) { "Created timestamp must not be negative" }
    }
}

/** Input to the pure report builder. */
data class ContributionRequest(
    val environment: ContributionEnvironment,
    val context: ContributionContext,
    val createdAtEpochMillis: Long,
    val includeDebugDetails: Boolean = false,
)

/** Everything the preview needs, including the exact text and JSON to export. */
data class ContributionReport(
    val payload: ReportPayload,
    val title: String,
    val body: String,
    val json: String,
    val issueUrl: String,
)
