package com.fossscout.core.feature

/**
 * The single authoritative source for feature availability and enabled state.
 * Profiles only supply capabilities; they never carry a competing feature list.
 */
class FeatureRegistry(
    private val profile: BuildProfile,
    private val descriptors: List<FeatureDescriptor>,
    private val userDisabled: Set<FeatureId> = emptySet(),
    private val storedValues: Map<FeatureId, Map<String, FeatureSettingValue>> = emptyMap(),
) {
    private val descriptorsById: Map<FeatureId, FeatureDescriptor> =
        descriptors.associateBy(FeatureDescriptor::id)

    fun state(id: FeatureId): FeatureState {
        val descriptor = descriptorsById[id]
            ?: throw IllegalArgumentException("Unknown feature id: $id")

        val missingCapabilities = descriptor.requiredCapabilities - profile.capabilities
        val available = missingCapabilities.isEmpty()
        val enhanced = descriptor.enhancedBy.isNotEmpty() &&
            descriptor.enhancedBy.all(profile.capabilities::contains)
        val enabled = available && descriptor.defaultEnabled && id !in userDisabled
        val settingValues = descriptor.settings.associate { setting ->
            setting.key to (
                storedValues[id]?.get(setting.key)?.takeIf(setting::validate)
                    ?: setting.defaultValue
                )
        }

        return FeatureState(
            descriptor = descriptor,
            available = available,
            enhanced = enhanced,
            enabled = enabled,
            missingCapabilities = missingCapabilities,
            settingValues = settingValues,
        )
    }

    fun states(): List<FeatureState> = descriptors.map { state(it.id) }

    fun availableFeatures(): List<FeatureDescriptor> =
        descriptors.filter { state(it.id).available }

    fun enabledFeatureIds(): Set<FeatureId> =
        states().filter(FeatureState::enabled).map { it.descriptor.id }.toSet()
}
