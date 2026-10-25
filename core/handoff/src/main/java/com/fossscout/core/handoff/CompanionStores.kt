package com.fossscout.core.handoff

import com.fossscout.core.matching.MatchCandidate
import com.fossscout.core.store.BuiltInStores
import com.fossscout.core.store.StoreDescriptor
import java.net.URLEncoder

/**
 * A store client FossScout can hand off to. Only the package names declared in
 * the manifest are queried; the detector never asks for broad package
 * visibility.
 */
data class CompanionStore(
    val id: String,
    val displayName: String,
    val packageName: String,
    val homepage: String,
    val supportedStoreIds: Set<String> = setOf(BuiltInStores.FDROID_ID),
    val supportsRepositoryAdd: Boolean = true,
) {
    init {
        require(id.isNotBlank()) { "Companion store id is blank" }
        require(displayName.isNotBlank()) { "Companion store name is blank" }
        require(packageName.isNotBlank()) { "Companion store package is blank" }
        require(homepage.startsWith("https://")) {
            "Companion store homepage must use HTTPS"
        }
        require(supportedStoreIds.isNotEmpty()) {
            "Companion store must support at least one store id"
        }
    }

    fun supportsStore(storeId: String): Boolean = storeId in supportedStoreIds

    /** A package page intent that the store client can resolve explicitly. */
    fun appDetailsUri(packageName: String): String =
        "market://details?id=${encode(packageName)}"

    private fun encode(value: String): String =
        URLEncoder.encode(value, Charsets.UTF_8.name())
}

/**
 * Known F-Droid-compatible clients detected in v1. Adding a client requires a
 * matching `<package>` entry in the manifest so detection still works on API
 * 30+ without `QUERY_ALL_PACKAGES`.
 */
object CompanionStores {
    val fdroid: CompanionStore = CompanionStore(
        id = "fdroid",
        displayName = "F-Droid",
        packageName = "org.fdroid.fdroid",
        homepage = "https://f-droid.org",
    )

    val droidify: CompanionStore = CompanionStore(
        id = "droidify",
        displayName = "Droid-ify",
        packageName = "com.looker.droidify",
        homepage = "https://github.com/Droid-ify/client",
    )

    val neoStore: CompanionStore = CompanionStore(
        id = "neostore",
        displayName = "Neo Store",
        packageName = "com.machiav3lli.fdroid",
        homepage = "https://github.com/NeoApplications/Neo-Store",
    )

    val all: List<CompanionStore> = listOf(fdroid, droidify, neoStore)

    fun byId(id: String): CompanionStore? = all.firstOrNull { it.id == id }

    fun byPackage(packageName: String): CompanionStore? =
        all.firstOrNull { it.packageName == packageName }
}

/** An installed client and whether the platform currently enables it. */
data class InstalledCompanionStore(
    val store: CompanionStore,
    val enabled: Boolean,
    val versionName: String? = null,
)

/** The package page handoff for one candidate and one installed client. */
data class AppHandoff(
    val client: CompanionStore,
    val packageName: String,
    val marketUri: String,
    val browserUri: String,
) {
    init {
        require(packageName.isNotBlank()) { "Handoff package is blank" }
        require(marketUri.startsWith("market://")) { "Store app URI must use market://" }
        require(browserUri.startsWith("https://")) { "Browser fallback must use HTTPS" }
    }
}

/** Store-independent actions shown when no client is installed. */
data class HandoffFallback(
    val installUrl: String = CompanionStores.fdroid.homepage,
    val browserUrl: String,
) {
    init {
        require(installUrl.startsWith("https://")) { "Install URL must use HTTPS" }
        require(browserUrl.startsWith("https://")) { "Browser fallback must use HTTPS" }
    }
}

object HandoffLinks {
    /**
     * The store page a browser can open without a client. F-Droid and
     * IzzyOnDroid have stable public package pages; other stores fall back to
     * their homepage rather than inventing a URL.
     */
    fun browserAppPage(
        candidate: MatchCandidate,
        descriptor: StoreDescriptor? = null,
    ): String = when (candidate.storeId) {
        BuiltInStores.FDROID_ID ->
            "https://f-droid.org/packages/${encodePath(candidate.packageName)}/"

        BuiltInStores.IZZY_ON_DROID_ID ->
            "https://apt.izzysoft.de/fdroid/index/apk/${encodePath(candidate.packageName)}"

        else -> descriptor?.homepage
            ?.takeIf { it.startsWith("https://") }
            ?: CompanionStores.fdroid.homepage
    }

    private fun encodePath(value: String): String =
        URLEncoder.encode(value, Charsets.UTF_8.name()).replace("+", "%20")
}
