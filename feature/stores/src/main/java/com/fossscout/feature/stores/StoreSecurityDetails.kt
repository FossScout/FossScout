package com.fossscout.feature.stores

import com.fossscout.core.index.SignatureStatus
import com.fossscout.core.index.SyncFailureKind
import com.fossscout.core.index.TrustRecord
import com.fossscout.core.index.VerificationFailure
import com.fossscout.core.index.pinnedFingerprintOrNull
import com.fossscout.core.model.StoreId
import com.fossscout.core.store.StoreDescriptor

/** The distinct trust states the UI is allowed to describe. */
enum class StoreTrustState {
    VERIFIED,
    UNVERIFIED,
    FAILED,
    ROTATION_REQUIRED,
    NOT_CHECKED,
}

enum class FingerprintRotationReason {
    PIN_CHANGED,
    SIGNER_MISMATCH,
    PIN_REMOVED,
}

/**
 * A pending fingerprint change. [newFingerprint] is null when the user removed
 * the pin; that cannot be approved as a rotation and must be re-pinned or the
 * store removed.
 */
data class FingerprintRotation(
    val currentFingerprint: String?,
    val newFingerprint: String?,
    val reason: FingerprintRotationReason,
)

/**
 * Presentation-only security evidence for one store. It deliberately keeps
 * index verification separate from APK verification: v1 does not check APK
 * hashes or signer continuity, and the UI must not imply that it does.
 */
data class StoreSecurityDetails(
    val storeId: StoreId,
    val storeName: String,
    val trustState: StoreTrustState,
    val pinnedFingerprint: String?,
    val expectedFingerprint: String?,
    val signerFingerprint: String?,
    val signatureStatus: SignatureStatus?,
    val verificationFailure: VerificationFailure?,
    val syncFailure: SyncFailureKind?,
    val verificationCheckedAtEpochMillis: Long?,
    val lastSuccessfulSyncEpochMillis: Long?,
    val verificationSourceUrl: String?,
    val indexSourceUrl: String?,
    val indexVersion: Int?,
    val indexTimestamp: Long?,
    val indexSha256: String?,
    val httpInsecure: Boolean,
    val httpOptIn: Boolean,
    val audit: List<TrustRecord>,
    val rotation: FingerprintRotation?,
) {
    companion object {
        fun from(
            descriptor: StoreDescriptor,
            status: StoreSyncStatus?,
        ): StoreSecurityDetails {
            val snapshot = status?.snapshot
            val trust = status?.trust
            val currentFingerprint =
                (snapshot?.expectedFingerprint ?: trust?.expectedFingerprint).normalized()
            val pinnedFingerprint = descriptor.pinnedFingerprintOrNull()
            val signerFingerprint =
                (trust?.signerFingerprint ?: snapshot?.signerFingerprint).normalized()
            val signatureStatus = trust?.signatureStatus ?: snapshot?.signatureStatus
            val failure = trust?.failure
            val syncFailure = status?.job?.error?.kind
            val rotation = rotationFor(
                currentFingerprint = currentFingerprint,
                pinnedFingerprint = pinnedFingerprint,
                signerFingerprint = signerFingerprint,
                failure = failure,
            )
            val trustState = when {
                rotation != null -> StoreTrustState.ROTATION_REQUIRED
                signatureStatus == SignatureStatus.INVALID ||
                    signatureStatus == SignatureStatus.UNSIGNED -> StoreTrustState.FAILED
                failure != null &&
                    failure != VerificationFailure.NO_PINNED_FINGERPRINT -> StoreTrustState.FAILED
                syncFailure == SyncFailureKind.VERIFICATION -> StoreTrustState.FAILED
                signatureStatus == SignatureStatus.UNVERIFIED ||
                    failure == VerificationFailure.NO_PINNED_FINGERPRINT ->
                    StoreTrustState.UNVERIFIED
                signatureStatus == SignatureStatus.VERIFIED -> StoreTrustState.VERIFIED
                else -> StoreTrustState.NOT_CHECKED
            }
            return StoreSecurityDetails(
                storeId = descriptor.id,
                storeName = descriptor.name,
                trustState = trustState,
                pinnedFingerprint = pinnedFingerprint,
                expectedFingerprint = currentFingerprint,
                signerFingerprint = signerFingerprint,
                signatureStatus = signatureStatus,
                verificationFailure = failure,
                syncFailure = syncFailure,
                verificationCheckedAtEpochMillis = trust?.checkedAtEpochMillis,
                lastSuccessfulSyncEpochMillis = snapshot?.fetchedAtEpochMillis,
                verificationSourceUrl = trust?.sourceUrl,
                indexSourceUrl = snapshot?.sourceUrl,
                indexVersion = snapshot?.indexVersion ?: trust?.indexVersion,
                indexTimestamp = snapshot?.indexTimestamp ?: trust?.indexTimestamp,
                indexSha256 = snapshot?.indexSha256,
                httpInsecure = StoreEditor.from(descriptor).usesInsecureHttp(),
                httpOptIn = descriptor.allowInsecureHttp,
                audit = status?.audit.orEmpty().sortedBy(TrustRecord::checkedAtEpochMillis),
                rotation = rotation,
            )
        }

        private fun rotationFor(
            currentFingerprint: String?,
            pinnedFingerprint: String?,
            signerFingerprint: String?,
            failure: VerificationFailure?,
        ): FingerprintRotation? = when {
            currentFingerprint == null -> null
            pinnedFingerprint == null -> FingerprintRotation(
                currentFingerprint = currentFingerprint,
                newFingerprint = null,
                reason = FingerprintRotationReason.PIN_REMOVED,
            )

            pinnedFingerprint != currentFingerprint -> FingerprintRotation(
                currentFingerprint = currentFingerprint,
                newFingerprint = pinnedFingerprint,
                reason = FingerprintRotationReason.PIN_CHANGED,
            )

            failure == VerificationFailure.SIGNER_MISMATCH &&
                signerFingerprint != null &&
                signerFingerprint != currentFingerprint -> FingerprintRotation(
                currentFingerprint = currentFingerprint,
                newFingerprint = signerFingerprint,
                reason = FingerprintRotationReason.SIGNER_MISMATCH,
            )

            else -> null
        }
    }
}

private fun String?.normalized(): String? = this
    ?.trim()
    ?.lowercase()
    ?.replace(":", "")
    ?.replace(" ", "")
    ?.takeIf(String::isNotEmpty)
