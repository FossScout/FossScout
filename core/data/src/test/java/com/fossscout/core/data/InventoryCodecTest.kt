package com.fossscout.core.data

import com.fossscout.core.inventory.AppProfile
import com.fossscout.core.inventory.Classification
import com.fossscout.core.inventory.InstalledApp
import com.fossscout.core.inventory.InventoryCoverage
import com.fossscout.core.inventory.InventoryEntry
import com.fossscout.core.inventory.InventorySnapshot
import com.fossscout.core.inventory.InventoryState
import com.fossscout.core.inventory.InventoryStatus
import com.fossscout.core.inventory.ScanMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class InventoryCodecTest {
    private val codec = InventoryCodec()

    @Test
    fun `round trip preserves snapshot timestamp and hide list`() {
        val state = InventoryState(
            snapshot = InventorySnapshot(
                scanMode = ScanMode.FULL,
                capturedAtEpochMillis = 123L,
                entries = listOf(
                    InventoryEntry(
                        identity = "org.example.app",
                        app = InstalledApp(
                            packageName = "org.example.app",
                            label = "Example",
                            versionName = "1.2",
                            versionCode = 12L,
                            installerPackage = "com.example.store",
                            signerSha256 = "abc",
                            isSystemApp = true,
                            isEnabled = false,
                            profile = AppProfile(userId = 10, isWorkProfile = true),
                        ),
                        classification = Classification.FOSS,
                    ),
                ),
                status = InventoryStatus.PARTIAL,
                coverage = InventoryCoverage(1, 1, 2),
            ),
            hiddenPackages = setOf("org.example.hidden"),
        )

        assertEquals(state, codec.decode(codec.encode(state)))
    }

    @Test
    fun `corrupt json raises a typed format error`() {
        assertThrows(InventoryFormatException::class.java) {
            codec.decode("""{"schemaVersion":1,"state":[""")
        }
    }

    @Test
    fun `unversioned state is rejected`() {
        assertThrows(InventoryFormatException::class.java) {
            codec.decode("""{"state":{}}""")
        }
    }

    @Test
    fun `unknown envelope version is rejected`() {
        assertThrows(InventoryFormatException::class.java) {
            codec.decode("""{"schemaVersion":99,"state":{}}""")
        }
    }

    @Test
    fun `unknown snapshot version is rejected`() {
        val encoded = """
            {
              "schemaVersion": 1,
              "state": {
                "snapshot": {
                  "scanMode": "FULL",
                  "capturedAtEpochMillis": 1,
                  "schemaVersion": 99
                }
              }
            }
        """.trimIndent()

        assertThrows(InventoryFormatException::class.java) {
            codec.decode(encoded)
        }
    }
}
