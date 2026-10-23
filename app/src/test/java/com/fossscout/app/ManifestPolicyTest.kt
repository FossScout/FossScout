package com.fossscout.app

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the Play/F-Droid package-visibility boundary at the manifest level.
 * The merged-manifest check runs opportunistically when assemble has produced
 * the Play manifest; the source-manifest checks always run.
 */
class ManifestPolicyTest {
    private val restrictedTokens = listOf(
        "QUERY_ALL_PACKAGES",
        "REQUEST_INSTALL_PACKAGES",
        "BIND_ACCESSIBILITY_SERVICE",
        "android.accessibilityservice",
    )

    @Test
    fun `main and play manifests contain no restricted package or accessibility declarations`() {
        val manifests = listOfNotNull(
            manifest("src/main/AndroidManifest.xml"),
            manifest("src/playstore/AndroidManifest.xml"),
        )

        assertTrue(manifests.isNotEmpty())
        manifests.forEach { (name, contents) ->
            restrictedTokens.forEach { token ->
                assertFalse("$name must not contain $token", contents.contains(token))
            }
        }
    }

    @Test
    fun `fdroid manifest declares broad package visibility`() {
        val (_, contents) = manifest("src/fdroid/AndroidManifest.xml")
            ?: error("F-Droid manifest is missing")

        assertTrue(contents.contains("android.permission.QUERY_ALL_PACKAGES"))
    }

    @Test
    fun `merged play manifest is policy safe when present`() {
        val merged = mergedPlayManifest() ?: return
        restrictedTokens.forEach { token ->
            assertFalse("Merged Play manifest must not contain $token", merged.contains(token))
        }
    }

    private fun manifest(path: String): Pair<String, String>? {
        val file = File(path)
        return if (file.isFile) path to file.readText() else null
    }

    private fun mergedPlayManifest(): String? {
        val candidates = listOf(
            "build/intermediates/merged_manifests/playstoreDebug/processPlaystoreDebugManifest/AndroidManifest.xml",
            "build/intermediates/merged_manifests/playstoreDebug/processPlaystoreDebugMainManifest/AndroidManifest.xml",
            "build/intermediates/merged_manifest/playstoreDebug/processPlaystoreDebugMainManifest/AndroidManifest.xml",
        )
        return candidates
            .map(::File)
            .firstOrNull(File::isFile)
            ?.readText()
    }
}
