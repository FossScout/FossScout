package com.fossscout.core.feature

import org.junit.Assert.assertEquals
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

    @Test
    fun `integer constraints validate bounds and the value type`() {
        val setting = FeatureSetting(
            key = "budget",
            section = FeatureSettingSection.ADVANCED,
            defaultValue = FeatureSettingValue.IntegerValue(256),
            validation = FeatureSettingValidation.IntegerConstraints(min = 16, max = 2_048),
        )

        assertTrue(setting.validate(FeatureSettingValue.IntegerValue(16)))
        assertTrue(setting.validate(FeatureSettingValue.IntegerValue(2_048)))
        assertFalse(setting.validate(FeatureSettingValue.IntegerValue(15)))
        assertFalse(setting.validate(FeatureSettingValue.IntegerValue(2_049)))
        assertFalse(setting.validate(FeatureSettingValue.BooleanValue(true)))
        assertEquals(FeatureSettingType.INTEGER, setting.type)
    }

    @Test
    fun `integer default must satisfy its constraints`() {
        assertThrows(IllegalArgumentException::class.java) {
            FeatureSetting(
                key = "budget",
                section = FeatureSettingSection.ADVANCED,
                defaultValue = FeatureSettingValue.IntegerValue(4),
                validation = FeatureSettingValidation.IntegerConstraints(min = 16),
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            FeatureSettingValidation.IntegerConstraints(min = 10, max = 1)
        }
    }

    @Test
    fun `index cache storage budget is a real bounded integer setting`() {
        val setting = IndexCacheSettings.storageBudget

        assertEquals(FeatureSettingType.INTEGER, setting.type)
        assertEquals(FeatureSettingSection.ADVANCED, setting.section)
        assertEquals(
            FeatureSettingValue.IntegerValue(256),
            setting.defaultValue,
        )
        assertTrue(
            setting.validate(
                FeatureSettingValue.IntegerValue(IndexCacheSettings.MIN_STORAGE_BUDGET_MB),
            ),
        )
        assertTrue(
            setting.validate(
                FeatureSettingValue.IntegerValue(IndexCacheSettings.MAX_STORAGE_BUDGET_MB),
            ),
        )
        assertFalse(
            setting.validate(
                FeatureSettingValue.IntegerValue(IndexCacheSettings.MIN_STORAGE_BUDGET_MB - 1),
            ),
        )
        assertFalse(
            setting.validate(
                FeatureSettingValue.IntegerValue(IndexCacheSettings.MAX_STORAGE_BUDGET_MB + 1),
            ),
        )
    }
}
