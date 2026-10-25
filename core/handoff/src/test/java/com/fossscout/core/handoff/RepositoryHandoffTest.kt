package com.fossscout.core.handoff

import com.fossscout.core.matching.MatchCandidate
import com.fossscout.core.matching.MatchLevel
import com.fossscout.core.model.StoreId
import com.fossscout.core.store.AdapterType
import com.fossscout.core.store.BuiltInStores
import com.fossscout.core.store.StoreDescriptor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RepositoryHandoffTest {
    @Test
    fun `F-Droid descriptor has one canonical URI for every add path`() {
        val descriptor = RepositoryDescriptor.from(BuiltInStores.fdroid)
            ?: error("F-Droid must produce a repository descriptor")
        val handoff = RepositoryHandoff(descriptor)

        assertEquals(
            "fdroidrepos://f-droid.org/repo?fingerprint=" +
                BuiltInStores.FDROID_FINGERPRINT,
            descriptor.uri,
        )
        assertEquals(descriptor.uri, handoff.addRepositoryUri)
        assertEquals(descriptor.uri, handoff.qrPayload)
        assertEquals(descriptor.uri, handoff.copyPayload)
    }

    @Test
    fun `repository URL and fingerprint are preserved`() {
        val descriptor = RepositoryDescriptor.from(
            store(
                repositoryUrl = "https://example.org/fdroid/repo/",
                fingerprint = FINGERPRINT.uppercase().chunked(2).joinToString(":"),
            ),
        )

        assertEquals("https://example.org/fdroid/repo", descriptor?.repositoryUrl)
        assertEquals(FINGERPRINT, descriptor?.fingerprint)
    }

    @Test
    fun `missing or malformed fingerprint cannot be added`() {
        assertNull(
            RepositoryDescriptor.from(store(fingerprint = null)),
        )
        assertNull(
            RepositoryDescriptor.from(store(fingerprint = "not-a-fingerprint")),
        )
    }

    @Test
    fun `non F-Droid adapters cannot be added as a repository`() {
        assertNull(
            RepositoryDescriptor.from(
                store(adapterType = AdapterType.GENERIC_JSON),
            ),
        )
    }

    @Test
    fun `plain HTTP requires explicit opt-in`() {
        assertNull(
            RepositoryDescriptor.from(
                store(repositoryUrl = "http://example.org/fdroid/repo"),
            ),
        )
        val optedIn = RepositoryDescriptor.from(
            store(
                repositoryUrl = "http://example.org/fdroid/repo",
                allowInsecureHttp = true,
            ),
        )
        assertEquals(
            "fdroidrepos://example.org/fdroid/repo?fingerprint=$FINGERPRINT",
            optedIn?.uri,
        )
    }

    @Test
    fun `store package intent and browser fallback are stable`() {
        val candidate = candidate()

        assertEquals(
            "market://details?id=org.example.app",
            CompanionStores.fdroid.appDetailsUri(candidate.packageName),
        )
        assertEquals(
            "https://f-droid.org/packages/org.example.app/",
            HandoffLinks.browserAppPage(candidate, BuiltInStores.fdroid),
        )
        assertTrue(CompanionStores.all.all { it.homepage.startsWith("https://") })
        assertEquals(
            CompanionStores.fdroid,
            CompanionStores.byPackage("org.fdroid.fdroid"),
        )
    }

    private fun store(
        adapterType: AdapterType = AdapterType.FDROID_V2,
        repositoryUrl: String = "https://example.org/fdroid/repo",
        fingerprint: String? = FINGERPRINT,
        allowInsecureHttp: Boolean = false,
    ): StoreDescriptor = StoreDescriptor(
        id = StoreId("test"),
        name = "Test store",
        homepage = "https://example.org",
        adapterType = adapterType,
        indexUrl = repositoryUrl,
        repositoryUrl = repositoryUrl,
        fingerprint = fingerprint,
        allowInsecureHttp = allowInsecureHttp,
    )

    private fun candidate(): MatchCandidate = MatchCandidate(
        appKey = "org.example.app",
        level = MatchLevel.L1,
        storeId = BuiltInStores.FDROID_ID,
        storeName = "F-Droid",
        packageName = "org.example.app",
        displayName = "Example",
    )

    private companion object {
        const val FINGERPRINT =
            "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
    }
}
