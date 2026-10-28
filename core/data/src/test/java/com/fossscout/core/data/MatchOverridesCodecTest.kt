package com.fossscout.core.data

import com.fossscout.core.model.AppKey
import com.fossscout.core.model.MatchOverride
import com.fossscout.core.model.MatchOverrideTarget
import com.fossscout.core.model.StoreId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class MatchOverridesCodecTest {
    private val codec = MatchOverridesCodec()
    private val override = MatchOverride(
        installedAppKey = AppKey("org.example.app"),
        target = MatchOverrideTarget(
            appKey = AppKey("org.example.alt"),
            displayName = "Alternative",
            storeId = StoreId("store"),
            storeName = "Store",
        ),
        note = "private",
        createdAtEpochMillis = 1,
        updatedAtEpochMillis = 2,
    )

    @Test
    fun `round trip preserves overrides and schema version`() {
        val decoded = codec.decode(codec.encode(listOf(override)))

        assertEquals(listOf(override), decoded)
    }

    @Test
    fun `future envelope version is rejected`() {
        val error = assertThrows(MatchOverridesFormatException::class.java) {
            codec.decode("""{"schemaVersion":99,"overrides":[]}""")
        }

        assertTrue(error.message.orEmpty().contains("99"))
    }

    @Test
    fun `duplicate app keys are rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            codec.encode(listOf(override, override))
        }
        assertThrows(MatchOverridesFormatException::class.java) {
            codec.decode(
                """
                {
                  "schemaVersion": 1,
                  "overrides": [
                    {
                      "installedAppKey": "org.example.app",
                      "note": "",
                      "createdAtEpochMillis": 1,
                      "updatedAtEpochMillis": 2,
                      "schemaVersion": 1
                    },
                    {
                      "installedAppKey": "org.example.app",
                      "note": "",
                      "createdAtEpochMillis": 1,
                      "updatedAtEpochMillis": 2,
                      "schemaVersion": 1
                    }
                  ]
                }
                """.trimIndent(),
            )
        }
    }

    @Test
    fun `malformed data raises instead of looking empty`() {
        assertThrows(MatchOverridesFormatException::class.java) {
            codec.decode("""{"schemaVersion":1,"overrides":[""")
        }
    }
}
