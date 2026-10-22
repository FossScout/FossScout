package com.fossscout.feature.favourites

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
                .catch { /* Keep the last good state if local storage cannot be read. */ }
                .collect { favourites ->
                    _uiState.value = FavouritesUiState(favourites = favourites)
                }
        }
    }

    fun saveNote(appKey: AppKey, note: String) {
        val normalized = normalizeNote(note)
        viewModelScope.launch {
            repository.updateNote(appKey, normalized)
        }
    }

    fun remove(appKey: AppKey) {
        viewModelScope.launch {
            repository.remove(appKey)
        }
    }
}
