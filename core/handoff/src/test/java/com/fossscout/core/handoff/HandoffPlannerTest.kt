package com.fossscout.core.handoff

import com.fossscout.core.matching.MatchCandidate
import com.fossscout.core.matching.MatchLevel
import com.fossscout.core.store.BuiltInStores
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HandoffPlannerTest {
    @Test
    fun `no client still has install and browser fallbacks`() {
        val plan = HandoffPlanner.plan(
            candidate = candidate(),
            descriptor = BuiltInStores.fdroid,
            clients = emptyList(),
        )

        assertNull(plan.selectedClient)
        assertNull(plan.appHandoff)
        assertNotNull(plan.repositoryHandoff)
        assertTrue(plan.fallback.installUrl.startsWith("https://"))
        assertTrue(plan.fallback.browserUrl.startsWith("https://"))
        assertTrue(plan.hasWorkingAction)
    }

    @Test
    fun `installed F-Droid gets the correct package intent`() {
        val plan = HandoffPlanner.plan(
            candidate = candidate(),
            descriptor = BuiltInStores.fdroid,
            clients = listOf(InstalledCompanionStore(CompanionStores.fdroid, enabled = true)),
        )

        assertEquals(CompanionStores.fdroid, plan.selectedClient?.store)
        assertEquals(
            "market://details?id=org.example.app",
            plan.appHandoff?.marketUri,
        )
        assertEquals(
            "https://f-droid.org/packages/org.example.app/",
            plan.appHandoff?.browserUri,
        )
        assertTrue(plan.canAddRepository)
    }

    @Test
    fun `preferred client is selected when installed`() {
        val plan = HandoffPlanner.plan(
            candidate = candidate(),
            descriptor = BuiltInStores.fdroid,
            clients = listOf(
                InstalledCompanionStore(CompanionStores.fdroid, enabled = true),
                InstalledCompanionStore(CompanionStores.droidify, enabled = true),
            ),
            preferredClientId = CompanionStores.droidify.id,
        )

        assertEquals(CompanionStores.droidify, plan.selectedClient?.store)
        assertEquals(
            "market://details?id=org.example.app",
            plan.appHandoff?.marketUri,
        )
    }

    @Test
    fun `disabled client is not selected`() {
        val plan = HandoffPlanner.plan(
            candidate = candidate(),
            descriptor = BuiltInStores.fdroid,
            clients = listOf(InstalledCompanionStore(CompanionStores.fdroid, enabled = false)),
        )

        assertNull(plan.selectedClient)
        assertNull(plan.appHandoff)
        assertFalse(plan.canAddRepository)
        assertTrue(plan.hasWorkingAction)
    }

    @Test
    fun `candidate from an unsupported store still exposes repository and browser`() {
        val plan = HandoffPlanner.plan(
            candidate = candidate(storeId = BuiltInStores.IZZY_ON_DROID_ID),
            descriptor = BuiltInStores.izzyOnDroid,
            clients = listOf(InstalledCompanionStore(CompanionStores.fdroid, enabled = true)),
        )

        assertEquals(CompanionStores.fdroid, plan.selectedClient?.store)
        assertNull(plan.appHandoff)
        assertNotNull(plan.repositoryHandoff)
        assertTrue(plan.fallback.browserUrl.contains("izzysoft.de"))
    }

    @Test
    fun `missing descriptor still produces a browser fallback`() {
        val plan = HandoffPlanner.plan(
            candidate = candidate(),
            descriptor = null,
            clients = emptyList(),
        )

        assertNull(plan.repositoryHandoff)
        assertTrue(plan.fallback.browserUrl.startsWith("https://"))
        assertTrue(plan.hasWorkingAction)
    }

    private fun candidate(
        storeId: String = BuiltInStores.FDROID_ID,
    ): MatchCandidate = MatchCandidate(
        appKey = "org.example.app",
        level = MatchLevel.L1,
        storeId = storeId,
        storeName = if (storeId == BuiltInStores.FDROID_ID) "F-Droid" else "IzzyOnDroid",
        packageName = "org.example.app",
        displayName = "Example",
    )
}
