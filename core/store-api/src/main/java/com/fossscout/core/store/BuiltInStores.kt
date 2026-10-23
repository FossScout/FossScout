package com.fossscout.core.store

import com.fossscout.core.model.StoreId

/**
 * Predefined F-Droid-compatible stores.
 *
 * Fingerprints were verified on 2026-10-23 and must never be guessed:
 * - F-Droid: <https://f-droid.org/docs/Release_Channels_and_Signing_Keys/>
 *   lists this as the APK signing certificate SHA-256 fingerprint.
 * - IzzyOnDroid: <https://apt.izzysoft.de/fdroid/> publishes the repository URL
 *   with the `fingerprint` query parameter containing this value.
 *
 * The registry stores lowercase hexadecimal without separators.
 */
object BuiltInStores {
    const val FDROID_ID = "fdroid"
    const val IZZY_ON_DROID_ID = "izzyondroid"

    const val FDROID_HOMEPAGE = "https://f-droid.org"
    const val FDROID_INDEX_URL = "https://f-droid.org/repo"
    const val FDROID_FINGERPRINT =
        "43238d512c1e5eb2d6569f4a3afbf5523418b82e0a3ed1552770abb9a9c9ccab"

    const val IZZY_ON_DROID_HOMEPAGE = "https://apt.izzysoft.de/fdroid/"
    const val IZZY_ON_DROID_INDEX_URL = "https://apt.izzysoft.de/fdroid/repo"
    const val IZZY_ON_DROID_FINGERPRINT =
        "3bf0d6abfeae2f401707b6d966be743bf0eee49c2561b9ba39073711f628937a"

    val fdroid: StoreDescriptor = StoreDescriptor(
        id = StoreId(FDROID_ID),
        name = "F-Droid",
        homepage = FDROID_HOMEPAGE,
        adapterType = AdapterType.FDROID_V2,
        indexUrl = FDROID_INDEX_URL,
        fingerprint = FDROID_FINGERPRINT,
        priority = 0,
        builtIn = true,
    )

    val izzyOnDroid: StoreDescriptor = StoreDescriptor(
        id = StoreId(IZZY_ON_DROID_ID),
        name = "IzzyOnDroid",
        homepage = IZZY_ON_DROID_HOMEPAGE,
        adapterType = AdapterType.FDROID_V2,
        indexUrl = IZZY_ON_DROID_INDEX_URL,
        fingerprint = IZZY_ON_DROID_FINGERPRINT,
        priority = 1,
        builtIn = true,
    )

    fun registry(): StoreRegistry = StoreRegistry(stores = listOf(fdroid, izzyOnDroid))
}
