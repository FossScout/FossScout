package com.fossscout.core.feature

enum class FeatureId {
    INVENTORY, MATCHING, ALTERNATIVES, STORES, INDEX_CACHE, COVERAGE,
    REPLACEMENT_GUIDANCE, COMPANION_HANDOFF, STANDALONE, INSTALL_PROMPT,
    FAVOURITES, SHARING, BACKUP, SECURITY, UPDATES, CONTRIBUTION, SETTINGS,
}

data class FeatureDescriptor(
    val id: FeatureId,
    val availability: Availability,
    val requiredCapabilities: Set<Capability>,
    val enhancedBy: Set<Capability> = emptySet(),
    val defaultEnabled: Boolean = true,
    val settings: List<FeatureSetting> = emptyList(),
) {
    init {
        val duplicateKeys = settings.groupingBy(FeatureSetting::key)
            .eachCount()
            .filterValues { count -> count > 1 }
            .keys
        require(duplicateKeys.isEmpty()) {
            "Duplicate setting keys for ${id.name}: ${duplicateKeys.joinToString()}"
        }
    }
}

data class BuildProfile(val name: String, val capabilities: Set<Capability>)

data class FeatureState(
    val descriptor: FeatureDescriptor,
    val available: Boolean,
    val enhanced: Boolean,
    val enabled: Boolean,
    val missingCapabilities: Set<Capability>,
    val settingValues: Map<String, FeatureSettingValue> = emptyMap(),
)
