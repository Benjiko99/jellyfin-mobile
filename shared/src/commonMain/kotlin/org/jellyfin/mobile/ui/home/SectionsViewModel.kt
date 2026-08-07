package org.jellyfin.mobile.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.jellyfin.mobile.domain.HomeSection
import org.jellyfin.mobile.domain.UiText
import org.jellyfin.mobile.domain.asUiText
import org.jellyfin.mobile.network.SessionExpiredException
import org.jellyfin.mobile.resources.Res
import org.jellyfin.mobile.resources.error_generic

sealed interface SectionsUiState {
    data object Loading : SectionsUiState

    /**
     * @param refreshing set only for a pull-to-refresh, so the indicator belongs to the gesture the
     * user made. Automatic reloads — the Favorites tab refetching whenever it is shown — deliberately
     * leave it false rather than spinning at someone who did not ask for anything.
     */
    data class Content(
        val sections: List<HomeSection>,
        val refreshing: Boolean = false,
    ) : SectionsUiState

    data class Error(val message: UiText) : SectionsUiState
}

/**
 * Loads a list of rows.
 *
 * Both home tabs are the same screen over different queries, so they share this rather than each
 * owning a copy of the load/error/session-expiry handling. [loader] is the only difference.
 *
 * @param loadOnInit false for the Favorites tab, which loads the first time it is opened instead of
 * spending five requests on a tab the user may never look at.
 */
class SectionsViewModel(
    private val loader: suspend () -> List<HomeSection>,
    /**
     * Invoked when the server rejects our token. Persisted tokens can be revoked from the server
     * dashboard, so without this the app would sit on an unrecoverable error screen every launch.
     */
    private val onSessionExpired: () -> Unit,
    loadOnInit: Boolean = true,
) : ViewModel() {
    private val _state = MutableStateFlow<SectionsUiState>(SectionsUiState.Loading)
    val state: StateFlow<SectionsUiState> = _state.asStateFlow()

    /**
     * The in-flight load. Favorites reloads whenever its tab is shown, and each load is a five
     * request fan-out, so a second one replaces the first rather than racing it to `_state`.
     */
    private var loadJob: Job? = null

    init {
        if (loadOnInit) load()
    }

    fun load() = load(showRefreshIndicator = false)

    /** Pull-to-refresh. The same reload, but the user asked, so they get an indicator for it. */
    fun refresh() = load(showRefreshIndicator = true)

    private fun load(showRefreshIndicator: Boolean) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            // Rows already on screen stay there while reloading; replacing them with a spinner
            // makes returning to a tab flash for no reason.
            _state.value = when (val current = _state.value) {
                is SectionsUiState.Content -> current.copy(refreshing = showRefreshIndicator)
                else -> SectionsUiState.Loading
            }

            _state.value = runCatching { loader() }.fold(
                onSuccess = { SectionsUiState.Content(it) },
                onFailure = { error ->
                    if (error is SessionExpiredException) onSessionExpired()
                    SectionsUiState.Error(error.asUiText(Res.string.error_generic))
                },
            )
        }
    }
}
