package org.jellyfin.mobile.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.jellyfin.mobile.data.MenuLinksRepository
import org.jellyfin.mobile.domain.MenuLink

/**
 * The navigation drawer's server-configured links.
 *
 * No loading or error state: [MenuLinksRepository] answers with an empty list rather than failing,
 * and the drawer's own rows are ready without it. The links appear when they arrive — a spinner in
 * a drawer for a request most servers answer with nothing would be worse than the small pop.
 *
 * Loaded once per session rather than per drawer opening. `config.json` changes when an
 * administrator edits a file on the server, which is not something to poll for.
 */
class MenuLinksViewModel(
    repository: MenuLinksRepository,
) : ViewModel() {
    private val _links = MutableStateFlow<List<MenuLink>>(emptyList())
    val links: StateFlow<List<MenuLink>> = _links.asStateFlow()

    init {
        viewModelScope.launch {
            _links.value = repository.loadMenuLinks()
        }
    }
}
