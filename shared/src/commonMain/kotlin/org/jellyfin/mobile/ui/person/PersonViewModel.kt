package org.jellyfin.mobile.ui.person

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jellyfin.mobile.data.PersonRepository
import org.jellyfin.mobile.domain.Filmography
import org.jellyfin.mobile.domain.PersonDetail
import org.jellyfin.mobile.network.SessionExpiredException

sealed interface PersonUiState {
    data object Loading : PersonUiState
    data class Error(val message: String) : PersonUiState
    data class Content(
        val person: PersonDetail,
        val filmography: Filmography = Filmography(),
        val filmographyLoading: Boolean = true,
        val actionError: String? = null,
    ) : PersonUiState
}

class PersonViewModel(
    private val personId: String,
    private val repository: PersonRepository,
    private val onSessionExpired: () -> Unit,
) : ViewModel() {
    private val _state = MutableStateFlow<PersonUiState>(PersonUiState.Loading)
    val state: StateFlow<PersonUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.value = PersonUiState.Loading

            val person = runCatching { repository.load(personId) }.getOrElse { error ->
                if (error is SessionExpiredException) onSessionExpired()
                _state.value = PersonUiState.Error(error.message ?: "Could not load this person")
                return@launch
            }

            // Show the biography immediately; the filmography is three more round trips.
            _state.value = PersonUiState.Content(person)

            val filmography = runCatching { repository.loadFilmography(personId) }
                .getOrDefault(Filmography())

            _state.update {
                (it as? PersonUiState.Content)
                    ?.copy(filmography = filmography, filmographyLoading = false) ?: it
            }
        }
    }

    fun toggleFavorite() {
        val content = _state.value as? PersonUiState.Content ?: return
        val target = !content.person.isFavorite

        _state.value = content.copy(person = content.person.copy(isFavorite = target))

        viewModelScope.launch {
            runCatching { repository.setFavorite(personId, target) }.fold(
                onSuccess = { serverValue ->
                    _state.update { state ->
                        (state as? PersonUiState.Content)
                            ?.copy(person = state.person.copy(isFavorite = serverValue)) ?: state
                    }
                },
                onFailure = { error ->
                    if (error is SessionExpiredException) onSessionExpired()
                    _state.update { state ->
                        (state as? PersonUiState.Content)?.copy(
                            person = state.person.copy(isFavorite = !target),
                            actionError = "Could not update favorites",
                        ) ?: state
                    }
                },
            )
        }
    }

    fun dismissActionError() {
        _state.update { (it as? PersonUiState.Content)?.copy(actionError = null) ?: it }
    }
}
