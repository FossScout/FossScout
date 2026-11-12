package com.fossscout.app.inventory

import com.fossscout.core.matching.ApkTrackerScanner
import com.fossscout.core.matching.TrackerDataset
import com.fossscout.core.matching.TrackerDatasetInfo
import com.fossscout.core.matching.TrackerScanResult
import com.fossscout.core.matching.TrackerScanSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Local-only tracker scan over the selected installed app's APK files. */
class PackageManagerTrackerScanSource(
    private val apkSource: InstalledApkSource,
    dataset: TrackerDataset?,
) : TrackerScanSource {
    private val scanner by lazy { dataset?.let { ApkTrackerScanner(it) } }

    override val datasetInfo: TrackerDatasetInfo? = dataset?.dataset

    override suspend fun scan(packageName: String): TrackerScanResult =
        withContext(Dispatchers.IO) {
            val scanner = scanner ?: return@withContext TrackerScanResult()
            val files = apkSource.apkFiles(packageName)
            if (files.isEmpty()) {
                return@withContext TrackerScanResult()
            }
            scanner.scan(files)
        }
}
