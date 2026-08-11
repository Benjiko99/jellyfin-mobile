package org.jellyfin.mobile.player

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.window.WindowPlacement
import org.jellyfin.mobile.LocalWindowState

/**
 * Full screen, which is what "immersive" means when there are no system bars to hide: the window
 * takes the whole display and the desktop's own furniture — title bar, taskbar, dock, menu bar —
 * goes with it.
 *
 * **[fullscreen] alone, and [controlsVisible] deliberately ignored.** Android hides its bars whenever
 * the controls go away, which is right for a strip of screen and wrong for a whole window: the
 * controls time out four seconds into playback, so following them would have the window seize the
 * display without being asked and give it back on the next click. Here it moves when the fullscreen
 * control is pressed and at no other time.
 *
 * **Through [LocalWindowState], not the window.** Setting `ComposeWindow.placement` does go full
 * screen, and coming back leaves the window the wrong size — the frame shrinks while the content
 * carries on laid out for the whole display, with the controls somewhere past the bottom edge. The
 * state is what Compose watches and what remembers the size and position to restore; changing the
 * window underneath it leaves the two disagreeing, and the window loses. Writing state also puts the
 * change on the next frame rather than partway through this one, which is what stopped the earlier
 * version crashing with "Reentry into ignoringRedrawRequests is not allowed".
 */
@Composable
actual fun ImmersiveMode(controlsVisible: Boolean, fullscreen: Boolean) {
    // Null in a test harness, and in nothing else: `MainWindow` provides it around the whole app.
    val window = LocalWindowState.current ?: return

    // Keyed rather than a `SideEffect` for the reason the Android actual gives: the player
    // recomposes as the position ticks, and this is a request to the windowing system.
    DisposableEffect(window, fullscreen) {
        // Captured before the change so a window the user had already maximised, or full-screened
        // themselves, is handed back the way they left it.
        val previous = window.placement
        window.placement = if (fullscreen) WindowPlacement.Fullscreen else WindowPlacement.Floating

        onDispose { window.placement = previous }
    }
}

/**
 * Empty. A desktop window has no status bar and no navigation bar, and the insets it does have —
 * anything the window manager reserves — are already outside the window Compose draws into, so there
 * is nothing here for the player's controls to keep clear of.
 */
@Composable
actual fun barInsetsIgnoringVisibility(): WindowInsets = WindowInsets(0, 0, 0, 0)
