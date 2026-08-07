package org.jellyfin.mobile.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jellyfin.mobile.data.DetailRepository
import org.jellyfin.mobile.domain.Episode
import org.jellyfin.mobile.domain.ItemDetail
import org.jellyfin.mobile.domain.ItemKind
import org.jellyfin.mobile.domain.Season
import org.jellyfin.mobile.domain.UiText
import org.jellyfin.mobile.domain.asUiText
import org.jellyfin.mobile.network.SessionExpiredException
import org.jellyfin.mobile.resources.Res
import org.jellyfin.mobile.resources.detail_error_load_episodes
import org.jellyfin.mobile.resources.detail_error_load_item
import org.jellyfin.mobile.resources.detail_error_load_seasons
import org.jellyfin.mobile.resources.detail_error_update_favorite
import org.jellyfin.mobile.resources.detail_error_update_played

sealed interface DetailUiState {
    data object Loading : DetailUiState
    data class Error(val message: UiText) : DetailUiState
    data class Content(
        val detail: ItemDetail,
        /** Empty unless this is a series. A season page has episodes but no season selector. */
        val seasons: List<Season> = emptyList(),
        val selectedSeasonId: String? = null,
        val episodes: List<Episode> = emptyList(),
        val episodesLoading: Boolean = false,
        val episodesError: UiText? = null,
        /** Transient message for a failed toggle; the state itself has already been rolled back. */
        val actionError: UiText? = null,
    ) : DetailUiState
}

class DetailViewModel(
    private val itemId: String,
    private val repository: DetailRepository,
    private val onSessionExpired: () -> Unit,
) : ViewModel() {
    private val _state = MutableStateFlow<DetailUiState>(DetailUiState.Loading)
    val state: StateFlow<DetailUiState> = _state.asStateFlow()

    /** Cancelled when the user switches season, so a slow response cannot overwrite a newer one. */
    private var episodeJob: Job? = null

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.value = DetailUiState.Loading
            val detail = runCatching { repository.load(itemId) }.getOrElse { error ->
                if (error is SessionExpiredException) onSessionExpired()
                _state.value = DetailUiState.Error(error.asUiText(Res.string.detail_error_load_item))
                return@launch
            }

            _state.value = DetailUiState.Content(detail)
            loadEpisodeListFor(detail)
        }
    }

    /**
     * A series lists its seasons and shows the first one; a season lists its own episodes directly.
     * Anything else has no episode list.
     */
    private suspend fun loadEpisodeListFor(detail: ItemDetail) {
        val seriesId = detail.episodeListSeriesId ?: return

        if (detail.kind == ItemKind.Season) {
            loadEpisodes(seriesId = seriesId, seasonId = detail.id)
            return
        }

        val seasons = runCatching { repository.loadSeasons(seriesId) }.getOrElse { error ->
            if (error is SessionExpiredException) onSessionExpired()
            _state.update {
                (it as? DetailUiState.Content)?.copy(
                    episodesError = UiText.Resource(Res.string.detail_error_load_seasons),
                ) ?: it
            }
            return
        }

        _state.update {
            (it as? DetailUiState.Content)?.copy(
                seasons = seasons,
                selectedSeasonId = seasons.firstOrNull()?.id,
            ) ?: it
        }

        seasons.firstOrNull()?.let { loadEpisodes(seriesId, it.id) }
    }

    fun selectSeason(seasonId: String) {
        val content = _state.value as? DetailUiState.Content ?: return
        if (content.selectedSeasonId == seasonId) return
        val seriesId = content.detail.episodeListSeriesId ?: return

        _state.value = content.copy(selectedSeasonId = seasonId, episodes = emptyList())

        episodeJob?.cancel()
        episodeJob = viewModelScope.launch { loadEpisodes(seriesId, seasonId) }
    }

    private suspend fun loadEpisodes(seriesId: String, seasonId: String?) {
        _state.update {
            (it as? DetailUiState.Content)?.copy(episodesLoading = true, episodesError = null) ?: it
        }

        val result = runCatching { repository.loadEpisodes(seriesId, seasonId) }

        _state.update { state ->
            val content = state as? DetailUiState.Content ?: return@update state
            // A response for a season the user has already navigated away from is stale.
            if (seasonId != null && content.selectedSeasonId != null && content.selectedSeasonId != seasonId) {
                return@update state
            }
            result.fold(
                onSuccess = { content.copy(episodes = it, episodesLoading = false) },
                onFailure = { error ->
                    if (error is SessionExpiredException) onSessionExpired()
                    content.copy(
                        episodesLoading = false,
                        episodesError = UiText.Resource(Res.string.detail_error_load_episodes),
                    )
                },
            )
        }
    }

    fun toggleFavorite() = toggle(
        current = { it.isFavorite },
        applyLocally = { detail, value -> detail.copy(isFavorite = value) },
        call = { repository.setFavorite(itemId, it) },
        failureMessage = UiText.Resource(Res.string.detail_error_update_favorite),
    )

    fun togglePlayed() = toggle(
        current = { it.isPlayed },
        // Marking watched clears any resume position, so drop the progress bar to match.
        applyLocally = { detail, value ->
            detail.copy(isPlayed = value, progress = if (value) null else detail.progress)
        },
        call = { repository.setPlayed(itemId, it) },
        failureMessage = UiText.Resource(Res.string.detail_error_update_played),
        // Marking a series or season watched cascades server-side, so the episode list is stale.
        refreshEpisodes = true,
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
        failureMessage: UiText,
        refreshEpisodes: Boolean = false,
    ) {
        val content = _state.value as? DetailUiState.Content ?: return
        val target = !current(content.detail)

        _state.value = content.copy(detail = applyLocally(content.detail, target))

        viewModelScope.launch {
            runCatching { call(target) }.fold(
                onSuccess = { serverValue ->
                    // Trust the server's answer over our optimistic guess.
                    _state.update { state ->
                        (state as? DetailUiState.Content)
                            ?.copy(detail = applyLocally(state.detail, serverValue))
                            ?: state
                    }
                    if (refreshEpisodes) {
                        val state = _state.value as? DetailUiState.Content ?: return@fold
                        val seriesId = state.detail.episodeListSeriesId
                        if (seriesId != null && state.episodes.isNotEmpty()) {
                            loadEpisodes(seriesId, state.selectedSeasonId ?: state.detail.id)
                        }
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
