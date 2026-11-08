package com.fossscout.core.matching

import com.fossscout.core.inventory.InstalledApp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PermissionDiffTest {
    @Test
    fun `diff separates installed-only, alternative-only, and shared permissions`() {
        val comparison = comparePermissions(
            installed = InstalledApp(
                packageName = "org.example.old",
                label = "Old app",
                requestedPermissions = listOf(
                    CAMERA,
                    INTERNET,
                    READ_CONTACTS,
                ),
            ),
            alternative = PermissionAlternative(
                label = "New app",
                packageName = "org.example.new",
                permissions = listOf(CAMERA, INTERNET, RECORD_AUDIO),
                storeName = "F-Droid",
                relationship = PermissionRelationship.DIFFERENT_APP,
            ),
        )

        val diff = requireNotNull(comparison.diff)
        assertEquals(listOf(READ_CONTACTS), diff.installedOnly.map(PermissionDetail::name))
        assertEquals(listOf(RECORD_AUDIO), diff.alternativeOnly.map(PermissionDetail::name))
        assertEquals(setOf(CAMERA, INTERNET), diff.shared.map(PermissionDetail::name).toSet())
    }

    @Test
    fun `missing alternative permissions stay unknown instead of empty`() {
        val comparison = comparePermissions(
            installed = InstalledApp(
                packageName = "org.example.old",
                label = "Old app",
                requestedPermissions = listOf(CAMERA),
            ),
            alternative = PermissionAlternative(
                label = "New app",
                packageName = "org.example.new",
                permissionsKnown = false,
                storeName = "F-Droid",
                relationship = PermissionRelationship.DIFFERENT_APP,
            ),
        )

        assertNull(comparison.diff)
        assertEquals(false, comparison.alternative.permissionsKnown)
        assertEquals(CAMERA, comparison.installed.permissions.single().name)
    }

    @Test
    fun `installed app with no requested permissions is known and empty`() {
        val comparison = comparePermissions(
            installed = InstalledApp(
                packageName = "org.example.old",
                label = "Old app",
            ),
            alternative = PermissionAlternative(
                label = "New app",
                packageName = "org.example.new",
                permissions = listOf(CAMERA),
                storeName = "F-Droid",
                relationship = PermissionRelationship.DIFFERENT_APP,
            ),
        )

        assertTrue(comparison.installed.permissionsKnown)
        assertTrue(comparison.installed.permissions.isEmpty())
        assertEquals(
            listOf(CAMERA),
            requireNotNull(comparison.diff).alternativeOnly.map(PermissionDetail::name),
        )
    }

    @Test
    fun `dangerous permissions include a plain-language explanation`() {
        val comparison = comparePermissions(
            installed = InstalledApp(
                packageName = "org.example.old",
                label = "Old app",
                requestedPermissions = listOf(CAMERA),
            ),
            alternative = PermissionAlternative(
                label = "New app",
                packageName = "org.example.new",
                permissions = listOf(CAMERA),
                storeName = "F-Droid",
                relationship = PermissionRelationship.DIFFERENT_APP,
            ),
        )

        val camera = comparison.installed.permissions.single()
        assertEquals(PermissionCategory.DANGEROUS, camera.category)
        assertTrue(camera.explanation.orEmpty().contains("photos"))
    }

    @Test
    fun `unknown permissions are labeled other with a readable fallback name`() {
        val detail = permissionDetail("com.example.permission.CUSTOM_SENSOR")

        assertEquals("Custom Sensor", detail.displayName)
        assertEquals(PermissionCategory.OTHER, detail.category)
        assertNull(detail.explanation)
    }

    @Test
    fun `candidate conversion preserves relationship and permissions`() {
        val candidate = MatchCandidate(
            appKey = "org.example.new",
            level = MatchLevel.L1,
            storeId = "store-1",
            storeName = "F-Droid",
            packageName = "org.example.new",
            displayName = "New app",
            permissions = listOf(CAMERA),
        )

        val alternative = candidate.toPermissionAlternative()

        assertEquals(PermissionRelationship.SAME_APP, alternative.relationship)
        assertEquals(listOf(CAMERA), alternative.permissions)
    }

    private companion object {
        const val CAMERA = "android.permission.CAMERA"
        const val INTERNET = "android.permission.INTERNET"
        const val READ_CONTACTS = "android.permission.READ_CONTACTS"
        const val RECORD_AUDIO = "android.permission.RECORD_AUDIO"
    }
}
