package com.fossscout.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class MatchOverrideTest {
    @Test
    fun `null target represents no suitable match`() {
        val override = MatchOverride(
            installedAppKey = AppKey("org.example.app"),
            target = null,
            createdAtEpochMillis = 1,
            updatedAtEpochMillis = 2,
        )

        assertTrue(override.isNoMatch)
    }

    @Test
    fun `target requires a display name and store name`() {
        assertThrows(IllegalArgumentException::class.java) {
            MatchOverrideTarget(
                appKey = AppKey("org.example.alt"),
                displayName = " ",
                storeId = StoreId("store"),
                storeName = "Store",
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            MatchOverrideTarget(
                appKey = AppKey("org.example.alt"),
                displayName = "Alternative",
                storeId = StoreId("store"),
                storeName = " ",
            )
        }
    }

    @Test
    fun `note length is capped`() {
        val override = MatchOverride(
            installedAppKey = AppKey("org.example.app"),
            note = "x".repeat(MAX_NOTE_LENGTH + 1),
            createdAtEpochMillis = 1,
            updatedAtEpochMillis = 2,
        )

        assertThrows(IllegalArgumentException::class.java) { override.validate() }
    }

    @Test
    fun `unsupported schema version is rejected`() {
        val override = MatchOverride(
            installedAppKey = AppKey("org.example.app"),
            createdAtEpochMillis = 1,
            updatedAtEpochMillis = 2,
            schemaVersion = 99,
        )

        val error = assertThrows(IllegalArgumentException::class.java) {
            override.validate()
        }
        assertEquals(
            "Unsupported match override schema version: 99",
            error.message,
        )
    }
}
