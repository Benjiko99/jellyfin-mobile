package org.jellyfin.mobile.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.jellyfin.mobile.data.LibrariesRepository
import org.jellyfin.mobile.domain.LibraryView

/**
 * The drawer's "Media" section.
 *
 * Loaded once per session, like [MenuLinksViewModel] and for the same reason: libraries are added
 * and renamed from the server's dashboard, which is not something to poll for. No loading or error
 * state — the section stays absent until the answer arrives, and the home screen behind the drawer
 * has already reported any real connection problem.
 *
 * The home screen fetches `/UserViews` too, to build its "Recently Added in …" rows. Two requests
 * for the same list, deliberately: sharing them would mean a cache with an invalidation policy, and
 * the drawer would then be as stale as whatever the home screen last fetched.
 */
class LibrariesViewModel(
    repository: LibrariesRepository,
) : ViewModel() {
    private val _libraries = MutableStateFlow<List<LibraryView>>(emptyList())
    val libraries: StateFlow<List<LibraryView>> = _libraries.asStateFlow()

    init {
        viewModelScope.launch {
            _libraries.value = repository.loadLibraries()
        }
    }
}
