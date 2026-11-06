package com.fossscout.core.matching

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KnownProprietaryAppsTest {
    @Test
    fun `catalog entries have unique package names and HTTPS evidence`() {
        val entries = KnownProprietaryApps.entries

        assertTrue(entries.isNotEmpty())
        assertEquals(entries.size, entries.map { it.packageName }.toSet().size)
        entries.forEach { entry ->
            assertTrue(entry.packageName.isNotBlank())
            assertTrue(entry.displayName.isNotBlank())
            assertTrue(entry.evidenceUrl.startsWith("https://"))
        }
    }

    @Test
    fun `Slack and Facebook are covered`() {
        assertTrue(KnownProprietaryApps.evidenceFor("com.Slack") != null)
        assertTrue(KnownProprietaryApps.evidenceFor("com.facebook.katana") != null)
    }

    @Test
    fun `unlisted package has no proprietary evidence`() {
        assertEquals(null, KnownProprietaryApps.evidenceFor("org.example.app"))
    }
}
