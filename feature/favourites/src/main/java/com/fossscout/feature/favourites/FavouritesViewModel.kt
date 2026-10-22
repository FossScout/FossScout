package com.fossscout.feature.favourites

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fossscout.core.data.FavouritesRepository
import com.fossscout.core.model.AppKey
import com.fossscout.core.model.Favourite
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

data class FavouritesUiState(
    val favourites: List<Favourite> = emptyList(),
)

class FavouritesViewModel(
    private val repository: FavouritesRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(FavouritesUiState())
    val uiState: StateFlow<FavouritesUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.favourites
                .catch { throwable ->
                    Log.e(TAG, "Failed to read favourites", throwable)
                }
                .collect { favourites ->
                    _uiState.value = FavouritesUiState(favourites = favourites)
                }
        }
    }

    fun saveNote(appKey: AppKey, note: String) {
        viewModelScope.launch {
            runCatching {
                repository.updateNote(appKey, normalizeNote(note))
            }.onFailure { throwable ->
                Log.e(TAG, "Failed to save note for ${appKey.value}", throwable)
            }
        }
    }

    fun remove(appKey: AppKey) {
        viewModelScope.launch {
            runCatching {
                repository.remove(appKey)
            }.onFailure { throwable ->
                Log.e(TAG, "Failed to remove ${appKey.value}", throwable)
            }
        }
    }

    private companion object {
        const val TAG = "FavouritesViewModel"
    }
}
