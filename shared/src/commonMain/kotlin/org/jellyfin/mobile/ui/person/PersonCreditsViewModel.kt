package org.jellyfin.mobile.ui.person

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jellyfin.mobile.data.PersonRepository
import org.jellyfin.mobile.domain.Credit
import org.jellyfin.mobile.domain.CreditKind
import org.jellyfin.mobile.network.SessionExpiredException

internal const val CREDIT_PAGE_SIZE = 40

data class PersonCreditsUiState(
    val credits: List<Credit> = emptyList(),
    /** Null until the first page returns. */
    val totalCount: Int? = null,
    val loadingFirstPage: Boolean = true,
    val loadingMore: Boolean = false,
    /** Set when the first page fails; a later page failing keeps what is already on screen. */
    val error: String? = null,
    val loadMoreFailed: Boolean = false,
) {
    val endReached: Boolean
        get() = totalCount != null && credits.size >= totalCount
}

class PersonCreditsViewModel(
    private val personId: String,
    private val kind: CreditKind,
    private val repository: PersonRepository,
    private val onSessionExpired: () -> Unit,
) : ViewModel() {
    private val _state = MutableStateFlow(PersonCreditsUiState())
    val state: StateFlow<PersonCreditsUiState> = _state.asStateFlow()

    /** Guards against the scroll listener firing repeatedly while a page is already in flight. */
    private var loading = false

    init {
        loadNextPage()
    }

    fun loadNextPage() {
        val current = _state.value
        if (loading || current.endReached) return
        loading = true

        _state.update {
            if (it.credits.isEmpty()) {
                it.copy(loadingFirstPage = true, error = null)
            } else {
                it.copy(loadingMore = true, loadMoreFailed = false)
            }
        }

        viewModelScope.launch {
            val startIndex = _state.value.credits.size
            runCatching {
                repository.loadCreditPage(
                    personId = personId,
                    kind = kind,
                    startIndex = startIndex,
                    limit = CREDIT_PAGE_SIZE,
                )
            }.fold(
                onSuccess = { page ->
                    _state.update { state ->
                        state.copy(
                            // Append by id to survive a page boundary shifting under us.
                            credits = (state.credits + page.credits).distinctBy { it.id },
                            totalCount = page.totalCount,
                            loadingFirstPage = false,
                            loadingMore = false,
                            error = null,
                            loadMoreFailed = false,
                        )
                    }
                },
                onFailure = { error ->
                    if (error is SessionExpiredException) onSessionExpired()
                    _state.update { state ->
                        if (state.credits.isEmpty()) {
                            state.copy(
                                loadingFirstPage = false,
                                error = error.message ?: "Could not load credits",
                            )
                        } else {
                            // Keep what loaded and offer a retry rather than blanking the screen.
                            state.copy(loadingMore = false, loadMoreFailed = true)
                        }
                    }
                },
            )
            loading = false
        }
    }

    fun retry() {
        if (_state.value.credits.isEmpty()) {
            _state.value = PersonCreditsUiState()
        }
        loadNextPage()
    }
}
