package com.fossscout.core.data

import com.fossscout.core.model.StoreId
import com.fossscout.core.store.AdapterType
import com.fossscout.core.store.BuiltInStores
import com.fossscout.core.store.LinkOnlyReason
import com.fossscout.core.store.StoreDescriptor
import com.fossscout.core.store.StoreDraft
import com.fossscout.core.store.StoreEntry
import com.fossscout.core.store.StoreMode
import com.fossscout.core.store.StoreRegistry
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StoreRegistryRepositoryTest {
    @Test
    fun `fresh registry lists predefined stores in priority order`() = runTest {
        val repository = repository()

        val entries = repository.entries.first()

        assertEquals(
            listOf(BuiltInStores.FDROID_ID, BuiltInStores.IZZY_ON_DROID_ID),
            entries.map { it.descriptor.id.value },
        )
        assertEquals(listOf(0, 1), entries.map { it.descriptor.priority })
        assertTrue(entries.all(StoreEntry::matchable))
    }

    @Test
    fun `add edit enable disable and delete affect the next query`() = runTest {
        val repository = repository()

        val added = repository.add(draft(name = "Custom repo"))
        assertEquals(2, added.priority)
        assertTrue(repository.entries.first().last().matchable)

        val updated = repository.update(
            added.id,
            draft(
                name = "Renamed repo",
                homepage = "https://renamed.example",
                indexUrl = "https://renamed.example/repo",
            ),
        )
        assertEquals(added.id, updated.id)
        assertEquals(added.priority, updated.priority)
        assertEquals("Renamed repo", repository.entries.first().last().descriptor.name)

        repository.setEnabled(added.id, false)
        assertFalse(repository.entries.first().last().matchable)
        assertTrue(repository.matchableDescriptors.first().none { it.id == added.id })

        repository.setEnabled(added.id, true)
        assertTrue(repository.entries.first().last().matchable)
        assertTrue(repository.matchableDescriptors.first().any { it.id == added.id })

        repository.delete(added.id)
        assertEquals(2, repository.entries.first().size)
        assertTrue(repository.entries.first().none { it.descriptor.id == added.id })
    }

    @Test
    fun `priority is normalized and reorder changes matching order`() = runTest {
        val repository = repository()
        val first = repository.add(draft(name = "First"))
        val second = repository.add(draft(name = "Second"))

        assertEquals(listOf(0, 1, 2, 3), repository.entries.first().map {
            it.descriptor.priority
        })

        assertTrue(repository.move(second.id, offset = -1))

        var entries = repository.entries.first()
        assertEquals(
            listOf(
                BuiltInStores.FDROID_ID,
                BuiltInStores.IZZY_ON_DROID_ID,
                second.id.value,
                first.id.value,
            ),
            entries.map { it.descriptor.id.value },
        )
        assertEquals(listOf(0, 1, 2, 3), entries.map { it.descriptor.priority })

        assertTrue(repository.move(
            id = StoreId(BuiltInStores.FDROID_ID),
            offset = 1,
        ))
        entries = repository.entries.first()
        assertEquals(
            StoreId(BuiltInStores.IZZY_ON_DROID_ID),
            entries.first().descriptor.id,
        )

        repository.reorder(
            listOf(first.id, second.id, StoreId(BuiltInStores.FDROID_ID),
                StoreId(BuiltInStores.IZZY_ON_DROID_ID)),
        )
        entries = repository.entries.first()
        assertEquals(
            listOf(first.id, second.id),
            entries.take(2).map { it.descriptor.id },
        )
        assertEquals(listOf(0, 1, 2, 3), entries.map { it.descriptor.priority })
        assertFalse(repository.move(first.id, offset = -1))
    }

    @Test
    fun `reorder rejects an incomplete set`() = runTest {
        val repository = repository()

        val result = runCatching {
            repository.reorder(listOf(StoreId(BuiltInStores.FDROID_ID)))
        }

        assertTrue(result.exceptionOrNull() is IllegalArgumentException)
        assertEquals(2, repository.entries.first().size)
    }

    @Test
    fun `unsupported adapter is persisted as link-only with a reason`() = runTest {
        val repository = repository()

        val added = repository.add(
            draft(
                name = "Source store",
                homepage = "https://code.example/project",
                adapterType = AdapterType.SOURCE_REPOSITORY,
                indexUrl = "",
            ),
        )

        val entry = repository.entries.first().last()
        assertEquals(added.id, entry.descriptor.id)
        assertEquals(StoreMode.LINK_ONLY, entry.availability.mode)
        assertEquals(LinkOnlyReason.ADAPTER_UNSUPPORTED, entry.availability.reason)
        assertFalse(entry.matchable)
        assertTrue(repository.matchableDescriptors.first().none { it.id == added.id })
    }

    @Test
    fun `F-Droid repository URL defaults to the index endpoint`() = runTest {
        val repository = repository()

        val added = repository.add(
            draft(
                name = "Small repo",
                homepage = "https://repo.example/fdroid",
                indexUrl = "",
            ),
        )

        assertEquals("https://repo.example/fdroid", added.indexUrl)
        val entry = repository.entries.first().last()
        assertEquals(added.id, entry.descriptor.id)
        assertTrue(entry.matchable)
    }

    @Test
    fun `HTTP store requires explicit opt-in for add and edit`() = runTest {
        val repository = repository()

        val rejectedAdd = runCatching {
            repository.add(
                draft(
                    name = "Insecure",
                    homepage = "http://insecure.example",
                    indexUrl = "http://insecure.example/repo",
                ),
            )
        }
        assertTrue(rejectedAdd.exceptionOrNull() is IllegalArgumentException)
        assertEquals(2, repository.entries.first().size)

        val added = repository.add(
            draft(
                name = "Insecure",
                homepage = "http://insecure.example",
                indexUrl = "http://insecure.example/repo",
                allowInsecureHttp = true,
            ),
        )
        assertTrue(added.allowInsecureHttp)

        val rejectedEdit = runCatching {
            repository.update(
                added.id,
                draft(
                    name = "Insecure",
                    homepage = "http://other.example",
                    indexUrl = "http://other.example/repo",
                ),
            )
        }
        assertTrue(rejectedEdit.exceptionOrNull() is IllegalArgumentException)
        val unchanged = repository.entries.first().last().descriptor
        assertEquals("http://insecure.example", unchanged.homepage)
        assertTrue(unchanged.allowInsecureHttp)
    }

    @Test
    fun `malformed store update leaves the previous registry untouched`() = runTest {
        val repository = repository()
        val fdroid = repository.entries.first().first().descriptor

        val result = runCatching {
            repository.update(
                fdroid.id,
                draft(
                    name = "Broken",
                    indexUrl = "not a url",
                ),
            )
        }

        assertTrue(result.exceptionOrNull() is IllegalArgumentException)
        assertEquals(fdroid, repository.entries.first().first().descriptor)
    }

    @Test
    fun `descriptor export is portable and versioned`() = runTest {
        val repository = repository()

        val encoded = repository.exportDescriptor(StoreId(BuiltInStores.IZZY_ON_DROID_ID))
        val decoded = StoreRegistryCodec().decodeDescriptor(encoded)

        assertEquals(BuiltInStores.izzyOnDroid, decoded)
        assertTrue(encoded.contains("\"schemaVersion\":1"))
    }

    @Test
    fun `replace custom keeps built-ins and restores a pinned fingerprint`() = runTest {
        val repository = repository()
        val custom = StoreDescriptor(
            id = StoreId("custom-backup"),
            name = "Restored repo",
            homepage = "https://example.org",
            adapterType = AdapterType.FDROID_V2,
            indexUrl = "https://example.org/repo",
            fingerprint = "f".repeat(64),
            trustNote = "Restored trust note",
            priority = 2,
        )

        repository.replaceCustom(listOf(custom))

        val entries = repository.entries.first()
        assertEquals(
            listOf(BuiltInStores.FDROID_ID, BuiltInStores.IZZY_ON_DROID_ID),
            entries.take(2).map { it.descriptor.id.value },
        )
        assertEquals("f".repeat(64), entries.last().descriptor.fingerprint)
        assertEquals("Restored trust note", entries.last().descriptor.trustNote)
    }

    @Test
    fun `replace custom rejects a built-in id`() = runTest {
        val repository = repository()

        val result = runCatching {
            repository.replaceCustom(
                listOf(
                    StoreDescriptor(
                        id = StoreId(BuiltInStores.FDROID_ID),
                        name = "Fake",
                        homepage = "https://example.org",
                        adapterType = AdapterType.FDROID_V2,
                        indexUrl = "https://example.org/repo",
                    ),
                ),
            )
        }

        assertTrue(result.exceptionOrNull() is IllegalArgumentException)
    }

    @Test
    fun `read failure is surfaced without writing a replacement registry`() = runTest {
        val store = FakeStoreRegistryStore(readError = IOException("unreadable"))
        val repository = repository(store)

        val result = runCatching { repository.entries.first() }

        assertTrue(result.exceptionOrNull() is IOException)
        assertEquals(0, store.saveCount)
    }

    private fun repository(
        store: FakeStoreRegistryStore = FakeStoreRegistryStore(),
    ): StoreRegistryRepository {
        var nextId = 0
        return StoreRegistryRepository(
            store = store,
            idFactory = { StoreId("custom-${nextId++}") },
        )
    }

    private fun draft(
        name: String,
        homepage: String = "https://example.org",
        adapterType: AdapterType = AdapterType.FDROID_V2,
        indexUrl: String = "https://example.org/repo",
        allowInsecureHttp: Boolean = false,
    ): StoreDraft = StoreDraft(
        name = name,
        homepage = homepage,
        adapterType = adapterType,
        indexUrl = indexUrl,
        allowInsecureHttp = allowInsecureHttp,
    )
}

private class FakeStoreRegistryStore(
    initial: StoreRegistry = BuiltInStores.registry(),
    var readError: Throwable? = null,
) : StoreRegistryStore {
    val state = MutableStateFlow(initial)
    var saveCount = 0
        private set

    override val registry: Flow<StoreRegistry> = flow {
        readError?.let { throw it }
        emitAll(state)
    }

    override suspend fun save(registry: StoreRegistry) {
        saveCount += 1
        state.value = registry
    }
}
