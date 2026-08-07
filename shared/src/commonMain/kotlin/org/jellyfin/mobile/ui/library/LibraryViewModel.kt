package org.jellyfin.mobile.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jellyfin.mobile.data.LibraryRepository
import org.jellyfin.mobile.domain.LibraryFilterOptions
import org.jellyfin.mobile.domain.LibraryFilters
import org.jellyfin.mobile.domain.LibraryKind
import org.jellyfin.mobile.domain.LibraryTab
import org.jellyfin.mobile.domain.MediaItem
import org.jellyfin.mobile.network.SessionExpiredException

/**
 * One tab's grid.
 *
 * [items] survives a reload rather than being cleared to a spinner: changing a filter or jumping to
 * a letter re-queries the same library, and blanking the screen for every tap of the alphabet rail
 * would flash far more than it informs. [reloading] is what tells the UI to dim it instead.
 */
data class LibraryUiState(
    val tab: LibraryTab,
    val filters: LibraryFilters = LibraryFilters(),
    val startLetter: String? = null,
    val filterOptions: LibraryFilterOptions = LibraryFilterOptions(),
    val items: List<MediaItem> = emptyList(),
    val loadingFirstPage: Boolean = true,
    val reloading: Boolean = false,
    val loadingMore: Boolean = false,
    val endReached: Boolean = false,
    val totalCount: Int? = null,
    val error: String? = null,
    val loadMoreFailed: Boolean = false,
) {
    /** An empty grid is only worth explaining once the query behind it has finished. */
    val isEmpty: Boolean get() = items.isEmpty() && !loadingFirstPage && error == null
}

/**
 * A library browse screen: tabs across the top, one paged grid under whichever is selected.
 *
 * Paging follows [org.jellyfin.mobile.ui.section.SectionListViewModel] — a first page that owns the
 * error state, then appended pages that fail without discarding what is on screen. What this adds
 * is that the query itself changes: a tab, a filter set and a letter all rewrite it, and each of
 * those has to start the list again from index zero.
 */
class LibraryViewModel(
    private val libraryId: String,
    libraryKind: LibraryKind,
    private val repository: LibraryRepository,
    private val onSessionExpired: () -> Unit,
) : ViewModel() {
    val tabs: List<LibraryTab> = LibraryTab.forLibrary(libraryKind)

    private val _state = MutableStateFlow(LibraryUiState(tab = tabs.first()))
    val state: StateFlow<LibraryUiState> = _state.asStateFlow()

    /**
     * The in-flight page. Tapping three letters in a row starts three queries, and without this the
     * slowest would win and write its results over the letter the user actually picked.
     */
    private var loadJob: Job? = null

    init {
        loadFilterOptions()
        loadNextPage()
    }

    /**
     * Filters are not carried across tabs.
     *
     * They cannot be: the genres of one tab are not the genres of the next — "Reality TV" does not
     * exist in a movie library — so a carried filter would produce an empty grid with nothing on
     * screen to explain why.
     */
    fun selectTab(tab: LibraryTab) {
        if (tab == _state.value.tab) return
        _state.value = LibraryUiState(tab = tab)
        loadFilterOptions()
        loadNextPage()
    }

    fun setFilters(filters: LibraryFilters) {
        if (filters == _state.value.filters) return
        _state.update { it.copy(filters = filters) }
        restart()
    }

    /** A letter from the rail, or null for the whole list. Tapping the current letter clears it. */
    fun selectLetter(letter: String?) {
        val next = letter.takeIf { it != _state.value.startLetter }
        if (next == _state.value.startLetter) return
        _state.update { it.copy(startLetter = next) }
        restart()
    }

    /** Re-runs the query from index zero, keeping what is on screen until the first page lands. */
    private fun restart() {
        loadJob?.cancel()
        _state.update {
            it.copy(
                items = emptyList(),
                reloading = it.items.isNotEmpty(),
                loadingFirstPage = true,
                loadingMore = false,
                endReached = false,
                totalCount = null,
                error = null,
                loadMoreFailed = false,
            )
        }
        loadNextPage()
    }

    fun loadNextPage() {
        val current = _state.value
        if (current.loadingMore || current.endReached) return
        if (!current.loadingFirstPage && current.loadMoreFailed) return

        _state.value = current.copy(loadingMore = true, loadMoreFailed = false)

        loadJob = viewModelScope.launch {
            val query = _state.value
            runCatching {
                repository.loadPage(
                    libraryId = libraryId,
                    tab = query.tab,
                    filters = query.filters,
                    startLetter = query.startLetter,
                    startIndex = query.items.size,
                )
            }
                .onSuccess { page ->
                    _state.update { state ->
                        state.copy(
                            // Deduped by id, as in the section list: paging is by index, so an item
                            // added mid-scroll shifts every later one and re-serves the boundary.
                            items = (state.items + page.items).distinctBy { it.id },
                            loadingFirstPage = false,
                            reloading = false,
                            loadingMore = false,
                            totalCount = page.totalCount ?: state.totalCount,
                            endReached = page.endReached,
                            error = null,
                        )
                    }
                }
                .onFailure { error ->
                    if (error is SessionExpiredException) onSessionExpired()
                    _state.update { state ->
                        if (state.loadingFirstPage) {
                            state.copy(
                                loadingFirstPage = false,
                                reloading = false,
                                loadingMore = false,
                                error = error.message ?: "Could not reach the server",
                            )
                        } else {
                            state.copy(loadingMore = false, loadMoreFailed = true)
                        }
                    }
                }
        }
    }

    fun retry() {
        val current = _state.value
        _state.value = if (current.items.isEmpty()) {
            current.copy(
                loadingFirstPage = true,
                loadingMore = false,
                endReached = false,
                error = null,
                loadMoreFailed = false,
            )
        } else {
            current.copy(loadMoreFailed = false)
        }
        loadNextPage()
    }

    /**
     * Fetched per tab, alongside the first page rather than when the sheet opens: the filter button
     * should not be a thing that pauses before it does anything.
     */
    private fun loadFilterOptions() {
        val tab = _state.value.tab
        viewModelScope.launch {
            val options = repository.loadFilterOptions(libraryId, tab)
            // The user can change tabs while this is in flight; the answer belongs to the tab it
            // was asked for.
            _state.update { if (it.tab == tab) it.copy(filterOptions = options) else it }
        }
    }
}
