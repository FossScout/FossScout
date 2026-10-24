package com.fossscout.core.matching

import com.fossscout.core.inventory.InstalledApp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MatchQueryTest {
    @Test
    fun `package-shaped input becomes an exact package query`() {
        val query = MatchQuery.forInput("org.example.app")

        assertEquals("org.example.app", query.effectivePackageName)
    }

    @Test
    fun `free text keeps no package identity`() {
        val query = MatchQuery.forInput("Example App")

        assertNull(query.effectivePackageName)
    }

    @Test
    fun `package detection is strict`() {
        assertTrue(looksLikePackageName("org.example.app"))
        assertFalse(looksLikePackageName("Example App"))
        assertFalse(looksLikePackageName("org"))
        assertFalse(looksLikePackageName("org..app"))
    }

    @Test
    fun `installed app query carries package and signer`() {
        val query = MatchQuery.from(
            InstalledApp(
                packageName = "org.example.app",
                label = "Example",
                signerSha256 = "AABB",
            ),
        )

        assertEquals("org.example.app", query.effectivePackageName)
        assertEquals("Example", query.label)
        assertEquals("aabb", query.installedSignerSha256)
    }
}
