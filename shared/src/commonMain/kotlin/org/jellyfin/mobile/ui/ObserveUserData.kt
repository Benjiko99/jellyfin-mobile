package org.jellyfin.mobile.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import org.jellyfin.mobile.data.UserDataStore
import org.jellyfin.mobile.domain.UserDataChange

/**
 * Keeps a screen in step with watched and favourite changes made anywhere else in the app.
 *
 * Called from a view model's `init`, so the collection lives as long as the screen's back stack
 * entry does — which is the point: the screens that go stale are the ones sitting *under* the one
 * the user is tapping on.
 *
 * [onChange] is handed every change, including ones about items this screen has never heard of. The
 * `applying` helpers in `domain/UserDataChange.kt` return their receiver untouched in that case, so
 * the caller does not have to filter first.
 */
fun ViewModel.observeUserData(store: UserDataStore, onChange: (UserDataChange) -> Unit) {
    viewModelScope.launch { store.changes.collect(onChange) }
}
