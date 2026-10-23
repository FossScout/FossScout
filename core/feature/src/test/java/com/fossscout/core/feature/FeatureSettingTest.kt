package com.fossscout.core.feature

import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class FeatureSettingTest {
    @Test
    fun `blank keys are rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            FeatureSetting(
                key = " ",
                section = FeatureSettingSection.BASIC,
                defaultValue = FeatureSettingValue.BooleanValue(false),
            )
        }
    }

    @Test
    fun `duplicate setting keys are rejected by the descriptor`() {
        val setting = FeatureSetting(
            key = "duplicate",
            section = FeatureSettingSection.BASIC,
            defaultValue = FeatureSettingValue.BooleanValue(false),
        )

        assertThrows(IllegalArgumentException::class.java) {
            FeatureDescriptor(
                id = FeatureId.FAVOURITES,
                availability = Availability.GENERAL,
                requiredCapabilities = emptySet(),
                settings = listOf(setting, setting),
            )
        }
    }

    @Test
    fun `text constraints validate length and blank values`() {
        val setting = FeatureSetting(
            key = "prefix",
            section = FeatureSettingSection.BASIC,
            defaultValue = FeatureSettingValue.TextValue("home"),
            validation = FeatureSettingValidation.TextConstraints(
                maxLength = 4,
                allowBlank = false,
            ),
        )

        assertTrue(setting.validate(FeatureSettingValue.TextValue("work")))
        assertFalse(setting.validate(FeatureSettingValue.TextValue("")))
        assertFalse(setting.validate(FeatureSettingValue.TextValue("longer")))
        assertFalse(setting.validate(FeatureSettingValue.BooleanValue(true)))
    }

    @Test
    fun `default value must satisfy the declared validation`() {
        assertThrows(IllegalArgumentException::class.java) {
            FeatureSetting(
                key = "prefix",
                section = FeatureSettingSection.ADVANCED,
                defaultValue = FeatureSettingValue.TextValue("too long"),
                validation = FeatureSettingValidation.TextConstraints(maxLength = 3),
            )
        }
    }

    @Test
    fun `validation must match the default value type`() {
        assertThrows(IllegalArgumentException::class.java) {
            FeatureSetting(
                key = "flag",
                section = FeatureSettingSection.BASIC,
                defaultValue = FeatureSettingValue.BooleanValue(true),
                validation = FeatureSettingValidation.TextConstraints(),
            )
        }
    }
}
