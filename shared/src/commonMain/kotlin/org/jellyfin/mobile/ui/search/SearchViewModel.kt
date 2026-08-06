package org.jellyfin.mobile.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jellyfin.mobile.data.SearchRepository
import org.jellyfin.mobile.domain.HomeSection
import org.jellyfin.mobile.domain.MediaItem
import org.jellyfin.mobile.network.SessionExpiredException
import kotlin.coroutines.coroutineContext

/**
 * How long typing has to pause before a query is sent.
 *
 * Every search is a five request fan-out, so this is the difference between one of those per word
 * and one per keystroke.
 */
private const val SEARCH_DEBOUNCE_MS = 300L

/** What the screen shows below the search field. */
sealed interface SearchContent {
    data object Loading : SearchContent

    /** The resting state, before anything has been typed. May be empty on a fresh account. */
    data class Suggestions(val items: List<MediaItem>) : SearchContent

    /**
     * One row per category that matched. Empty means the search found nothing at all — a category
     * with no matches yields no row rather than an empty one.
     *
     * [term] is carried so the screen can name what it found nothing for, and so the rows can be
     * rebuilt from scratch on a new query instead of inheriting the previous one's scroll position.
     */
    data class Results(val term: String, val sections: List<HomeSection>) : SearchContent

    data class Error(val message: String) : SearchContent
}

data class SearchUiState(
    /** Exactly what is in the field, untrimmed — this is the text the user is editing. */
    val query: String = "",
    val content: SearchContent = SearchContent.Loading,
)

class SearchViewModel(
    private val repository: SearchRepository,
    /** See [org.jellyfin.mobile.ui.home.SectionsViewModel]; a persisted token can be revoked. */
    private val onSessionExpired: () -> Unit,
) : ViewModel() {
    private val _state = MutableStateFlow(SearchUiState())
    val state: StateFlow<SearchUiState> = _state.asStateFlow()

    /**
     * Recommendations do not depend on the query, so they are fetched once and kept — clearing the
     * field to go back to them should not cost a request.
     */
    private var suggestions: List<MediaItem>? = null

    /** The term the in-flight (or last finished) load is for, so re-typing the same one is free. */
    private var loadedTerm: String? = null

    private var loadJob: Job? = null

    init {
        load(term = "", debounce = false)
    }

    fun onQueryChange(value: String) {
        _state.update { it.copy(query = value) }

        // Leading and trailing space is noise the server would search on, and trimming here means
        // "batman" and "batman " are one query rather than two.
        val term = value.trim()
        if (term == loadedTerm) return
        load(term, debounce = term.isNotEmpty())
    }

    fun retry() = load(loadedTerm.orEmpty(), debounce = false)

    private fun load(term: String, debounce: Boolean) {
        loadedTerm = term
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            // Cancelled by the next keystroke, which is what makes this a debounce rather than a
            // delay on every search.
            if (debounce) delay(SEARCH_DEBOUNCE_MS)

            _state.update { it.copy(content = SearchContent.Loading) }

            val result = runCatching {
                if (term.isEmpty()) {
                    SearchContent.Suggestions(
                        suggestions ?: repository.loadSuggestions().also { suggestions = it },
                    )
                } else {
                    SearchContent.Results(term, repository.search(term))
                }
            }

            // runCatching swallows cancellation too. Without this a superseded search would write
            // "Job was cancelled" over the state its replacement had already claimed.
            coroutineContext.ensureActive()

            _state.update {
                it.copy(
                    content = result.getOrElse { error ->
                        if (error is SessionExpiredException) onSessionExpired()
                        SearchContent.Error(error.message ?: "Could not reach the server")
                    },
                )
            }
        }
    }
}
