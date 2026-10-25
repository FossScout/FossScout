package com.fossscout.core.handoff

import com.fossscout.core.inventory.InstalledApp
import com.fossscout.core.matching.MatchCandidate
import com.fossscout.core.store.BuiltInStores

/** The primary label shown for a matched FOSS candidate. */
enum class ReplacementVerdict {
    NOT_INSTALLED,
    UPDATE,
    REPLACE,
    ALTERNATIVE,
    NO_UPDATE,
    CANNOT_VERIFY,
}

enum class SignerComparison {
    SAME,
    DIFFERENT,
    UNKNOWN,
    NOT_APPLICABLE,
}

/** Warnings shown before a handoff. None of them performs an action. */
enum class ReplacementWarning {
    DATA_LOSS,
    UNINSTALL_REQUIRED,
    SYSTEM_APP,
    OTHER_STORE,
    CROSS_STORE_KEYS,
    DOWNGRADE,
    VERSION_UNKNOWN,
    SIGNER_UNKNOWN,
    WORK_PROFILE,
    DISABLED_APP,
    INSTALLER_UNKNOWN,
}

data class VersionSummary(
    val versionName: String? = null,
    val versionCode: Long? = null,
) {
    val known: Boolean get() = versionCode != null && versionCode > 0L
}

/**
 * F07 guidance derived from installed metadata and a matched store candidate.
 * This type never contains an install, uninstall, or delete command.
 */
data class ReplacementAssessment(
    val verdict: ReplacementVerdict,
    val candidatePackageName: String,
    val candidateStoreId: String,
    val candidateStoreName: String,
    val installedPackageName: String? = null,
    val installedLabel: String? = null,
    val installedInstallerPackage: String? = null,
    val installedVersion: VersionSummary? = null,
    val candidateVersion: VersionSummary? = null,
    val signerComparison: SignerComparison = SignerComparison.NOT_APPLICABLE,
    val warnings: List<ReplacementWarning> = emptyList(),
    val explanation: List<String> = emptyList(),
    val requiresExplicitConfirmation: Boolean = false,
) {
    init {
        require(candidatePackageName.isNotBlank()) { "Candidate package is blank" }
        require(candidateStoreId.isNotBlank()) { "Candidate store id is blank" }
        require(candidateStoreName.isNotBlank()) { "Candidate store name is blank" }
        require(!requiresExplicitConfirmation || verdict == ReplacementVerdict.REPLACE) {
            "Only a replace verdict needs destructive-action confirmation"
        }
    }

    val canUpdateInPlace: Boolean get() = verdict == ReplacementVerdict.UPDATE

    val canReplaceWithDataLoss: Boolean get() = verdict == ReplacementVerdict.REPLACE

    val isInstalled: Boolean get() = verdict != ReplacementVerdict.NOT_INSTALLED
}

/**
 * Pure decision engine for F07. Missing signer or version metadata fails closed
 * to [ReplacementVerdict.CANNOT_VERIFY]; it never claims a safe update.
 */
object ReplacementAssessmentEngine {
    fun assess(
        installed: InstalledApp?,
        candidate: MatchCandidate,
    ): ReplacementAssessment {
        val candidateVersion = VersionSummary(
            versionName = candidate.versionName,
            versionCode = candidate.versionCode,
        )
        if (installed == null) {
            return ReplacementAssessment(
                verdict = ReplacementVerdict.NOT_INSTALLED,
                candidatePackageName = candidate.packageName,
                candidateStoreId = candidate.storeId,
                candidateStoreName = candidate.storeName,
                candidateVersion = candidateVersion,
                explanation = listOf(
                    "This package is not installed or is not visible to FossScout on this " +
                        "device. There is no update or replacement to perform.",
                ),
            )
        }

        val installedVersion = VersionSummary(
            versionName = installed.versionName,
            versionCode = installed.versionCode.takeIf { it > 0L },
        )
        val warnings = linkedSetOf<ReplacementWarning>()
        if (installed.isSystemApp) warnings += ReplacementWarning.SYSTEM_APP
        if (!installed.isEnabled) warnings += ReplacementWarning.DISABLED_APP
        if (installed.profile.isWorkProfile) warnings += ReplacementWarning.WORK_PROFILE

        val installer = installed.installerPackage
            ?.trim()
            ?.takeIf(String::isNotEmpty)
        if (installer == null) {
            warnings += ReplacementWarning.INSTALLER_UNKNOWN
        } else if (installer != CompanionStores.fdroid.packageName) {
            warnings += ReplacementWarning.OTHER_STORE
        }

        if (installed.packageName != candidate.packageName) {
            return ReplacementAssessment(
                verdict = ReplacementVerdict.ALTERNATIVE,
                candidatePackageName = candidate.packageName,
                candidateStoreId = candidate.storeId,
                candidateStoreName = candidate.storeName,
                installedPackageName = installed.packageName,
                installedLabel = installed.label,
                installedInstallerPackage = installer,
                installedVersion = installedVersion,
                candidateVersion = candidateVersion,
                signerComparison = SignerComparison.NOT_APPLICABLE,
                warnings = warnings.toList(),
                explanation = listOf(
                    "This is a different package. It cannot update or replace the installed " +
                        "app in place, and its data will not migrate automatically.",
                ),
            )
        }

        if (
            candidate.storeId in F_DROID_COMPATIBLE_STORE_IDS &&
            installer != null &&
            CompanionStores.byPackage(installer) == null
        ) {
            warnings += ReplacementWarning.CROSS_STORE_KEYS
        }

        val signerComparison = compareSigners(
            installedSigner = installed.signerSha256,
            candidateSigners = candidate.signerSha256,
        )
        if (signerComparison == SignerComparison.UNKNOWN) {
            warnings += ReplacementWarning.SIGNER_UNKNOWN
            return assessment(
                verdict = ReplacementVerdict.CANNOT_VERIFY,
                installed = installed,
                candidate = candidate,
                installedVersion = installedVersion,
                candidateVersion = candidateVersion,
                installer = installer,
                signerComparison = signerComparison,
                warnings = warnings,
                explanation = listOf(
                    "Signer metadata is unavailable. FossScout cannot verify that this is a " +
                        "safe in-place update, so it will not recommend one.",
                ),
            )
        }

        if (signerComparison == SignerComparison.DIFFERENT) {
            warnings += ReplacementWarning.UNINSTALL_REQUIRED
            warnings += ReplacementWarning.DATA_LOSS
            return assessment(
                verdict = ReplacementVerdict.REPLACE,
                installed = installed,
                candidate = candidate,
                installedVersion = installedVersion,
                candidateVersion = candidateVersion,
                installer = installer,
                signerComparison = signerComparison,
                warnings = warnings,
                explanation = listOf(
                    "The package matches but the signing key differs. Android requires " +
                        "uninstalling the installed app before installing this build.",
                    "Uninstalling can delete the app's local data. Back up anything important " +
                        "before continuing.",
                ),
                requiresExplicitConfirmation = true,
            )
        }

        if (!installedVersion.known || !candidateVersion.known) {
            warnings += ReplacementWarning.VERSION_UNKNOWN
            return assessment(
                verdict = ReplacementVerdict.CANNOT_VERIFY,
                installed = installed,
                candidate = candidate,
                installedVersion = installedVersion,
                candidateVersion = candidateVersion,
                installer = installer,
                signerComparison = signerComparison,
                warnings = warnings,
                explanation = listOf(
                    "Version metadata is unavailable. FossScout cannot verify whether this is " +
                        "an update.",
                ),
            )
        }

        val installedCode = installedVersion.versionCode ?: 0L
        val candidateCode = candidateVersion.versionCode ?: 0L
        if (candidateCode > installedCode) {
            return assessment(
                verdict = ReplacementVerdict.UPDATE,
                installed = installed,
                candidate = candidate,
                installedVersion = installedVersion,
                candidateVersion = candidateVersion,
                installer = installer,
                signerComparison = signerComparison,
                warnings = warnings,
                explanation = listOf(
                    "The package and signer match. The store build is newer and may update " +
                        "in place after you open it in the store.",
                ),
            )
        }

        if (candidateCode < installedCode) {
            warnings += ReplacementWarning.DOWNGRADE
            return assessment(
                verdict = ReplacementVerdict.NO_UPDATE,
                installed = installed,
                candidate = candidate,
                installedVersion = installedVersion,
                candidateVersion = candidateVersion,
                installer = installer,
                signerComparison = signerComparison,
                warnings = warnings,
                explanation = listOf(
                    "The store listing is older than the installed version. Android may " +
                        "refuse a downgrade.",
                ),
            )
        }

        return assessment(
            verdict = ReplacementVerdict.NO_UPDATE,
            installed = installed,
            candidate = candidate,
            installedVersion = installedVersion,
            candidateVersion = candidateVersion,
            installer = installer,
            signerComparison = signerComparison,
            warnings = warnings,
            explanation = listOf(
                "The store listing is not newer than the installed version.",
            ),
        )
    }

    private fun assessment(
        verdict: ReplacementVerdict,
        installed: InstalledApp,
        candidate: MatchCandidate,
        installedVersion: VersionSummary,
        candidateVersion: VersionSummary,
        installer: String?,
        signerComparison: SignerComparison,
        warnings: Set<ReplacementWarning>,
        explanation: List<String>,
        requiresExplicitConfirmation: Boolean = false,
    ): ReplacementAssessment = ReplacementAssessment(
        verdict = verdict,
        candidatePackageName = candidate.packageName,
        candidateStoreId = candidate.storeId,
        candidateStoreName = candidate.storeName,
        installedPackageName = installed.packageName,
        installedLabel = installed.label,
        installedInstallerPackage = installer,
        installedVersion = installedVersion,
        candidateVersion = candidateVersion,
        signerComparison = signerComparison,
        warnings = warnings.toList(),
        explanation = explanation,
        requiresExplicitConfirmation = requiresExplicitConfirmation,
    )

    private fun compareSigners(
        installedSigner: String?,
        candidateSigners: List<String>,
    ): SignerComparison {
        val installed = normalizeSigner(installedSigner) ?: return SignerComparison.UNKNOWN
        val candidates = candidateSigners.mapNotNull(::normalizeSigner).toSet()
        if (candidates.isEmpty()) return SignerComparison.UNKNOWN
        return if (installed in candidates) {
            SignerComparison.SAME
        } else {
            SignerComparison.DIFFERENT
        }
    }

    private fun normalizeSigner(value: String?): String? =
        value
            ?.trim()
            ?.lowercase()
            ?.replace(":", "")
            ?.replace(" ", "")
            ?.takeIf(String::isNotEmpty)

    private val F_DROID_COMPATIBLE_STORE_IDS = setOf(
        BuiltInStores.FDROID_ID,
        BuiltInStores.IZZY_ON_DROID_ID,
    )
}
