package com.fossscout.core.matching

import java.io.File
import java.util.zip.ZipFile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/**
 * Scans locally available APK files for DEX code signatures. Files are supplied
 * by the caller; this class never downloads an APK and never uploads results.
 */
class ApkTrackerScanner(
    dataset: TrackerDataset,
    private val maxScanBytes: Long = DEFAULT_MAX_SCAN_BYTES,
) {
    private val matcher = TrackerSignatureMatcher(dataset)

    init {
        require(maxScanBytes > 0L) { "maxScanBytes must be positive" }
    }

    suspend fun scan(apkFiles: List<File>): TrackerScanResult {
        val files = apkFiles
            .distinctBy { it.absolutePath }
            .filter { it.isFile }
        if (files.isEmpty()) {
            return TrackerScanResult()
        }

        val context = currentCoroutineContext()
        val found = mutableMapOf<Int, MutableSet<String>>()
        var scannedFiles = 0
        var scannedBytes = 0L
        var truncated = false
        var readFailure = false

        for (file in files) {
            context.ensureActive()
            if (scannedBytes >= maxScanBytes) {
                truncated = true
                break
            }
            try {
                ZipFile(file).use { zip ->
                    val dexEntries = zip.entries()
                        .asSequence()
                        .filter { entry -> DEX_ENTRY.matches(entry.name) }
                        .toList()
                    for (entry in dexEntries) {
                        context.ensureActive()
                        if (scannedBytes >= maxScanBytes) {
                            truncated = true
                            break
                        }
                        val remaining = maxScanBytes - scannedBytes
                        val scan = zip.getInputStream(entry).use { input ->
                            matcher.scan(input, remaining)
                        }
                        scan.found.forEach { (trackerIndex, signatures) ->
                            found.getOrPut(trackerIndex) { mutableSetOf() }
                                .addAll(signatures)
                        }
                        scannedFiles += 1
                        scannedBytes += scan.consumedBytes
                        if (scan.truncated) {
                            truncated = true
                            break
                        }
                    }
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                readFailure = true
            }
        }

        if (readFailure) {
            truncated = true
        }
        if (scannedFiles == 0) {
            return TrackerScanResult(
                status = TrackerScanStatus.UNAVAILABLE,
                scannedBytes = scannedBytes,
            )
        }

        return TrackerScanResult(
            status = if (truncated) {
                TrackerScanStatus.TRUNCATED
            } else {
                TrackerScanStatus.COMPLETE
            },
            matches = matcher.toMatches(
                found.mapValues { (_, signatures) -> signatures.toSet() },
            ),
            scannedFiles = scannedFiles,
            scannedBytes = scannedBytes,
        )
    }

    companion object {
        /** Caps work for very large apps; a truncated result is shown as incomplete. */
        const val DEFAULT_MAX_SCAN_BYTES: Long = 64L * 1024L * 1024L

        private val DEX_ENTRY = Regex("classes(\\d*)\\.dex")
    }
}
