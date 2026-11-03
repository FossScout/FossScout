package com.fossscout.core.inventory

/**
 * Displayable source of an installed package. [Known] and [Other] keep the
 * platform's installer package; [SystemApp] is used only when the installer is
 * missing but the platform marks the package as a system app.
 */
sealed interface InstallerSource {
    data class Known(val name: String) : InstallerSource
    data class Other(val packageName: String) : InstallerSource
    data object SystemApp : InstallerSource
    data object Unknown : InstallerSource
}

private val KNOWN_INSTALLERS = mapOf(
    "com.android.vending" to "Google Play",
    "org.fdroid.fdroid" to "F-Droid",
    "com.looker.droidify" to "Droid-ify",
    "com.machiav3lli.fdroid" to "Neo Store",
    "com.aurora.store" to "Aurora Store",
    "com.sec.android.app.samsungapps" to "Galaxy Store",
    "com.amazon.venezia" to "Amazon Appstore",
    "com.huawei.appmarket" to "AppGallery",
    "com.google.android.packageinstaller" to "the package installer",
    "com.android.packageinstaller" to "the package installer",
    "com.android.shell" to "ADB",
)

/**
 * Resolves an installer package to a displayable source. A missing installer
 * falls back to [InstallerSource.SystemApp] only when the platform marks the
 * package as a system app.
 */
fun installerSource(installerPackage: String?, isSystemApp: Boolean): InstallerSource {
    val packageName = installerPackage?.trim()?.takeIf(String::isNotEmpty)
    if (packageName != null) {
        return KNOWN_INSTALLERS[packageName]
            ?.let(InstallerSource::Known)
            ?: InstallerSource.Other(packageName)
    }
    return if (isSystemApp) InstallerSource.SystemApp else InstallerSource.Unknown
}
