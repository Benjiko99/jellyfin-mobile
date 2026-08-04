package org.jellyfin.mobile.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jellyfin.mobile.data.DetailRepository
import org.jellyfin.mobile.domain.ItemDetail
import org.jellyfin.mobile.network.SessionExpiredException

sealed interface DetailUiState {
    data object Loading : DetailUiState
    data class Error(val message: String) : DetailUiState
    data class Content(
        val detail: ItemDetail,
        /** Transient message for a failed toggle; the state itself has already been rolled back. */
        val actionError: String? = null,
    ) : DetailUiState
}

class DetailViewModel(
    private val itemId: String,
    private val repository: DetailRepository,
    private val onSessionExpired: () -> Unit,
) : ViewModel() {
    private val _state = MutableStateFlow<DetailUiState>(DetailUiState.Loading)
    val state: StateFlow<DetailUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.value = DetailUiState.Loading
            _state.value = runCatching { repository.load(itemId) }.fold(
                onSuccess = { DetailUiState.Content(it) },
                onFailure = { error ->
                    if (error is SessionExpiredException) onSessionExpired()
                    DetailUiState.Error(error.message ?: "Could not load this item")
                },
            )
        }
    }

    fun toggleFavorite() = toggle(
        current = { it.isFavorite },
        applyLocally = { detail, value -> detail.copy(isFavorite = value) },
        call = { repository.setFavorite(itemId, it) },
        failureMessage = "Could not update favorites",
    )

    fun togglePlayed() = toggle(
        current = { it.isPlayed },
        // Marking watched clears any resume position, so drop the progress bar to match.
        applyLocally = { detail, value ->
            detail.copy(isPlayed = value, progress = if (value) null else detail.progress)
        },
        call = { repository.setPlayed(itemId, it) },
        failureMessage = "Could not update watched state",
    )

    /**
     * Applies the change immediately and reverts it if the server disagrees. These toggles are the
     * kind of thing users tap repeatedly, so waiting on a round trip before showing anything makes
     * the screen feel broken.
     */
    private fun toggle(
        current: (ItemDetail) -> Boolean,
        applyLocally: (ItemDetail, Boolean) -> ItemDetail,
        call: suspend (Boolean) -> Boolean,
        failureMessage: String,
    ) {
        val content = _state.value as? DetailUiState.Content ?: return
        val target = !current(content.detail)

        _state.value = DetailUiState.Content(applyLocally(content.detail, target))

        viewModelScope.launch {
            runCatching { call(target) }.fold(
                onSuccess = { serverValue ->
                    // Trust the server's answer over our optimistic guess.
                    _state.update { state ->
                        (state as? DetailUiState.Content)
                            ?.copy(detail = applyLocally(state.detail, serverValue))
                            ?: state
                    }
                },
                onFailure = { error ->
                    if (error is SessionExpiredException) onSessionExpired()
                    _state.update { state ->
                        (state as? DetailUiState.Content)?.copy(
                            detail = applyLocally(state.detail, !target),
                            actionError = failureMessage,
                        ) ?: state
                    }
                },
            )
        }
    }

    fun dismissActionError() {
        _state.update { (it as? DetailUiState.Content)?.copy(actionError = null) ?: it }
    }
}
