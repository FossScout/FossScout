package com.fossscout.feature.settings

import androidx.annotation.StringRes
import com.fossscout.core.feature.Capability
import com.fossscout.core.feature.FeatureId
import com.fossscout.core.feature.FeatureSetting
import com.fossscout.core.feature.IndexCacheSettings

@StringRes
internal fun FeatureId.titleRes(): Int = when (this) {
    FeatureId.INVENTORY -> R.string.feature_inventory_title
    FeatureId.MATCHING -> R.string.feature_matching_title
    FeatureId.ALTERNATIVES -> R.string.feature_alternatives_title
    FeatureId.STORES -> R.string.feature_stores_title
    FeatureId.INDEX_CACHE -> R.string.feature_index_cache_title
    FeatureId.COVERAGE -> R.string.feature_coverage_title
    FeatureId.REPLACEMENT_GUIDANCE -> R.string.feature_replacement_guidance_title
    FeatureId.COMPANION_HANDOFF -> R.string.feature_companion_handoff_title
    FeatureId.STANDALONE -> R.string.feature_standalone_title
    FeatureId.INSTALL_PROMPT -> R.string.feature_install_prompt_title
    FeatureId.FAVOURITES -> R.string.feature_favourites_title
    FeatureId.SHARING -> R.string.feature_sharing_title
    FeatureId.BACKUP -> R.string.feature_backup_title
    FeatureId.SECURITY -> R.string.feature_security_title
    FeatureId.UPDATES -> R.string.feature_updates_title
    FeatureId.CONTRIBUTION -> R.string.feature_contribution_title
    FeatureId.SETTINGS -> R.string.feature_settings_title
}

/**
 * Settings labels are resolved by stable key so declarations stay free of
 * Android resources. Add a case here when a feature declares a real setting.
 */
@StringRes
internal fun FeatureSetting.titleRes(featureId: FeatureId): Int = when (featureId to key) {
    FeatureId.INDEX_CACHE to IndexCacheSettings.STORAGE_BUDGET_MB ->
        R.string.settings_storage_budget_title

    else -> R.string.settings_setting_title_fallback
}

@StringRes
internal fun Capability.labelRes(): Int = when (this) {
    Capability.BROAD_PACKAGE_VISIBILITY -> R.string.capability_broad_package_visibility
    Capability.MANUAL_PACKAGE_SELECTION -> R.string.capability_manual_package_selection
    Capability.ACCESSIBILITY_INSTALL_PROMPT -> R.string.capability_accessibility_install_prompt
    Capability.DIRECT_APK_INSTALL -> R.string.capability_direct_apk_install
    Capability.BACKGROUND_UPDATE_CHECK -> R.string.capability_background_update_check
    Capability.STORE_INDEX_NETWORK -> R.string.capability_store_index_network
    Capability.SHARE_IMPORT_EXPORT -> R.string.capability_share_import_export
    Capability.CUSTOM_STORE_ADAPTERS -> R.string.capability_custom_store_adapters
    Capability.SOURCE_REPOSITORY_ADAPTERS -> R.string.capability_source_repository_adapters
    Capability.ACCESSIBILITY_SERVICE -> R.string.capability_accessibility_service
}
