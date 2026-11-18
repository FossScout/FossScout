package com.fossscout.core.matching

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StoreSearchHistoryRepositoryTest {
    @Test
    fun `record keeps newest unique queries within the limit`() = runTest {
        val repository = StoreSearchHistoryRepository(FakeStore(), limit = 3)

        repository.record("  First ")
        repository.record("second")
        repository.record("THIRD")
        repository.record("first")

        assertEquals(
            listOf("first", "THIRD", "second"),
            repository.history.first().queries,
        )
    }

    @Test
    fun `record ignores blank queries`() = runTest {
        val repository = StoreSearchHistoryRepository(FakeStore())

        repository.record("   ")

        assertTrue(repository.history.first().queries.isEmpty())
    }

    @Test
    fun `disabling history clears queries and suppresses recording`() = runTest {
        val repository = StoreSearchHistoryRepository(
            FakeStore(
                StoreSearchHistory(queries = listOf("old query")),
            ),
        )

        repository.setEnabled(false)
        repository.record("new query")

        val history = repository.history.first()
        assertFalse(history.enabled)
        assertTrue(history.queries.isEmpty())
    }

    @Test
    fun `clear removes queries but keeps the enabled preference`() = runTest {
        val store = FakeStore(
            StoreSearchHistory(enabled = true, queries = listOf("old query")),
        )
        val repository = StoreSearchHistoryRepository(store)

        repository.clear()

        val history = repository.history.first()
        assertTrue(history.enabled)
        assertTrue(history.queries.isEmpty())
    }

    @Test
    fun `stored queries are hidden when history is disabled`() = runTest {
        val repository = StoreSearchHistoryRepository(
            FakeStore(
                StoreSearchHistory(
                    enabled = false,
                    queries = listOf("old query"),
                ),
            ),
        )

        assertTrue(repository.history.first().queries.isEmpty())
    }

    @Test
    fun `stored queries are trimmed deduplicated and bounded`() = runTest {
        val repository = StoreSearchHistoryRepository(
            FakeStore(
                StoreSearchHistory(
                    queries = listOf(" one ", "ONE", "", "two", "three", "four"),
                ),
            ),
            limit = 2,
        )

        assertEquals(
            listOf("one", "two"),
            repository.history.first().queries,
        )
    }

    private class FakeStore(
        initial: StoreSearchHistory = StoreSearchHistory(),
    ) : StoreSearchHistoryStore {
        private val state = MutableStateFlow(initial)

        override val history = state

        override suspend fun save(history: StoreSearchHistory) {
            state.value = history
        }

        override suspend fun clearQueries() {
            state.update { it.copy(queries = emptyList()) }
        }
    }
}
