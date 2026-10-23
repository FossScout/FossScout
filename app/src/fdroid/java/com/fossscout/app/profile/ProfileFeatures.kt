package com.fossscout.app.profile

import com.fossscout.core.feature.BuildProfile
import com.fossscout.core.feature.Capability

object ProfileFeatures {
    val profile = BuildProfile(
        name = "fdroid",
        capabilities = setOf(
            Capability.MANUAL_PACKAGE_SELECTION,
            Capability.STORE_INDEX_NETWORK,
            Capability.SHARE_IMPORT_EXPORT,
            Capability.CUSTOM_STORE_ADAPTERS,
            Capability.BROAD_PACKAGE_VISIBILITY,
        ),
    )
}
