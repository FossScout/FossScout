package com.fossscout.core.feature

const val FEATURE_SETTINGS_SCHEMA_VERSION = 1

/**
 * Immutable, versioned feature configuration. This is the read-only snapshot
 * that F13 can include in a full backup.
 */
data class FeatureSettings(
    val schemaVersion: Int = FEATURE_SETTINGS_SCHEMA_VERSION,
    val disabledFeatureIds: Set<FeatureId> = emptySet(),
    val values: Map<FeatureId, Map<String, FeatureSettingValue>> = emptyMap(),
) {
    fun valueFor(featureId: FeatureId, setting: FeatureSetting): FeatureSettingValue =
        values[featureId]?.get(setting.key)?.takeIf(setting::validate) ?: setting.defaultValue

    fun valuesFor(featureId: FeatureId): Map<String, FeatureSettingValue> =
        values[featureId].orEmpty()
}
