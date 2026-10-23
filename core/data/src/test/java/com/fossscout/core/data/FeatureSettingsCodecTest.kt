package com.fossscout.core.data

import com.fossscout.core.feature.FEATURE_SETTINGS_SCHEMA_VERSION
import com.fossscout.core.feature.FeatureId
import com.fossscout.core.feature.FeatureSettingValue
import com.fossscout.core.feature.FeatureSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class FeatureSettingsCodecTest {
    private val codec = FeatureSettingsCodec()

    @Test
    fun `round trip preserves disabled features and values`() {
        val settings = FeatureSettings(
            disabledFeatureIds = setOf(FeatureId.SHARING, FeatureId.BACKUP),
            values = mapOf(
                FeatureId.FAVOURITES to mapOf(
                    "note_prefix" to FeatureSettingValue.TextValue("home \"work\""),
                ),
                FeatureId.SHARING to mapOf(
                    "confirm" to FeatureSettingValue.BooleanValue(true),
                ),
            ),
        )

        val decoded = codec.decode(codec.encode(settings))

        assertEquals(FEATURE_SETTINGS_SCHEMA_VERSION, decoded.schemaVersion)
        assertEquals(settings.disabledFeatureIds, decoded.disabledFeatureIds)
        assertEquals(settings.values, decoded.values)
    }

    @Test
    fun `unknown ids types and malformed values are ignored`() {
        val encoded = """
            {
              "schemaVersion": $FEATURE_SETTINGS_SCHEMA_VERSION,
              "disabledFeatureIds": ["SHARING", "NOT_A_FEATURE"],
              "values": [
                {
                  "featureId": "FAVOURITES",
                  "key": "note_prefix",
                  "type": "TEXT",
                  "value": "home"
                },
                {
                  "featureId": "NOT_A_FEATURE",
                  "key": "value",
                  "type": "TEXT",
                  "value": "ignored"
                },
                {
                  "featureId": "SHARING",
                  "key": "confirm",
                  "type": "BOOLEAN",
                  "value": "not-a-boolean"
                },
                {
                  "featureId": "SHARING",
                  "key": "unknown_type",
                  "type": "NUMBER",
                  "value": "1"
                }
              ]
            }
        """.trimIndent()

        val decoded = codec.decode(encoded)

        assertEquals(setOf(FeatureId.SHARING), decoded.disabledFeatureIds)
        assertEquals(
            mapOf(
                FeatureId.FAVOURITES to mapOf(
                    "note_prefix" to FeatureSettingValue.TextValue("home"),
                ),
            ),
            decoded.values,
        )
    }

    @Test
    fun `unsupported schema version is rejected`() {
        val encoded = """
            {
              "schemaVersion": ${FEATURE_SETTINGS_SCHEMA_VERSION + 1},
              "disabledFeatureIds": [],
              "values": []
            }
        """.trimIndent()

        val error = assertThrows(FeatureSettingsFormatException::class.java) {
            codec.decode(encoded)
        }

        assertTrue(error.message!!.contains("schema version"))
    }

    @Test
    fun `malformed data is rejected`() {
        assertThrows(FeatureSettingsFormatException::class.java) {
            codec.decode("{not json")
        }
    }

    @Test
    fun `legacy disabled features migrate without losing known ids`() {
        val migrated = migrateLegacyDisabledFeatures(
            setOf("SHARING", "FAVOURITES", "NOT_A_FEATURE"),
        )

        assertEquals(FEATURE_SETTINGS_SCHEMA_VERSION, migrated.schemaVersion)
        assertEquals(setOf(FeatureId.SHARING, FeatureId.FAVOURITES), migrated.disabledFeatureIds)
        assertTrue(migrated.values.isEmpty())
    }
}
