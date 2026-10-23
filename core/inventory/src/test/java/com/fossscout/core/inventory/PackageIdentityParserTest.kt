package com.fossscout.core.inventory

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PackageIdentityParserTest {
    @Test
    fun `raw package names are accepted`() {
        assertEquals("org.example.app", PackageIdentityParser.extract("org.example.app"))
        assertEquals(
            "org.example.app",
            PackageIdentityParser.extract("  org.example.app\n"),
        )
    }

    @Test
    fun `play store links are accepted`() {
        assertEquals(
            "org.example.app",
            PackageIdentityParser.extract(
                "https://play.google.com/store/apps/details?id=org.example.app&hl=en",
            ),
        )
    }

    @Test
    fun `market links are accepted`() {
        assertEquals(
            "org.example.app",
            PackageIdentityParser.extract("market://details?id=org.example.app"),
        )
    }

    @Test
    fun `f-droid links are accepted`() {
        assertEquals(
            "org.example.app",
            PackageIdentityParser.extract("https://f-droid.org/packages/org.example.app/"),
        )
        assertEquals(
            "org.example.app",
            PackageIdentityParser.extract("https://f-droid.org/en/packages/org.example.app/"),
        )
    }

    @Test
    fun `shared text containing a store link is accepted`() {
        assertEquals(
            "org.example.app",
            PackageIdentityParser.extract(
                "Check out this app: https://play.google.com/store/apps/details?id=org.example.app now",
            ),
        )
    }

    @Test
    fun `shared text containing a package token is accepted`() {
        assertEquals(
            "org.example.app",
            PackageIdentityParser.extract("Try org.example.app today"),
        )
    }

    @Test
    fun `invalid input is rejected`() {
        assertNull(PackageIdentityParser.extract(""))
        assertNull(PackageIdentityParser.extract("No app here"))
        assertNull(PackageIdentityParser.extract("https://play.google.com/store/apps"))
        assertNull(PackageIdentityParser.extract("com"))
        assertNull(PackageIdentityParser.extract("not a package name"))
    }
}
