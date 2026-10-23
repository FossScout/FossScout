package com.fossscout.core.feature

object FeatureCatalog {
    val all: List<FeatureDescriptor> = listOf(
        FeatureDescriptor(
            id = FeatureId.INVENTORY,
            availability = Availability.GENERAL_WITH_RESTRICTED_ENHANCEMENT,
            requiredCapabilities = setOf(Capability.MANUAL_PACKAGE_SELECTION),
            enhancedBy = setOf(Capability.BROAD_PACKAGE_VISIBILITY),
        ),
        FeatureDescriptor(
            id = FeatureId.MATCHING,
            availability = Availability.GENERAL,
            requiredCapabilities = setOf(Capability.STORE_INDEX_NETWORK),
        ),
        FeatureDescriptor(
            id = FeatureId.ALTERNATIVES,
            availability = Availability.GENERAL,
            requiredCapabilities = setOf(Capability.STORE_INDEX_NETWORK),
        ),
        FeatureDescriptor(
            id = FeatureId.STORES,
            availability = Availability.GENERAL,
            requiredCapabilities = setOf(
                Capability.CUSTOM_STORE_ADAPTERS,
                Capability.STORE_INDEX_NETWORK,
            ),
        ),
        FeatureDescriptor(
            id = FeatureId.INDEX_CACHE,
            availability = Availability.GENERAL,
            requiredCapabilities = setOf(Capability.STORE_INDEX_NETWORK),
            settings = listOf(IndexCacheSettings.storageBudget),
        ),
        FeatureDescriptor(
            id = FeatureId.COVERAGE,
            availability = Availability.GENERAL,
            requiredCapabilities = emptySet(),
        ),
        FeatureDescriptor(
            id = FeatureId.REPLACEMENT_GUIDANCE,
            availability = Availability.GENERAL,
            requiredCapabilities = setOf(Capability.MANUAL_PACKAGE_SELECTION),
            enhancedBy = setOf(Capability.DIRECT_APK_INSTALL),
        ),
        FeatureDescriptor(
            id = FeatureId.COMPANION_HANDOFF,
            availability = Availability.GENERAL,
            requiredCapabilities = setOf(Capability.MANUAL_PACKAGE_SELECTION),
        ),
        FeatureDescriptor(
            id = FeatureId.STANDALONE,
            availability = Availability.RESTRICTED,
            requiredCapabilities = setOf(Capability.DIRECT_APK_INSTALL),
        ),
        FeatureDescriptor(
            id = FeatureId.INSTALL_PROMPT,
            availability = Availability.RESTRICTED,
            requiredCapabilities = setOf(
                Capability.ACCESSIBILITY_INSTALL_PROMPT,
                Capability.ACCESSIBILITY_SERVICE,
                Capability.MANUAL_PACKAGE_SELECTION,
            ),
        ),
        FeatureDescriptor(
            id = FeatureId.FAVOURITES,
            availability = Availability.GENERAL,
            requiredCapabilities = emptySet(),
        ),
        FeatureDescriptor(
            id = FeatureId.SHARING,
            availability = Availability.GENERAL,
            requiredCapabilities = setOf(Capability.SHARE_IMPORT_EXPORT),
        ),
        FeatureDescriptor(
            id = FeatureId.BACKUP,
            availability = Availability.GENERAL,
            requiredCapabilities = setOf(Capability.SHARE_IMPORT_EXPORT),
        ),
        FeatureDescriptor(
            id = FeatureId.SECURITY,
            availability = Availability.GENERAL,
            requiredCapabilities = setOf(Capability.STORE_INDEX_NETWORK),
            enhancedBy = setOf(Capability.DIRECT_APK_INSTALL),
        ),
        FeatureDescriptor(
            id = FeatureId.UPDATES,
            availability = Availability.GENERAL_WITH_RESTRICTED_ENHANCEMENT,
            requiredCapabilities = setOf(Capability.BACKGROUND_UPDATE_CHECK),
            enhancedBy = setOf(Capability.DIRECT_APK_INSTALL),
        ),
        FeatureDescriptor(
            id = FeatureId.CONTRIBUTION,
            availability = Availability.GENERAL,
            requiredCapabilities = emptySet(),
        ),
        FeatureDescriptor(
            id = FeatureId.SETTINGS,
            availability = Availability.GENERAL,
            requiredCapabilities = emptySet(),
        ),
    )
}
