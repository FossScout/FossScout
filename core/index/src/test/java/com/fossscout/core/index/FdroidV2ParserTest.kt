package com.fossscout.core.index

import java.io.ByteArrayInputStream
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FdroidV2ParserTest {
    private val parser = FdroidV2Parser()

    @Test
    fun `full index exposes listing metadata needed by later features`() = runTest {
        val packages = mutableListOf<StorePackage>()

        val parsed = parser.parseIndex(
            ByteArrayInputStream(TestIndexFixtures.indexJson().toByteArray()),
        ) { packages += it }

        assertEquals(2, parsed.packageCount)
        assertEquals("https://repo.example/repo", parsed.repoAddress)
        assertEquals("Test repo", parsed.repoName)
        assertEquals(
            listOf(TestIndexFixtures.PACKAGE_A, TestIndexFixtures.PACKAGE_B),
            packages.map(StorePackage::packageName),
        )
        val alpha = packages.first()
        assertEquals("Alpha", alpha.localizedName())
        assertEquals("First test app", alpha.localizedSummary())
        assertEquals("A longer alpha description", alpha.description["en-US"])
        assertEquals(listOf("Tools"), alpha.categories)
        assertEquals("GPL-3.0-or-later", alpha.license)
        assertEquals("https://example.org/alpha", alpha.sourceCode)
        assertEquals("https://example.org", alpha.website)
        assertEquals(1_000L, alpha.addedAtEpochMillis)
        assertEquals(2_000L, alpha.lastUpdatedEpochMillis)
        assertEquals(
            mapOf("en-US" to "Uses a network"),
            alpha.antiFeatures["NonFreeNet"],
        )
        val version = requireNotNull(alpha.latestVersion())
        assertEquals(TestIndexFixtures.VERSION_HASH_A, version.fileSha256)
        assertEquals("/alpha-1.apk", version.fileName)
        assertEquals(1_234L, version.sizeBytes)
        assertEquals(1L, version.versionCode)
        assertEquals("1.0", version.versionName)
        assertEquals(24, version.minSdk)
        assertEquals(35, version.targetSdk)
        assertEquals(
            listOf("dddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddd"),
            version.signerSha256,
        )
        assertEquals(listOf("android.permission.INTERNET"), version.permissions)
    }

    @Test
    fun `diff distinguishes removal from an unchanged package`() {
        val diffs = parser.parseDiff(
            ByteArrayInputStream(TestIndexFixtures.diffJson().toByteArray()),
        )

        assertTrue(diffs.containsKey(TestIndexFixtures.PACKAGE_A))
        assertTrue(diffs.containsKey(TestIndexFixtures.PACKAGE_B))
        assertNull(diffs[TestIndexFixtures.PACKAGE_B])
        assertEquals(2, diffs.size)
    }

    @Test
    fun `diff merges metadata versions and removals into a package`() = runTest {
        val packages = mutableListOf<StorePackage>()
        parser.parseIndex(
            ByteArrayInputStream(TestIndexFixtures.indexJson().toByteArray()),
        ) { packages += it }
        val previous = packages.first()
        val patch = requireNotNull(
            parser.parseDiff(
                ByteArrayInputStream(TestIndexFixtures.diffJson().toByteArray()),
            )[TestIndexFixtures.PACKAGE_A],
        )

        val merged = parser.applyDiff(previous, patch)

        assertEquals("Updated alpha", merged.summary["en-US"])
        assertEquals(5_000L, merged.lastUpdatedEpochMillis)
        assertEquals(listOf("Tools"), merged.categories)
        assertEquals(
            listOf(TestIndexFixtures.VERSION_HASH_A2),
            merged.versions.map(StorePackageVersion::fileSha256),
        )
        assertEquals(3L, merged.versions.single().versionCode)
        assertEquals("3.0", merged.versions.single().versionName)
    }

    @Test
    fun `encoded package round trips through the payload codec`() {
        val packageRecord = StorePackage(
            packageName = "org.example.roundtrip",
            name = mapOf("en-US" to "Round trip"),
            versions = listOf(
                StorePackageVersion(
                    fileSha256 = "a".repeat(64),
                    versionCode = 7,
                    versionName = "7.0",
                ),
            ),
        )

        assertEquals(packageRecord, parser.decodePackage(parser.encodePackage(packageRecord)))
    }

    @Test
    fun `malformed index is reported as a parse failure`() = runTest {
        val failure = runCatching {
            parser.parseIndex(ByteArrayInputStream("{\"packages\":{".toByteArray())) { }
        }.exceptionOrNull()

        assertTrue(failure is IndexSyncException)
        assertEquals(SyncFailureKind.PARSE, (failure as IndexSyncException).kind)
    }

    @Test
    fun `malformed diff is reported as a parse failure`() {
        val failure = runCatching {
            parser.parseDiff(ByteArrayInputStream("{\"packages\":".toByteArray()))
        }.exceptionOrNull()

        assertTrue(failure is IndexSyncException)
        assertEquals(SyncFailureKind.PARSE, (failure as IndexSyncException).kind)
    }

    @Test
    fun `malformed entry inside a signed jar is a parse failure`() {
        val unsigned = FileHolder.entryJar("""{"timestamp":1}""")

        val failure = runCatching { parser.readEntry(unsigned) }.exceptionOrNull()

        assertTrue(failure is IndexSyncException)
        assertEquals(SyncFailureKind.PARSE, (failure as IndexSyncException).kind)
        assertTrue(unsigned.isFile)
    }

    /** Minimal unsigned JAR so entry parsing can be tested without signing. */
    private object FileHolder {
        fun entryJar(json: String): java.io.File {
            val dir = java.nio.file.Files.createTempDirectory("fossscout-entry").toFile()
            val file = java.io.File(dir, "entry.jar")
            java.util.jar.JarOutputStream(java.io.FileOutputStream(file)).use { jar ->
                jar.putNextEntry(java.util.jar.JarEntry("entry.json"))
                jar.write(json.toByteArray())
                jar.closeEntry()
            }
            return file
        }
    }
}
