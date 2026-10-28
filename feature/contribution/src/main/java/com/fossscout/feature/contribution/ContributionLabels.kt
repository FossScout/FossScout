package com.fossscout.feature.contribution

import androidx.annotation.StringRes
import com.fossscout.core.contribution.ReportErrorClass
import com.fossscout.core.contribution.ReportKind
import com.fossscout.core.store.AdapterType

@StringRes
internal fun ReportKind.titleRes(): Int = when (this) {
    ReportKind.MISSING_STORE -> R.string.contribution_kind_missing_store
    ReportKind.WRONG_MATCH -> R.string.contribution_kind_wrong_match
    ReportKind.ADAPTER_REQUEST -> R.string.contribution_kind_adapter
}

@StringRes
internal fun ReportKind.descriptionRes(): Int = when (this) {
    ReportKind.MISSING_STORE -> R.string.contribution_kind_missing_store_body
    ReportKind.WRONG_MATCH -> R.string.contribution_kind_wrong_match_body
    ReportKind.ADAPTER_REQUEST -> R.string.contribution_kind_adapter_body
}

@StringRes
internal fun ReportErrorClass.labelRes(): Int = when (this) {
    ReportErrorClass.STORE_VALIDATION -> R.string.contribution_error_store_validation
    ReportErrorClass.STORE_SYNC -> R.string.contribution_error_store_sync
    ReportErrorClass.MATCH -> R.string.contribution_error_match
    ReportErrorClass.ADAPTER_UNSUPPORTED -> R.string.contribution_error_adapter
    ReportErrorClass.OTHER -> R.string.contribution_error_other
}

@StringRes
internal fun AdapterType.labelRes(): Int = when (this) {
    AdapterType.FDROID_V2 -> R.string.contribution_adapter_fdroid_v2
    AdapterType.FDROID_V1 -> R.string.contribution_adapter_fdroid_v1
    AdapterType.LIVE_API -> R.string.contribution_adapter_live_api
    AdapterType.GENERIC_JSON -> R.string.contribution_adapter_generic_json
    AdapterType.SOURCE_REPOSITORY -> R.string.contribution_adapter_source_repository
    AdapterType.DEEP_LINK -> R.string.contribution_adapter_deep_link
}

@StringRes
internal fun ContributionError.messageRes(): Int = when (this) {
    ContributionError.INVALID_REPORT -> R.string.contribution_error_invalid
    ContributionError.PREVIEW_FAILED -> R.string.contribution_error_preview
}
