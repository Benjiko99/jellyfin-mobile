package org.jellyfin.mobile.ui.person

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jellyfin.mobile.data.PersonRepository
import org.jellyfin.mobile.data.UserDataStore
import org.jellyfin.mobile.domain.Filmography
import org.jellyfin.mobile.domain.PersonDetail
import org.jellyfin.mobile.domain.UiText
import org.jellyfin.mobile.domain.UserDataChange
import org.jellyfin.mobile.domain.applying
import org.jellyfin.mobile.domain.asUiText
import org.jellyfin.mobile.network.SessionExpiredException
import org.jellyfin.mobile.resources.Res
import org.jellyfin.mobile.resources.detail_error_update_favorite
import org.jellyfin.mobile.resources.person_error_load
import org.jellyfin.mobile.ui.observeUserData

sealed interface PersonUiState {
    data object Loading : PersonUiState
    data class Error(val message: UiText) : PersonUiState
    data class Content(
        val person: PersonDetail,
        val filmography: Filmography = Filmography(),
        val filmographyLoading: Boolean = true,
        val actionError: UiText? = null,
    ) : PersonUiState
}

class PersonViewModel(
    private val personId: String,
    private val repository: PersonRepository,
    private val userDataStore: UserDataStore,
    private val onSessionExpired: () -> Unit,
) : ViewModel() {
    private val _state = MutableStateFlow<PersonUiState>(PersonUiState.Loading)
    val state: StateFlow<PersonUiState> = _state.asStateFlow()

    init {
        load()
        observeUserData(userDataStore, ::onUserDataChange)
    }

    /**
     * A credit is the film or episode itself, so watching one from anywhere else in the app is what
     * moves the ticks on this page. The person's own favourite state comes through the same way.
     */
    private fun onUserDataChange(change: UserDataChange) {
        _state.update { state ->
            val content = state as? PersonUiState.Content ?: return@update state
            content.copy(
                person = content.person.applying(change),
                filmography = content.filmography.applying(change),
            )
        }
    }

    fun load() {
        viewModelScope.launch {
            _state.value = PersonUiState.Loading

            val person = runCatching { repository.load(personId) }.getOrElse { error ->
                if (error is SessionExpiredException) onSessionExpired()
                _state.value = PersonUiState.Error(error.asUiText(Res.string.person_error_load))
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
            // The server's answer comes back through [onUserDataChange] rather than being applied
            // here, so the Favorites row that also holds this person follows the same change.
            runCatching { userDataStore.setFavorite(personId, target) }.onFailure { error ->
                if (error is SessionExpiredException) onSessionExpired()
                _state.update { state ->
                    (state as? PersonUiState.Content)?.copy(
                        person = state.person.copy(isFavorite = !target),
                        actionError = UiText.Resource(Res.string.detail_error_update_favorite),
                    ) ?: state
                }
            }
        }
    }

    fun dismissActionError() {
        _state.update { (it as? PersonUiState.Content)?.copy(actionError = null) ?: it }
    }
}
