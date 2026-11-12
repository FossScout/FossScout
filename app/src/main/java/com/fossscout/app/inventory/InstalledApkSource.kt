package com.fossscout.app.inventory

import java.io.File

/** Resolves the local APK files for one installed package. */
interface InstalledApkSource {
    suspend fun apkFiles(packageName: String): List<File>
}
