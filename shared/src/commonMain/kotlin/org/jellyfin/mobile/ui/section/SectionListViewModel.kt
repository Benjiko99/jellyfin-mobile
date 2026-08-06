package org.jellyfin.mobile.ui.section

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.jellyfin.mobile.data.SECTION_PAGE_SIZE
import org.jellyfin.mobile.data.SectionRepository
import org.jellyfin.mobile.domain.MediaItem
import org.jellyfin.mobile.domain.SectionKind
import org.jellyfin.mobile.network.SessionExpiredException

data class SectionListUiState(
    val items: List<MediaItem> = emptyList(),
    val loadingFirstPage: Boolean = true,
    val loadingMore: Boolean = false,
    val endReached: Boolean = false,
    val totalCount: Int? = null,
    val error: String? = null,
    /** Set when a later page failed, so the footer can offer a retry without losing what loaded. */
    val loadMoreFailed: Boolean = false,
)

/**
 * The full, paged list behind a row's "More" action.
 *
 * Mirrors [org.jellyfin.mobile.ui.person.PersonCreditsViewModel] — a first page that owns the error
 * state, then appended pages that fail without discarding what is already on screen.
 */
class SectionListViewModel(
    private val kind: SectionKind,
    private val parentId: String?,
    private val repository: SectionRepository,
    private val onSessionExpired: () -> Unit,
) : ViewModel() {
    private val _state = MutableStateFlow(SectionListUiState())
    val state: StateFlow<SectionListUiState> = _state.asStateFlow()

    init {
        loadNextPage()
    }

    fun loadNextPage() {
        val current = _state.value
        if (current.loadingMore || current.endReached) return
        if (!current.loadingFirstPage && current.loadMoreFailed) return

        _state.value = current.copy(loadingMore = true, loadMoreFailed = false)

        viewModelScope.launch {
            runCatching { repository.loadPage(kind, parentId, startIndex = _state.value.items.size) }
                .onSuccess { page ->
                    val state = _state.value
                    _state.value = state.copy(
                        items = state.items + page.items,
                        loadingFirstPage = false,
                        loadingMore = false,
                        // A total of zero means the endpoint does not report one; keep whatever we
                        // already had rather than clearing the header.
                        totalCount = page.totalCount ?: state.totalCount,
                        endReached = page.endReached,
                        error = null,
                    )
                }
                .onFailure { error ->
                    if (error is SessionExpiredException) onSessionExpired()
                    val state = _state.value
                    _state.value = if (state.loadingFirstPage) {
                        state.copy(
                            loadingFirstPage = false,
                            loadingMore = false,
                            error = error.message ?: "Could not reach the server",
                        )
                    } else {
                        state.copy(loadingMore = false, loadMoreFailed = true)
                    }
                }
        }
    }

    /** Clears a failed page so [loadNextPage] will try again. */
    fun retry() {
        val current = _state.value
        _state.value = if (current.items.isEmpty()) {
            SectionListUiState()
        } else {
            current.copy(loadMoreFailed = false)
        }
        loadNextPage()
    }

    companion object {
        const val PAGE_SIZE = SECTION_PAGE_SIZE
    }
}
