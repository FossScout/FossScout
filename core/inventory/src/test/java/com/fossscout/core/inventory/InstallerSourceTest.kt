package com.fossscout.core.inventory

import org.junit.Assert.assertEquals
import org.junit.Test

class InstallerSourceTest {
    @Test
    fun `known stores use friendly names`() {
        assertEquals(
            InstallerSource.Known("Google Play"),
            installerSource("com.android.vending", isSystemApp = false),
        )
        assertEquals(
            InstallerSource.Known("F-Droid"),
            installerSource("org.fdroid.fdroid", isSystemApp = false),
        )
        assertEquals(
            InstallerSource.Known("Droid-ify"),
            installerSource("com.looker.droidify", isSystemApp = false),
        )
        assertEquals(
            InstallerSource.Known("Neo Store"),
            installerSource("com.machiav3lli.fdroid", isSystemApp = false),
        )
    }

    @Test
    fun `additional stores use friendly names`() {
        assertEquals(
            InstallerSource.Known("Aurora Store"),
            installerSource("com.aurora.store", isSystemApp = false),
        )
        assertEquals(
            InstallerSource.Known("Galaxy Store"),
            installerSource("com.sec.android.app.samsungapps", isSystemApp = false),
        )
        assertEquals(
            InstallerSource.Known("Amazon Appstore"),
            installerSource("com.amazon.venezia", isSystemApp = false),
        )
        assertEquals(
            InstallerSource.Known("AppGallery"),
            installerSource("com.huawei.appmarket", isSystemApp = false),
        )
    }

    @Test
    fun `package installer and adb use friendly names`() {
        assertEquals(
            InstallerSource.Known("the package installer"),
            installerSource("com.google.android.packageinstaller", isSystemApp = false),
        )
        assertEquals(
            InstallerSource.Known("the package installer"),
            installerSource("com.android.packageinstaller", isSystemApp = false),
        )
        assertEquals(
            InstallerSource.Known("ADB"),
            installerSource("com.android.shell", isSystemApp = false),
        )
    }

    @Test
    fun `unknown installer keeps the raw package`() {
        assertEquals(
            InstallerSource.Other("com.example.store"),
            installerSource("  com.example.store  ", isSystemApp = false),
        )
    }

    @Test
    fun `missing installer for a system app is recognized as a system app`() {
        assertEquals(
            InstallerSource.SystemApp,
            installerSource(installerPackage = null, isSystemApp = true),
        )
        assertEquals(
            InstallerSource.SystemApp,
            installerSource(installerPackage = "   ", isSystemApp = true),
        )
    }

    @Test
    fun `missing installer for a non-system app stays unknown`() {
        assertEquals(
            InstallerSource.Unknown,
            installerSource(installerPackage = null, isSystemApp = false),
        )
    }

    @Test
    fun `known installer wins over the system app flag`() {
        assertEquals(
            InstallerSource.Known("Google Play"),
            installerSource("com.android.vending", isSystemApp = true),
        )
    }
}
