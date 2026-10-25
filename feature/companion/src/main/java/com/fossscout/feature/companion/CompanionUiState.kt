package com.fossscout.feature.companion

import com.fossscout.core.handoff.AppHandoff
import com.fossscout.core.handoff.HandoffFallback
import com.fossscout.core.handoff.InstalledCompanionStore
import com.fossscout.core.handoff.RepositoryHandoff
import com.fossscout.core.handoff.ReplacementAssessment
import com.fossscout.core.inventory.InstalledApp
import com.fossscout.core.matching.MatchCandidate
import com.fossscout.core.store.StoreDescriptor

enum class CompanionError {
    NO_CANDIDATE,
    INSTALLED_LOOKUP_FAILED,
    DETECTION_FAILED,
    STORE_LOOKUP_FAILED,
    PREFERENCE_READ_FAILED,
    PREFERENCE_WRITE_FAILED,
}

data class CompanionUiState(
    val isLoading: Boolean = false,
    val candidate: MatchCandidate? = null,
    val installed: InstalledApp? = null,
    val storeDescriptor: StoreDescriptor? = null,
    val assessment: ReplacementAssessment? = null,
    val clients: List<InstalledCompanionStore> = emptyList(),
    val selectedClientId: String? = null,
    val appHandoff: AppHandoff? = null,
    val repositoryHandoff: RepositoryHandoff? = null,
    val fallback: HandoffFallback? = null,
    val showReplacementGuidance: Boolean = true,
    val showHandoff: Boolean = true,
    val replaceConfirmed: Boolean = false,
    val showQr: Boolean = false,
    val error: CompanionError? = null,
    val installedLookupFailed: Boolean = false,
) {
    val selectedClient: InstalledCompanionStore?
        get() = clients.firstOrNull { it.store.id == selectedClientId }

    val requiresConfirmation: Boolean
        get() = assessment?.requiresExplicitConfirmation == true

    val canOpenAppInStore: Boolean
        get() = appHandoff != null && (!requiresConfirmation || replaceConfirmed)

    val canAddRepository: Boolean
        get() {
            val client = selectedClient ?: return false
            return repositoryHandoff != null &&
                client.enabled &&
                client.store.supportsRepositoryAdd
        }

    val qrPayload: String? get() = repositoryHandoff?.qrPayload

    val hasEnabledClient: Boolean get() = clients.any(InstalledCompanionStore::enabled)

    val hasAnyAction: Boolean
        get() = appHandoff != null || repositoryHandoff != null || fallback != null
}
