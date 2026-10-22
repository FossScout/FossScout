package com.fossscout.feature.favourites

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fossscout.core.data.FavouritesRepository
import com.fossscout.core.model.AppKey
import com.fossscout.core.model.Favourite
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class FavouritesError { READ_FAILED, SAVE_FAILED, REMOVE_FAILED }

data class FavouriteEditor(
    val appKey: AppKey,
    val displayName: String,
    val draft: String,
    val isSaving: Boolean = false,
    val saveFailed: Boolean = false,
)

data class FavouritesUiState(
    val favourites: List<Favourite> = emptyList(),
    val isLoading: Boolean = true,
    val error: FavouritesError? = null,
    val editor: FavouriteEditor? = null,
)

class FavouritesViewModel(
    private val repository: FavouritesRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(FavouritesUiState())
    val uiState: StateFlow<FavouritesUiState> = _uiState.asStateFlow()

    private var readJob: Job? = null

    init {
        collectFavourites()
    }

    fun retry() {
        collectFavourites()
    }

    fun openNote(favourite: Favourite) {
        _uiState.update { state ->
            state.copy(
                editor = FavouriteEditor(
                    appKey = favourite.appKey,
                    displayName = favourite.displayName,
                    draft = favourite.note,
                ),
                error = state.error.takeUnless { it == FavouritesError.SAVE_FAILED },
            )
        }
    }

    fun updateNoteDraft(draft: String) {
        _uiState.update { state ->
            state.copy(editor = state.editor?.copy(draft = draft, saveFailed = false))
        }
    }

    fun saveNote() {
        val editor = _uiState.value.editor ?: return
        if (editor.isSaving) return

        val normalized = try {
            normalizeNote(editor.draft)
        } catch (exception: IllegalArgumentException) {
            Log.e(TAG, "Refusing to save an invalid note", exception)
            return
        }

        _uiState.update { state ->
            state.copy(editor = state.editor?.copy(isSaving = true, saveFailed = false))
        }
        viewModelScope.launch {
            try {
                repository.updateNote(editor.appKey, normalized)
                _uiState.update { state ->
                    state.copy(
                        editor = null,
                        error = state.error.takeUnless { it == FavouritesError.SAVE_FAILED },
                    )
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (throwable: Throwable) {
                Log.e(TAG, "Failed to save note for ${editor.appKey.value}", throwable)
                _uiState.update { state ->
                    state.copy(
                        editor = state.editor?.copy(isSaving = false, saveFailed = true),
                        error = FavouritesError.SAVE_FAILED,
                    )
                }
            }
        }
    }

    fun dismissEditor() {
        _uiState.update { state ->
            state.copy(
                editor = null,
                error = state.error.takeUnless { it == FavouritesError.SAVE_FAILED },
            )
        }
    }

    fun remove(appKey: AppKey) {
        viewModelScope.launch {
            try {
                repository.remove(appKey)
                _uiState.update { state ->
                    state.copy(
                        error = state.error.takeUnless { it == FavouritesError.REMOVE_FAILED },
                    )
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (throwable: Throwable) {
                Log.e(TAG, "Failed to remove ${appKey.value}", throwable)
                _uiState.update { state -> state.copy(error = FavouritesError.REMOVE_FAILED) }
            }
        }
    }

    fun dismissError() {
        _uiState.update { state -> state.copy(error = null) }
    }

    private fun collectFavourites() {
        readJob?.cancel()
        _uiState.update { state ->
            state.copy(
                isLoading = true,
                error = state.error.takeUnless { it == FavouritesError.READ_FAILED },
            )
        }
        readJob = viewModelScope.launch {
            repository.favourites
                .catch { throwable ->
                    if (throwable is CancellationException) throw throwable
                    Log.e(TAG, "Failed to read favourites", throwable)
                    _uiState.update { state ->
                        state.copy(isLoading = false, error = FavouritesError.READ_FAILED)
                    }
                }
                .collect { favourites ->
                    _uiState.update { state ->
                        state.copy(
                            favourites = favourites,
                            isLoading = false,
                            error = state.error.takeUnless { it == FavouritesError.READ_FAILED },
                        )
                    }
                }
        }
    }

    private companion object {
        const val TAG = "FavouritesViewModel"
    }
}
