package com.fossscout.core.feature

/** Where a declared setting appears in the generated settings screen. */
enum class FeatureSettingSection { BASIC, ADVANCED }

/** Value kinds supported by declared feature settings. */
enum class FeatureSettingType { BOOLEAN, TEXT }

sealed interface FeatureSettingValue {
    val type: FeatureSettingType

    data class BooleanValue(val value: Boolean) : FeatureSettingValue {
        override val type: FeatureSettingType get() = FeatureSettingType.BOOLEAN
    }

    data class TextValue(val value: String) : FeatureSettingValue {
        override val type: FeatureSettingType get() = FeatureSettingType.TEXT
    }
}

/**
 * Declarative validation so feature declarations stay free of functions and
 * Android resources. Add a new constraint only when a real setting needs it.
 */
sealed interface FeatureSettingValidation {
    data object None : FeatureSettingValidation

    data class TextConstraints(
        val maxLength: Int? = null,
        val allowBlank: Boolean = true,
    ) : FeatureSettingValidation {
        init {
            require(maxLength == null || maxLength >= 0) { "maxLength must not be negative" }
        }
    }
}

/**
 * A user-configurable setting declared by a feature. [key] is stable across
 * releases and unique within its descriptor; changing it breaks stored values.
 */
data class FeatureSetting(
    val key: String,
    val section: FeatureSettingSection,
    val defaultValue: FeatureSettingValue,
    val validation: FeatureSettingValidation = FeatureSettingValidation.None,
) {
    val type: FeatureSettingType get() = defaultValue.type

    init {
        require(key.isNotBlank()) { "Setting key must not be blank" }
        require(key == key.trim()) { "Setting key must not have surrounding whitespace" }
        require(validate(defaultValue)) { "Default value is invalid for setting $key" }
    }

    fun validate(value: FeatureSettingValue): Boolean {
        if (value.type != type) return false
        return when (val constraints = validation) {
            FeatureSettingValidation.None -> true

            is FeatureSettingValidation.TextConstraints ->
                value is FeatureSettingValue.TextValue &&
                    (constraints.allowBlank || value.value.isNotBlank()) &&
                    (constraints.maxLength == null || value.value.length <= constraints.maxLength)
        }
    }
}
