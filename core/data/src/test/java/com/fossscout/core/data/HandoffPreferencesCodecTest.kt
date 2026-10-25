package com.fossscout.core.data

import com.fossscout.core.handoff.HANDOFF_PREFERENCES_SCHEMA_VERSION
import com.fossscout.core.handoff.HandoffPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class HandoffPreferencesCodecTest {
    private val codec = HandoffPreferencesCodec()

    @Test
    fun `round trip preserves preferred stores`() {
        val preferences = HandoffPreferences(
            preferredStores = mapOf(
                "org.example.app" to "fdroid",
                "org.example.other" to "droidify",
            ),
        )

        assertEquals(preferences, codec.decode(codec.encode(preferences)))
    }

    @Test
    fun `empty preferences round trip`() {
        assertEquals(
            HandoffPreferences(),
            codec.decode(codec.encode(HandoffPreferences())),
        )
    }

    @Test
    fun `malformed data raises instead of silently clearing preferences`() {
        assertThrows(HandoffPreferencesFormatException::class.java) {
            codec.decode("{not-json")
        }
    }

    @Test
    fun `future schema version is rejected`() {
        val encoded = """
            {"schemaVersion":${HANDOFF_PREFERENCES_SCHEMA_VERSION + 1},"preferredStores":{}}
        """.trimIndent()

        assertThrows(HandoffPreferencesFormatException::class.java) {
            codec.decode(encoded)
        }
    }

    @Test
    fun `blank stored keys are rejected`() {
        val encoded = """
            {"schemaVersion":$HANDOFF_PREFERENCES_SCHEMA_VERSION,
             "preferredStores":{" ":"fdroid"}}
        """.trimIndent()

        assertThrows(HandoffPreferencesFormatException::class.java) {
            codec.decode(encoded)
        }
    }
}
