package com.fossscout.core.handoff

import com.fossscout.core.inventory.AppProfile
import com.fossscout.core.inventory.InstalledApp
import com.fossscout.core.matching.MatchCandidate
import com.fossscout.core.matching.MatchLevel
import com.fossscout.core.store.BuiltInStores
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReplacementAssessmentEngineTest {
    @Test
    fun `same package and signer with a newer version is an update`() {
        val result = ReplacementAssessmentEngine.assess(
            installed = installed(versionCode = 10L, versionName = "1.0"),
            candidate = candidate(versionCode = 11L, versionName = "1.1"),
        )

        assertEquals(ReplacementVerdict.UPDATE, result.verdict)
        assertTrue(result.canUpdateInPlace)
        assertFalse(result.canReplaceWithDataLoss)
        assertFalse(result.requiresExplicitConfirmation)
        assertEquals(10L, result.installedVersion?.versionCode)
        assertEquals(11L, result.candidateVersion?.versionCode)
    }

    @Test
    fun `same package with a different signer requires a replacement warning`() {
        val result = ReplacementAssessmentEngine.assess(
            installed = installed(signer = "aaaa"),
            candidate = candidate(signerSha256 = listOf("bbbb")),
        )

        assertEquals(ReplacementVerdict.REPLACE, result.verdict)
        assertEquals(SignerComparison.DIFFERENT, result.signerComparison)
        assertTrue(result.canReplaceWithDataLoss)
        assertTrue(result.requiresExplicitConfirmation)
        assertTrue(ReplacementWarning.UNINSTALL_REQUIRED in result.warnings)
        assertTrue(ReplacementWarning.DATA_LOSS in result.warnings)
    }

    @Test
    fun `a different package is an alternative and never an in-place update`() {
        val result = ReplacementAssessmentEngine.assess(
            installed = installed(),
            candidate = candidate(packageName = "org.example.other"),
        )

        assertEquals(ReplacementVerdict.ALTERNATIVE, result.verdict)
        assertEquals(SignerComparison.NOT_APPLICABLE, result.signerComparison)
        assertFalse(result.canUpdateInPlace)
        assertFalse(result.canReplaceWithDataLoss)
        assertTrue(result.explanation.any { it.contains("different package") })
    }

    @Test
    fun `missing installed signer fails closed`() {
        val result = ReplacementAssessmentEngine.assess(
            installed = installed(signer = null),
            candidate = candidate(),
        )

        assertEquals(ReplacementVerdict.CANNOT_VERIFY, result.verdict)
        assertEquals(SignerComparison.UNKNOWN, result.signerComparison)
        assertTrue(ReplacementWarning.SIGNER_UNKNOWN in result.warnings)
        assertFalse(result.canUpdateInPlace)
    }

    @Test
    fun `missing candidate signer fails closed`() {
        val result = ReplacementAssessmentEngine.assess(
            installed = installed(),
            candidate = candidate(signerSha256 = emptyList()),
        )

        assertEquals(ReplacementVerdict.CANNOT_VERIFY, result.verdict)
        assertEquals(SignerComparison.UNKNOWN, result.signerComparison)
        assertTrue(ReplacementWarning.SIGNER_UNKNOWN in result.warnings)
    }

    @Test
    fun `missing version metadata fails closed`() {
        val result = ReplacementAssessmentEngine.assess(
            installed = installed(),
            candidate = candidate(versionCode = null),
        )

        assertEquals(ReplacementVerdict.CANNOT_VERIFY, result.verdict)
        assertTrue(ReplacementWarning.VERSION_UNKNOWN in result.warnings)
        assertFalse(result.canUpdateInPlace)
    }

    @Test
    fun `equal versions are not presented as an update`() {
        val result = ReplacementAssessmentEngine.assess(
            installed = installed(versionCode = 10L),
            candidate = candidate(versionCode = 10L),
        )

        assertEquals(ReplacementVerdict.NO_UPDATE, result.verdict)
        assertFalse(result.canUpdateInPlace)
        assertFalse(ReplacementWarning.DOWNGRADE in result.warnings)
    }

    @Test
    fun `older candidate is a downgrade warning`() {
        val result = ReplacementAssessmentEngine.assess(
            installed = installed(versionCode = 10L),
            candidate = candidate(versionCode = 9L),
        )

        assertEquals(ReplacementVerdict.NO_UPDATE, result.verdict)
        assertTrue(ReplacementWarning.DOWNGRADE in result.warnings)
    }

    @Test
    fun `system and other-store installs keep explicit warnings`() {
        val result = ReplacementAssessmentEngine.assess(
            installed = installed(
                installer = "com.android.vending",
                isSystemApp = true,
                isEnabled = false,
                isWorkProfile = true,
            ),
            candidate = candidate(),
        )

        assertTrue(ReplacementWarning.SYSTEM_APP in result.warnings)
        assertTrue(ReplacementWarning.OTHER_STORE in result.warnings)
        assertTrue(ReplacementWarning.CROSS_STORE_KEYS in result.warnings)
        assertTrue(ReplacementWarning.DISABLED_APP in result.warnings)
        assertTrue(ReplacementWarning.WORK_PROFILE in result.warnings)
    }

    @Test
    fun `f-droid installs do not warn about store ownership or key crossing`() {
        val result = ReplacementAssessmentEngine.assess(
            installed = installed(installer = CompanionStores.fdroid.packageName),
            candidate = candidate(),
        )

        assertFalse(ReplacementWarning.OTHER_STORE in result.warnings)
        assertFalse(ReplacementWarning.CROSS_STORE_KEYS in result.warnings)
    }

    @Test
    fun `another f-droid client warns about ownership but not play key crossing`() {
        val result = ReplacementAssessmentEngine.assess(
            installed = installed(installer = CompanionStores.droidify.packageName),
            candidate = candidate(),
        )

        assertTrue(ReplacementWarning.OTHER_STORE in result.warnings)
        assertFalse(ReplacementWarning.CROSS_STORE_KEYS in result.warnings)
    }

    @Test
    fun `unknown installer is visible but not treated as a store`() {
        val result = ReplacementAssessmentEngine.assess(
            installed = installed(installer = null),
            candidate = candidate(),
        )

        assertTrue(ReplacementWarning.INSTALLER_UNKNOWN in result.warnings)
        assertFalse(ReplacementWarning.OTHER_STORE in result.warnings)
    }

    @Test
    fun `not installed candidates still have handoff guidance`() {
        val result = ReplacementAssessmentEngine.assess(
            installed = null,
            candidate = candidate(),
        )

        assertEquals(ReplacementVerdict.NOT_INSTALLED, result.verdict)
        assertFalse(result.isInstalled)
        assertEquals("org.example.app", result.candidatePackageName)
        assertEquals(20L, result.candidateVersion?.versionCode)
    }

    private fun installed(
        versionCode: Long = 10L,
        versionName: String = "1.0",
        signer: String? = SIGNER,
        installer: String? = "com.android.vending",
        isSystemApp: Boolean = false,
        isEnabled: Boolean = true,
        isWorkProfile: Boolean = false,
    ): InstalledApp = InstalledApp(
        packageName = PACKAGE,
        label = "Example",
        versionName = versionName,
        versionCode = versionCode,
        installerPackage = installer,
        signerSha256 = signer,
        isSystemApp = isSystemApp,
        isEnabled = isEnabled,
        profile = AppProfile(isWorkProfile = isWorkProfile),
    )

    private fun candidate(
        packageName: String = PACKAGE,
        versionCode: Long? = 20L,
        versionName: String? = "2.0",
        signerSha256: List<String> = listOf(SIGNER),
    ): MatchCandidate = MatchCandidate(
        appKey = packageName,
        level = MatchLevel.L1,
        storeId = BuiltInStores.FDROID_ID,
        storeName = "F-Droid",
        packageName = packageName,
        displayName = "Example",
        versionName = versionName,
        versionCode = versionCode,
        signerSha256 = signerSha256,
    )

    private companion object {
        const val PACKAGE = "org.example.app"
        const val SIGNER = "aaaa"
    }
}
