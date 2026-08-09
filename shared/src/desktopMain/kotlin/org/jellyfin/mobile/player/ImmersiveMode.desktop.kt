package org.jellyfin.mobile.player

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import java.awt.Frame
import java.awt.KeyboardFocusManager
import java.awt.Window

/**
 * Full screen, which is what "immersive" means when there are no system bars to hide: the window
 * takes the whole display and the desktop's own furniture — title bar, taskbar, dock, menu bar —
 * goes with it.
 *
 * Reached through AWT rather than through Compose's `WindowState.placement` because the state
 * belongs to the `Window` composable in [org.jellyfin.mobile.MainWindow], and the player is several
 * screens below it with no route to it. Plumbing one down would put a desktop-shaped parameter
 * through shared navigation; asking AWT for the window that currently has focus does not, and the
 * window in question is the one the player is being drawn in.
 *
 * `fullScreenWindow` is full-screen *exclusive* mode where the platform offers it and a resize to
 * the screen's bounds where it does not, which is the same outcome from the user's side.
 */
@Composable
actual fun ImmersiveMode(enabled: Boolean) {
    // Keyed rather than a `SideEffect` for the reason the Android actual gives: the player
    // recomposes as the position ticks, and this is a call out to the windowing system.
    DisposableEffect(enabled) {
        val window = activeWindow()
        val device = window?.graphicsConfiguration?.device

        // Captured before the change so a screen that was already full screen — someone who
        // full-screened the app themselves — is handed back that way rather than restored to a
        // window they never asked for.
        val previous = device?.fullScreenWindow
        device?.fullScreenWindow = if (enabled) window else null

        onDispose { device?.fullScreenWindow = previous }
    }
}

/**
 * The focused window, or the first one showing.
 *
 * The fallback covers the moment the player is disposed while the app is in the background, when
 * there is no active window and the restore above would otherwise be skipped — leaving a full-screen
 * window behind after the player has gone.
 */
private fun activeWindow(): Window? =
    KeyboardFocusManager.getCurrentKeyboardFocusManager().activeWindow
        ?: Frame.getFrames().firstOrNull { frame -> frame.isShowing }

/**
 * Empty. A desktop window has no status bar and no navigation bar, and the insets it does have —
 * anything the window manager reserves — are already outside the window Compose draws into, so there
 * is nothing here for the player's controls to keep clear of.
 */
@Composable
actual fun barInsetsIgnoringVisibility(): WindowInsets = WindowInsets(0, 0, 0, 0)
