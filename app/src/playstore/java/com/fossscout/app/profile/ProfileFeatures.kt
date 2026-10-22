package com.fossscout.app.profile

import com.fossscout.core.feature.BuildProfile
import com.fossscout.core.feature.Capability
import com.fossscout.core.feature.FeatureId

object ProfileFeatures {
    val profile = BuildProfile(
        name = "playstore",
        capabilities = setOf(
            Capability.MANUAL_PACKAGE_SELECTION,
            Capability.STORE_INDEX_NETWORK,
            Capability.SHARE_IMPORT_EXPORT,
            Capability.CUSTOM_STORE_ADAPTERS,
        ),
    )

    val enabledFeatures: Set<FeatureId> = setOf(
        FeatureId.INVENTORY,
        FeatureId.MATCHING,
        FeatureId.ALTERNATIVES,
        FeatureId.STORES,
        FeatureId.INDEX_CACHE,
        FeatureId.COVERAGE,
        FeatureId.REPLACEMENT_GUIDANCE,
        FeatureId.COMPANION_HANDOFF,
        FeatureId.FAVOURITES,
        FeatureId.SHARING,
        FeatureId.BACKUP,
        FeatureId.SECURITY,
        FeatureId.CONTRIBUTION,
        FeatureId.SETTINGS,
    )
}
