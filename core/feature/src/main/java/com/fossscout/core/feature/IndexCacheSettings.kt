package com.fossscout.core.feature

/** Settings declared by the offline index cache feature. */
object IndexCacheSettings {
    const val STORAGE_BUDGET_MB = "storage_budget_mb"
    const val DEFAULT_STORAGE_BUDGET_MB = 256
    const val MIN_STORAGE_BUDGET_MB = 16
    const val MAX_STORAGE_BUDGET_MB = 2048

    val storageBudget: FeatureSetting = FeatureSetting(
        key = STORAGE_BUDGET_MB,
        section = FeatureSettingSection.ADVANCED,
        defaultValue = FeatureSettingValue.IntegerValue(DEFAULT_STORAGE_BUDGET_MB),
        validation = FeatureSettingValidation.IntegerConstraints(
            min = MIN_STORAGE_BUDGET_MB,
            max = MAX_STORAGE_BUDGET_MB,
        ),
    )
}
