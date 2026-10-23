package com.fossscout.core.feature

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class FeatureRegistryTest {
    private val inventory = FeatureDescriptor(
        id = FeatureId.INVENTORY,
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
            availability = Availability.RESTRICTED,
            requiredCapabilities = setOf(Capability.DIRECT_APK_INSTALL),
        )
        val play = BuildProfile("playstore", setOf(Capability.MANUAL_PACKAGE_SELECTION))
        val state = FeatureRegistry(play, listOf(standalone)).state(FeatureId.STANDALONE)
        assertFalse(state.available)
        assertTrue(state.missingCapabilities.contains(Capability.DIRECT_APK_INSTALL))
    }

    @Test
    fun `unknown feature id is rejected`() {
        val registry = FeatureRegistry(BuildProfile("playstore", emptySet()), emptyList())
        val error = assertThrows(IllegalArgumentException::class.java) {
            registry.state(FeatureId.INVENTORY)
        }
        assertTrue(error.message!!.contains(FeatureId.INVENTORY.name))
    }

    @Test
    fun `available features preserve descriptor order`() {
        val first = FeatureDescriptor(
            id = FeatureId.COVERAGE,
            availability = Availability.GENERAL,
            requiredCapabilities = emptySet(),
        )
        val unavailable = FeatureDescriptor(
            id = FeatureId.STANDALONE,
            availability = Availability.RESTRICTED,
            requiredCapabilities = setOf(Capability.DIRECT_APK_INSTALL),
        )
        val second = FeatureDescriptor(
            id = FeatureId.FAVOURITES,
            availability = Availability.GENERAL,
            requiredCapabilities = emptySet(),
        )
        val registry = FeatureRegistry(
            BuildProfile("playstore", emptySet()),
            listOf(first, unavailable, second),
        )
        assertEquals(listOf(first, second), registry.availableFeatures())
    }

    @Test
    fun `user-disabled feature is not enabled`() {
        val settings = FeatureDescriptor(
            id = FeatureId.SETTINGS,
            availability = Availability.GENERAL,
            requiredCapabilities = emptySet(),
        )
        val registry = FeatureRegistry(
            BuildProfile("playstore", emptySet()),
            listOf(settings),
            userDisabled = setOf(FeatureId.SETTINGS),
        )
        val state = registry.state(FeatureId.SETTINGS)
        assertTrue(state.available)
        assertFalse(state.enabled)
    }

    @Test
    fun `declared setting values are resolved with defaults`() {
        val setting = FeatureSetting(
            key = "prefix",
            section = FeatureSettingSection.BASIC,
            defaultValue = FeatureSettingValue.TextValue("default"),
        )
        val descriptor = FeatureDescriptor(
            id = FeatureId.FAVOURITES,
            availability = Availability.GENERAL,
            requiredCapabilities = emptySet(),
            settings = listOf(setting),
        )
        val registry = FeatureRegistry(
            profile = BuildProfile("playstore", emptySet()),
            descriptors = listOf(descriptor),
            storedValues = mapOf(
                FeatureId.FAVOURITES to mapOf(
                    setting.key to FeatureSettingValue.TextValue("custom"),
                ),
            ),
        )

        assertEquals(
            FeatureSettingValue.TextValue("custom"),
            registry.state(FeatureId.FAVOURITES).settingValues[setting.key],
        )
    }

    @Test
    fun `invalid stored setting values fall back to the declared default`() {
        val setting = FeatureSetting(
            key = "prefix",
            section = FeatureSettingSection.BASIC,
            defaultValue = FeatureSettingValue.TextValue("home"),
            validation = FeatureSettingValidation.TextConstraints(maxLength = 4),
        )
        val descriptor = FeatureDescriptor(
            id = FeatureId.FAVOURITES,
            availability = Availability.GENERAL,
            requiredCapabilities = emptySet(),
            settings = listOf(setting),
        )
        val registry = FeatureRegistry(
            profile = BuildProfile("playstore", emptySet()),
            descriptors = listOf(descriptor),
            storedValues = mapOf(
                FeatureId.FAVOURITES to mapOf(
                    setting.key to FeatureSettingValue.TextValue("too long"),
                ),
            ),
        )

        assertEquals(
            FeatureSettingValue.TextValue("home"),
            registry.state(FeatureId.FAVOURITES).settingValues[setting.key],
        )
    }

    @Test
    fun `states and enabled ids follow registry order`() {
        val first = FeatureDescriptor(
            id = FeatureId.COVERAGE,
            availability = Availability.GENERAL,
            requiredCapabilities = emptySet(),
        )
        val second = FeatureDescriptor(
            id = FeatureId.FAVOURITES,
            availability = Availability.GENERAL,
            requiredCapabilities = emptySet(),
        )
        val registry = FeatureRegistry(
            BuildProfile("playstore", emptySet()),
            listOf(first, second),
            userDisabled = setOf(FeatureId.FAVOURITES),
        )

        assertEquals(listOf(first, second), registry.states().map { it.descriptor })
        assertEquals(setOf(FeatureId.COVERAGE), registry.enabledFeatureIds())
    }
}
