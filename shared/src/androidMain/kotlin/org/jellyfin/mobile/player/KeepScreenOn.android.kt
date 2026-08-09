package org.jellyfin.mobile.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView

/**
 * Marks the composition's view rather than adding `FLAG_KEEP_SCREEN_ON` to the window.
 *
 * The framework ORs the flag up from any attached, visible view, so the two have the same effect on
 * the display — but a view flag goes away with the view. jellyfin-android set it on the window
 * (`utils/extensions/Window.kt`), which is the whole app's window and outlives the player it was
 * taken for.
 */
@Composable
actual fun KeepScreenOn(enabled: Boolean) {
    val view = LocalView.current

    // Two effects, because the value has to be read before we write it. Keyed on the view alone so
    // that toggling `enabled` does not re-capture — a second run would record the state we set
    // ourselves and restore that on the way out instead of what was there before the player.
    DisposableEffect(view) {
        val wasOn = view.keepScreenOn
        onDispose { view.keepScreenOn = wasOn }
    }
    SideEffect { view.keepScreenOn = enabled }
}
