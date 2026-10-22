package com.fossscout.core.feature

enum class FeatureId {
    INVENTORY, MATCHING, ALTERNATIVES, STORES, INDEX_CACHE, COVERAGE,
    REPLACEMENT_GUIDANCE, COMPANION_HANDOFF, STANDALONE, INSTALL_PROMPT,
    FAVOURITES, SHARING, BACKUP, SECURITY, UPDATES, CONTRIBUTION, SETTINGS,
}

data class FeatureDescriptor(
    val id: FeatureId,
    val title: String,
    val availability: Availability,
    val requiredCapabilities: Set<Capability>,
    val enhancedBy: Set<Capability> = emptySet(),
    val defaultEnabled: Boolean = true,
)

data class BuildProfile(val name: String, val capabilities: Set<Capability>)

data class FeatureState(
    val descriptor: FeatureDescriptor,
    val available: Boolean,
    val enhanced: Boolean,
    val enabled: Boolean,
    val unavailableReason: String?,
)
