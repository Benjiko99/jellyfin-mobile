package org.jellyfin.mobile.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.jellyfin.mobile.data.HomeRepository
import org.jellyfin.mobile.domain.HomeSection

sealed interface HomeUiState {
    data object Loading : HomeUiState
    data class Content(val sections: List<HomeSection>, val refreshing: Boolean = false) : HomeUiState
    data class Error(val message: String) : HomeUiState
}

class HomeViewModel(
    private val repository: HomeRepository,
) : ViewModel() {
    private val _state = MutableStateFlow<HomeUiState>(HomeUiState.Loading)
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            // Keep the existing rows on screen while refreshing rather than flashing a spinner.
            _state.value = when (val current = _state.value) {
                is HomeUiState.Content -> current.copy(refreshing = true)
                else -> HomeUiState.Loading
            }

            _state.value = runCatching { repository.loadHome() }.fold(
                onSuccess = { HomeUiState.Content(it) },
                onFailure = { HomeUiState.Error(it.message ?: "Could not reach the server") },
            )
        }
    }
}
