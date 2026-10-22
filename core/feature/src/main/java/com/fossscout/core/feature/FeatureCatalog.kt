package com.fossscout.core.feature

object FeatureCatalog {
    val all: List<FeatureDescriptor> = listOf(
        FeatureDescriptor(
            id = FeatureId.INVENTORY,
            title = "Installed app inventory",
            availability = Availability.GENERAL_WITH_RESTRICTED_ENHANCEMENT,
            requiredCapabilities = setOf(Capability.MANUAL_PACKAGE_SELECTION),
            enhancedBy = setOf(Capability.BROAD_PACKAGE_VISIBILITY),
        ),
        FeatureDescriptor(
            id = FeatureId.MATCHING,
            title = "FOSS matching",
            availability = Availability.GENERAL,
            requiredCapabilities = setOf(Capability.STORE_INDEX_NETWORK),
        ),
        FeatureDescriptor(
            id = FeatureId.ALTERNATIVES,
            title = "Alternative suggestions",
            availability = Availability.GENERAL,
            requiredCapabilities = setOf(Capability.STORE_INDEX_NETWORK),
        ),
        FeatureDescriptor(
            id = FeatureId.STORES,
            title = "Store registry and adapters",
            availability = Availability.GENERAL,
            requiredCapabilities = setOf(
                Capability.CUSTOM_STORE_ADAPTERS,
                Capability.STORE_INDEX_NETWORK,
            ),
        ),
        FeatureDescriptor(
            id = FeatureId.INDEX_CACHE,
            title = "Index sync and offline cache",
            availability = Availability.GENERAL,
            requiredCapabilities = setOf(Capability.STORE_INDEX_NETWORK),
        ),
        FeatureDescriptor(
            id = FeatureId.COVERAGE,
            title = "Coverage and confidence",
            availability = Availability.GENERAL,
            requiredCapabilities = emptySet(),
        ),
        FeatureDescriptor(
            id = FeatureId.REPLACEMENT_GUIDANCE,
            title = "Replacement guidance",
            availability = Availability.GENERAL,
            requiredCapabilities = setOf(Capability.MANUAL_PACKAGE_SELECTION),
            enhancedBy = setOf(Capability.DIRECT_APK_INSTALL),
        ),
        FeatureDescriptor(
            id = FeatureId.COMPANION_HANDOFF,
            title = "Companion store handoff",
            availability = Availability.GENERAL,
            requiredCapabilities = setOf(Capability.MANUAL_PACKAGE_SELECTION),
        ),
        FeatureDescriptor(
            id = FeatureId.STANDALONE,
            title = "Standalone store client",
            availability = Availability.RESTRICTED,
            requiredCapabilities = setOf(Capability.DIRECT_APK_INSTALL),
        ),
        FeatureDescriptor(
            id = FeatureId.INSTALL_PROMPT,
            title = "Install-time suggestion",
            availability = Availability.RESTRICTED,
            requiredCapabilities = setOf(
                Capability.ACCESSIBILITY_INSTALL_PROMPT,
                Capability.ACCESSIBILITY_SERVICE,
                Capability.MANUAL_PACKAGE_SELECTION,
            ),
        ),
        FeatureDescriptor(
            id = FeatureId.FAVOURITES,
            title = "Favourites and notes",
            availability = Availability.GENERAL,
            requiredCapabilities = emptySet(),
        ),
        FeatureDescriptor(
            id = FeatureId.SHARING,
            title = "Collections and sharing",
            availability = Availability.GENERAL,
            requiredCapabilities = setOf(Capability.SHARE_IMPORT_EXPORT),
        ),
        FeatureDescriptor(
            id = FeatureId.BACKUP,
            title = "Backup and migration",
            availability = Availability.GENERAL,
            requiredCapabilities = setOf(Capability.SHARE_IMPORT_EXPORT),
        ),
        FeatureDescriptor(
            id = FeatureId.SECURITY,
            title = "Security verification",
            availability = Availability.GENERAL,
            requiredCapabilities = setOf(Capability.STORE_INDEX_NETWORK),
            enhancedBy = setOf(Capability.DIRECT_APK_INSTALL),
        ),
        FeatureDescriptor(
            id = FeatureId.UPDATES,
            title = "Update tracking",
            availability = Availability.GENERAL_WITH_RESTRICTED_ENHANCEMENT,
            requiredCapabilities = setOf(Capability.BACKGROUND_UPDATE_CHECK),
            enhancedBy = setOf(Capability.DIRECT_APK_INSTALL),
        ),
        FeatureDescriptor(
            id = FeatureId.CONTRIBUTION,
            title = "Contribution hooks",
            availability = Availability.GENERAL,
            requiredCapabilities = emptySet(),
        ),
        FeatureDescriptor(
            id = FeatureId.SETTINGS,
            title = "Feature configuration",
            availability = Availability.GENERAL,
            requiredCapabilities = emptySet(),
        ),
    )
}
