package com.fossscout.core.feature

class FeatureRegistry(
    private val profile: BuildProfile,
    private val descriptors: List<FeatureDescriptor>,
    private val userDisabled: Set<FeatureId> = emptySet(),
) {
    private val descriptorsById: Map<FeatureId, FeatureDescriptor> =
        descriptors.associateBy(FeatureDescriptor::id)

    fun state(id: FeatureId): FeatureState {
        val descriptor = descriptorsById[id]
            ?: throw IllegalArgumentException("Unknown feature id: $id")

        val available = descriptor.requiredCapabilities.all(profile.capabilities::contains)
        val enhanced = descriptor.enhancedBy.isNotEmpty() &&
            descriptor.enhancedBy.all(profile.capabilities::contains)
        val enabled = available && descriptor.defaultEnabled && id !in userDisabled

        return FeatureState(
            descriptor = descriptor,
            available = available,
            enhanced = enhanced,
            enabled = enabled,
            unavailableReason = if (available) null else unavailableReason(descriptor),
        )
    }

    fun availableFeatures(): List<FeatureDescriptor> =
        descriptors.filter { state(it.id).available }

    private fun unavailableReason(descriptor: FeatureDescriptor): String {
        val missing = (descriptor.requiredCapabilities - profile.capabilities)
            .sortedBy(Capability::name)
        return buildString {
            append("Not available in this build")
            if (missing.isNotEmpty()) {
                append(": missing ")
                append(missing.joinToString { it.name })
            }
        }
    }
}
