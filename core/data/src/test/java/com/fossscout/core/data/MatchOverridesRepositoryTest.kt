package com.fossscout.core.data

import com.fossscout.core.model.AppKey
import com.fossscout.core.model.MatchOverride
import com.fossscout.core.model.MatchOverrideTarget
import com.fossscout.core.model.StoreId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class MatchOverridesRepositoryTest {
    private val store = FakeMatchOverridesRepositoryStore()
    private var tick = 0L
    private val repository = MatchOverridesRepository(store = store, now = { tick++ })

    @Test
    fun `target is saved and later edits preserve creation time`() = runTest {
        val created = repository.setTarget(
            installedAppKey = AppKey("org.example.app"),
            target = target("org.example.alt"),
            note = "  private  ",
        )
        val updated = repository.setTarget(
            installedAppKey = AppKey("org.example.app"),
            target = target("org.example.new"),
            note = "changed",
        )

        assertEquals("private", created.note)
        assertEquals(0L, created.createdAtEpochMillis)
        assertEquals(created.createdAtEpochMillis, updated.createdAtEpochMillis)
        assertEquals(1L, updated.updatedAtEpochMillis)
        assertEquals(listOf(updated), repository.overrides.first())
    }

    @Test
    fun `no-match correction stores a null target`() = runTest {
        val saved = repository.setNoMatch(AppKey("org.example.app"), "not portable")

        assertTrue(saved.isNoMatch)
        assertTrue(repository.overrides.first().single().isNoMatch)
    }

    @Test
    fun `remove deletes only the requested app`() = runTest {
        repository.setTarget(AppKey("org.example.a"), target("org.example.a.alt"))
        repository.setTarget(AppKey("org.example.b"), target("org.example.b.alt"))

        repository.remove(AppKey("org.example.a"))

        assertEquals(
            listOf("org.example.b"),
            repository.overrides.first().map { it.installedAppKey.value },
        )
    }

    @Test
    fun `remove of an unknown app does not write`() = runTest {
        repository.remove(AppKey("org.example.missing"))

        assertEquals(0, store.saveCalls)
    }

    @Test
    fun `replaceAll rejects duplicate app keys`() {
        val override = MatchOverride(
            installedAppKey = AppKey("org.example.app"),
            target = target("org.example.alt"),
            createdAtEpochMillis = 1,
            updatedAtEpochMillis = 2,
        )

        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { repository.replaceAll(listOf(override, override)) }
        }
    }

    private fun target(appKey: String) = MatchOverrideTarget(
        appKey = AppKey(appKey),
        displayName = "Alternative",
        storeId = StoreId("store"),
        storeName = "Store",
    )
}

private class FakeMatchOverridesRepositoryStore(
    initial: List<MatchOverride> = emptyList(),
) : MatchOverridesStore {
    private val state = MutableStateFlow(initial)

    var saveCalls: Int = 0
        private set

    override val overrides: Flow<List<MatchOverride>> = state

    override suspend fun save(overrides: List<MatchOverride>) {
        saveCalls += 1
        state.value = overrides
    }
}
