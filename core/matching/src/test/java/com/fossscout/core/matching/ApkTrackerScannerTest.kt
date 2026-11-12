package com.fossscout.core.matching

import java.io.File
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ApkTrackerScannerTest {
    @Test
    fun `scans base and split DEX files`() = runTest {
        val directory = Files.createTempDirectory("tracker-scanner").toFile()
        try {
            val base = apk(
                directory,
                "base.apk",
                "classes.dex" to "Lcom/example/alpha/Class;".toByteArray(),
                "resources.arsc" to byteArrayOf(1, 2, 3),
            )
            val split = apk(
                directory,
                "split.apk",
                "classes2.dex" to "Lcom/example/zeta/Class;".toByteArray(),
            )
            val scanner = ApkTrackerScanner(dataset())

            val result = scanner.scan(listOf(base, split))

            assertEquals(TrackerScanStatus.COMPLETE, result.status)
            assertEquals(2, result.scannedFiles)
            assertEquals(listOf("Alpha", "Zeta"), result.matches.map(TrackerMatch::name))
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun `byte limit produces a truncated result`() = runTest {
        val directory = Files.createTempDirectory("tracker-scanner").toFile()
        try {
            val file = apk(
                directory,
                "base.apk",
                "classes.dex" to "x".repeat(100).toByteArray(),
            )
            val scanner = ApkTrackerScanner(dataset(), maxScanBytes = 10)

            val result = scanner.scan(listOf(file))

            assertEquals(TrackerScanStatus.TRUNCATED, result.status)
            assertEquals(1, result.scannedFiles)
            assertEquals(10L, result.scannedBytes)
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun `missing APK is unavailable rather than clean`() = runTest {
        val directory = Files.createTempDirectory("tracker-scanner").toFile()
        try {
            val scanner = ApkTrackerScanner(dataset())

            val result = scanner.scan(listOf(File(directory, "missing.apk")))

            assertEquals(TrackerScanStatus.UNAVAILABLE, result.status)
            assertTrue(result.matches.isEmpty())
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun `APK without DEX files is unavailable`() = runTest {
        val directory = Files.createTempDirectory("tracker-scanner").toFile()
        try {
            val file = apk(
                directory,
                "base.apk",
                "resources.arsc" to byteArrayOf(1, 2, 3),
            )
            val scanner = ApkTrackerScanner(dataset())

            val result = scanner.scan(listOf(file))

            assertEquals(TrackerScanStatus.UNAVAILABLE, result.status)
        } finally {
            directory.deleteRecursively()
        }
    }

    private fun apk(
        directory: File,
        name: String,
        vararg entries: Pair<String, ByteArray>,
    ): File {
        val file = File(directory, name)
        ZipOutputStream(file.outputStream()).use { zip ->
            entries.forEach { (entryName, bytes) ->
                zip.putNextEntry(ZipEntry(entryName))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
        return file
    }

    private fun dataset(): TrackerDataset = TrackerDataset(
        dataset = TrackerDatasetInfo(
            name = "Test dataset",
            version = "1",
            licenseName = "MIT",
            licenseUrl = "https://example.org/license",
            sourceUrl = "https://example.org/source",
        ),
        trackers = listOf(
            TrackerDefinition(
                name = "Alpha",
                signatures = listOf(
                    TrackerSignature("com.example.alpha.", TrackerConfidence.KNOWN),
                ),
            ),
            TrackerDefinition(
                name = "Zeta",
                signatures = listOf(
                    TrackerSignature("com.example.zeta.", TrackerConfidence.CANDIDATE),
                ),
            ),
        ),
    )
}
