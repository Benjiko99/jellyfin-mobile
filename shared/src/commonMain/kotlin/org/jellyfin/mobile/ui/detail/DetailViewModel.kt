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
import org.jellyfin.mobile.data.UserDataStore
import org.jellyfin.mobile.domain.Episode
import org.jellyfin.mobile.domain.ItemDetail
import org.jellyfin.mobile.domain.ItemKind
import org.jellyfin.mobile.domain.PlaylistEntry
import org.jellyfin.mobile.domain.Season
import org.jellyfin.mobile.domain.UiText
import org.jellyfin.mobile.domain.UserDataChange
import org.jellyfin.mobile.domain.applying
import org.jellyfin.mobile.domain.asUiText
import org.jellyfin.mobile.network.SessionExpiredException
import org.jellyfin.mobile.resources.Res
import org.jellyfin.mobile.resources.detail_error_load_episodes
import org.jellyfin.mobile.resources.detail_error_load_item
import org.jellyfin.mobile.resources.detail_error_load_playlist
import org.jellyfin.mobile.resources.detail_error_load_seasons
import org.jellyfin.mobile.resources.detail_error_update_favorite
import org.jellyfin.mobile.resources.detail_error_update_played
import org.jellyfin.mobile.ui.observeUserData

sealed interface DetailUiState {
    data object Loading : DetailUiState
    data class Error(val message: UiText) : DetailUiState
    data class Content(
        val detail: ItemDetail,
        /** Empty unless this is a series. A season page has episodes but no season selector. */
        val seasons: List<Season> = emptyList(),
        val selectedSeasonId: String? = null,
        val episodes: List<Episode> = emptyList(),
        /**
         * A playlist's entries, in the order they were arranged. Empty on everything else, and
         * typed differently from [episodes] because a playlist holds films as readily as episodes.
         */
        val playlistItems: List<PlaylistEntry> = emptyList(),
        /**
         * The state of whichever of the two lists above this page shows. One pair rather than two
         * because no page has both — a playlist has no episode list and a series has no entries.
         */
        val childrenLoading: Boolean = false,
        val childrenError: UiText? = null,
        /** Transient message for a failed toggle; the state itself has already been rolled back. */
        val actionError: UiText? = null,
    ) : DetailUiState
}

class DetailViewModel(
    private val itemId: String,
    private val repository: DetailRepository,
    private val userDataStore: UserDataStore,
    private val onSessionExpired: () -> Unit,
) : ViewModel() {
    private val _state = MutableStateFlow<DetailUiState>(DetailUiState.Loading)
    val state: StateFlow<DetailUiState> = _state.asStateFlow()

    /** Cancelled when the user switches season, so a slow response cannot overwrite a newer one. */
    private var episodeJob: Job? = null

    init {
        load()
        observeUserData(userDataStore, ::onUserDataChange)
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
            loadChildrenFor(detail)
        }
    }

    /**
     * A series lists its seasons and shows the first one; a season lists its own episodes directly;
     * a playlist lists its entries. Anything else has no child list at all.
     */
    private suspend fun loadChildrenFor(detail: ItemDetail) {
        if (detail.kind == ItemKind.Playlist) {
            loadPlaylistItems(detail.id)
            return
        }

        val seriesId = detail.episodeListSeriesId ?: return

        if (detail.kind == ItemKind.Season) {
            loadEpisodes(seriesId = seriesId, seasonId = detail.id)
            return
        }

        val seasons = runCatching { repository.loadSeasons(seriesId) }.getOrElse { error ->
            if (error is SessionExpiredException) onSessionExpired()
            _state.update {
                (it as? DetailUiState.Content)?.copy(
                    childrenError = UiText.Resource(Res.string.detail_error_load_seasons),
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
            (it as? DetailUiState.Content)?.copy(childrenLoading = true, childrenError = null) ?: it
        }

        val result = runCatching { repository.loadEpisodes(seriesId, seasonId) }

        _state.update { state ->
            val content = state as? DetailUiState.Content ?: return@update state
            // A response for a season the user has already navigated away from is stale.
            if (seasonId != null && content.selectedSeasonId != null && content.selectedSeasonId != seasonId) {
                return@update state
            }
            result.fold(
                onSuccess = { content.copy(episodes = it, childrenLoading = false) },
                onFailure = { error ->
                    if (error is SessionExpiredException) onSessionExpired()
                    content.copy(
                        childrenLoading = false,
                        childrenError = UiText.Resource(Res.string.detail_error_load_episodes),
                    )
                },
            )
        }
    }

    /**
     * A playlist's entries.
     *
     * No season equivalent and so no staleness check: a playlist has one list, loaded once, and
     * nothing on the page can ask for a different one.
     */
    private suspend fun loadPlaylistItems(playlistId: String) {
        _state.update {
            (it as? DetailUiState.Content)?.copy(childrenLoading = true, childrenError = null) ?: it
        }

        val result = runCatching { repository.loadPlaylistItems(playlistId) }

        _state.update { state ->
            val content = state as? DetailUiState.Content ?: return@update state
            result.fold(
                onSuccess = { content.copy(playlistItems = it, childrenLoading = false) },
                onFailure = { error ->
                    if (error is SessionExpiredException) onSessionExpired()
                    content.copy(
                        childrenLoading = false,
                        childrenError = UiText.Resource(Res.string.detail_error_load_playlist),
                    )
                },
            )
        }
    }

    fun toggleFavorite() {
        val detail = (_state.value as? DetailUiState.Content)?.detail ?: return
        val target = !detail.isFavorite
        toggle(
            apply = { it.copy(isFavorite = target) },
            revert = { it.copy(isFavorite = detail.isFavorite) },
            failureMessage = UiText.Resource(Res.string.detail_error_update_favorite),
        ) {
            userDataStore.setFavorite(itemId, target)
        }
    }

    fun togglePlayed() {
        val detail = (_state.value as? DetailUiState.Content)?.detail ?: return
        val target = !detail.isPlayed
        toggle(
            // Marking watched clears any resume position, so drop the progress bar to match.
            apply = { it.copy(isPlayed = target, progress = if (target) null else it.progress) },
            // Restored from what was replaced rather than re-derived: inverting the flag would
            // leave the resume position we just cleared gone for good.
            revert = { it.copy(isPlayed = detail.isPlayed, progress = detail.progress) },
            failureMessage = UiText.Resource(Res.string.detail_error_update_played),
        ) {
            userDataStore.setPlayed(
                itemId = itemId,
                played = target,
                // A series or season takes its episodes with it, and a collection its entries.
                cascadesToChildren = detail.isContainer,
                // Their unplayed counts move with this, and only the server knows the new numbers.
                ancestorIds = detail.ancestorIds,
            )
        }
    }

    /**
     * Applies the change immediately and reverts it if the server disagrees. These toggles are the
     * kind of thing users tap repeatedly, so waiting on a round trip before showing anything makes
     * the screen feel broken.
     *
     * Success needs no handling here. The store broadcasts what the server actually said and
     * [onUserDataChange] applies it — on this screen along with every other one showing the item,
     * which is the whole reason the write goes through the store rather than the repository.
     */
    private fun toggle(
        apply: (ItemDetail) -> ItemDetail,
        revert: (ItemDetail) -> ItemDetail,
        failureMessage: UiText,
        call: suspend () -> Unit,
    ) {
        _state.update { state ->
            val content = state as? DetailUiState.Content ?: return@update state
            content.copy(detail = apply(content.detail))
        }

        viewModelScope.launch {
            runCatching { call() }.onFailure { error ->
                if (error is SessionExpiredException) onSessionExpired()
                _state.update { state ->
                    val content = state as? DetailUiState.Content ?: return@update state
                    content.copy(detail = revert(content.detail), actionError = failureMessage)
                }
            }
        }
    }

    /**
     * Something, somewhere in the app, changed an item's watched or favourite state.
     *
     * Most of the time that is this page's own item or one of the episodes it lists, and the new
     * values are in the change. The exception is a container being marked watched: the server
     * cascades that to everything inside, and the change says nothing about what those children
     * became — so whichever side of the relationship this page is on has to re-read.
     */
    private fun onUserDataChange(change: UserDataChange) {
        val detail = (_state.value as? DetailUiState.Content)?.detail ?: return

        _state.update { state ->
            val content = state as? DetailUiState.Content ?: return@update state
            content.copy(
                detail = content.detail.applying(change),
                episodes = content.episodes.map { it.applying(change) },
                playlistItems = content.playlistItems.map { it.applying(change) },
            )
        }

        if (!change.cascadedToChildren) return

        // The episodes below this page were all marked watched along with their series or season.
        if (change.itemId == detail.id || change.itemId == detail.episodeListSeriesId) {
            refreshEpisodes()
        }
        // The other direction: this item is one of the children that just changed.
        if (change.itemId in detail.ancestorIds) reloadDetail()
    }

    /** Re-reads the episode list in place, for a cascade that invalidated all of it at once. */
    private fun refreshEpisodes() {
        val content = _state.value as? DetailUiState.Content ?: return
        val seriesId = content.detail.episodeListSeriesId ?: return
        if (content.episodes.isEmpty()) return

        episodeJob?.cancel()
        episodeJob = viewModelScope.launch {
            loadEpisodes(seriesId, content.selectedSeasonId ?: content.detail.id)
        }
    }

    /**
     * Re-reads this item without passing through [DetailUiState.Loading].
     *
     * This runs on a page sitting *under* the one the user is on, with content already drawn.
     * Blanking it to a spinner nobody asked for would be a worse answer than the stale flag it
     * replaces, and the user would be looking at the spinner when they came back.
     */
    private fun reloadDetail() {
        viewModelScope.launch {
            val detail = runCatching { repository.load(itemId) }.getOrNull() ?: return@launch
            _state.update { state ->
                (state as? DetailUiState.Content)?.copy(detail = detail) ?: state
            }
        }
    }

    fun dismissActionError() {
        _state.update { (it as? DetailUiState.Content)?.copy(actionError = null) ?: it }
    }
}
