package com.fossscout.core.handoff

import com.fossscout.core.matching.MatchCandidate
import com.fossscout.core.store.StoreDescriptor

/**
 * The selected client and every safe next step for one candidate. A browser
 * fallback is always present, so a missing or disabled client cannot produce a
 * dead end.
 */
data class HandoffPlan(
    val selectedClient: InstalledCompanionStore? = null,
    val appHandoff: AppHandoff? = null,
    val repositoryHandoff: RepositoryHandoff? = null,
    val fallback: HandoffFallback,
) {
    val canOpenAppInStore: Boolean get() = appHandoff != null

    val canAddRepository: Boolean
        get() = selectedClient != null &&
            selectedClient.store.supportsRepositoryAdd &&
            repositoryHandoff != null

    val hasWorkingAction: Boolean
        get() = appHandoff != null || repositoryHandoff != null || fallback.browserUrl.isNotBlank()
}

object HandoffPlanner {
    fun plan(
        candidate: MatchCandidate,
        descriptor: StoreDescriptor?,
        clients: List<InstalledCompanionStore>,
        preferredClientId: String? = null,
    ): HandoffPlan {
        val enabled = clients.filter(InstalledCompanionStore::enabled)
        val supporting = enabled.filter { it.store.supportsStore(candidate.storeId) }
        val selected = enabled.firstOrNull { it.store.id == preferredClientId }
            ?: supporting.firstOrNull()
            ?: enabled.firstOrNull()

        val browserUri = HandoffLinks.browserAppPage(candidate, descriptor)
        val appHandoff = selected
            ?.takeIf { it.store.supportsStore(candidate.storeId) }
            ?.let { installed ->
                AppHandoff(
                    client = installed.store,
                    packageName = candidate.packageName,
                    marketUri = installed.store.appDetailsUri(candidate.packageName),
                    browserUri = browserUri,
                )
            }
        val repositoryHandoff = descriptor
            ?.let(RepositoryDescriptor::from)
            ?.let(::RepositoryHandoff)
        return HandoffPlan(
            selectedClient = selected,
            appHandoff = appHandoff,
            repositoryHandoff = repositoryHandoff,
            fallback = HandoffFallback(browserUrl = browserUri),
        )
    }
}
