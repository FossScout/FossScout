package com.fossscout.feature.favourites

import com.fossscout.core.data.FavouritesFormatException
import com.fossscout.core.data.FavouritesRepository
import com.fossscout.core.data.FavouritesStore
import com.fossscout.core.model.AppKey
import com.fossscout.core.model.Favourite
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FavouritesViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `read failure sets a read error and stops loading`() = runTest(dispatcher) {
        val store = FakeFavouritesStore(readError = FavouritesFormatException("corrupt"))
        val viewModel = FavouritesViewModel(FavouritesRepository(store))

        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertEquals(FavouritesError.READ_FAILED, state.error)
    }

    @Test
    fun `retry restarts the read collection after a failure`() = runTest(dispatcher) {
        val favourite = favourite()
        val store = FakeFavouritesStore(
            initial = listOf(favourite),
            readError = FavouritesFormatException("corrupt"),
        )
        val viewModel = FavouritesViewModel(FavouritesRepository(store))
        advanceUntilIdle()
        assertEquals(FavouritesError.READ_FAILED, viewModel.uiState.value.error)

        store.readError = null
        viewModel.retry()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertNull(state.error)
        assertFalse(state.isLoading)
        assertEquals(listOf(favourite), state.favourites)
    }

    @Test
    fun `save failure keeps the editor open and reports the error`() = runTest(dispatcher) {
        val favourite = favourite()
        val store = FakeFavouritesStore(initial = listOf(favourite))
        val viewModel = FavouritesViewModel(FavouritesRepository(store))
        advanceUntilIdle()
        viewModel.openNote(favourite)
        viewModel.updateNoteDraft("updated note")
        store.writeError = IOException("disk full")

        viewModel.saveNote()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        val editor = state.editor
        assertNotNull(editor)
        assertTrue(editor!!.saveFailed)
        assertFalse(editor.isSaving)
        assertEquals("updated note", editor.draft)
        assertEquals(FavouritesError.SAVE_FAILED, state.error)
    }

    @Test
    fun `successful save closes the editor`() = runTest(dispatcher) {
        val favourite = favourite()
        val store = FakeFavouritesStore(initial = listOf(favourite))
        val viewModel = FavouritesViewModel(FavouritesRepository(store))
        advanceUntilIdle()
        viewModel.openNote(favourite)
        viewModel.updateNoteDraft("updated note")

        viewModel.saveNote()
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.editor)
        assertEquals("updated note", viewModel.uiState.value.favourites.single().note)
    }

    @Test
    fun `successful retry clears a previous save error`() = runTest(dispatcher) {
        val favourite = favourite()
        val store = FakeFavouritesStore(initial = listOf(favourite))
        val viewModel = FavouritesViewModel(FavouritesRepository(store))
        advanceUntilIdle()
        viewModel.openNote(favourite)
        viewModel.updateNoteDraft("updated note")
        store.writeError = IOException("disk full")
        viewModel.saveNote()
        advanceUntilIdle()
        assertEquals(FavouritesError.SAVE_FAILED, viewModel.uiState.value.error)

        store.writeError = null
        viewModel.saveNote()
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.editor)
        assertNull(viewModel.uiState.value.error)
    }

    @Test
    fun `remove failure reports the error`() = runTest(dispatcher) {
        val favourite = favourite()
        val store = FakeFavouritesStore(initial = listOf(favourite))
        val viewModel = FavouritesViewModel(FavouritesRepository(store))
        advanceUntilIdle()
        store.writeError = IOException("disk full")

        viewModel.remove(favourite.appKey)
        advanceUntilIdle()

        assertEquals(FavouritesError.REMOVE_FAILED, viewModel.uiState.value.error)
        assertEquals(listOf(favourite), viewModel.uiState.value.favourites)
    }

    private fun favourite() = Favourite(
        appKey = AppKey("org.example"),
        displayName = "Example",
        createdAtEpochMillis = 1L,
        updatedAtEpochMillis = 1L,
    )
}

private class FakeFavouritesStore(
    initial: List<Favourite> = emptyList(),
    var readError: Throwable? = null,
    var writeError: Throwable? = null,
) : FavouritesStore {
    private val state = MutableStateFlow(initial)

    override val favourites: Flow<List<Favourite>> = flow {
        readError?.let { throw it }
        emitAll(state)
    }

    override suspend fun save(favourites: List<Favourite>) {
        writeError?.let { throw it }
        state.value = favourites
    }
}
