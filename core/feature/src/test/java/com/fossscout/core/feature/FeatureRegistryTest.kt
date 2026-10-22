package com.fossscout.core.feature

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FeatureRegistryTest {
    private val inventory = FeatureDescriptor(
        id = FeatureId.INVENTORY,
        title = "Installed apps",
        availability = Availability.GENERAL_WITH_RESTRICTED_ENHANCEMENT,
        requiredCapabilities = setOf(Capability.MANUAL_PACKAGE_SELECTION),
        enhancedBy = setOf(Capability.BROAD_PACKAGE_VISIBILITY),
    )

    @Test
    fun `restricted enhancement is unavailable without the capability`() {
        val play = BuildProfile("playstore", setOf(Capability.MANUAL_PACKAGE_SELECTION))
        val registry = FeatureRegistry(play, listOf(inventory))
        val state = registry.state(FeatureId.INVENTORY)
        assertTrue(state.available)
        assertFalse(state.enhanced)
    }

    @Test
    fun `restricted feature is unavailable in the play profile`() {
        val standalone = FeatureDescriptor(
            id = FeatureId.STANDALONE,
            title = "Standalone install",
            availability = Availability.RESTRICTED,
            requiredCapabilities = setOf(Capability.DIRECT_APK_INSTALL),
        )
        val play = BuildProfile("playstore", setOf(Capability.MANUAL_PACKAGE_SELECTION))
        val state = FeatureRegistry(play, listOf(standalone)).state(FeatureId.STANDALONE)
        assertFalse(state.available)
        assertTrue(state.unavailableReason!!.contains("build", ignoreCase = true))
    }
}
