package org.jellyfin.mobile.player

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.awt.ComposeWindow
import androidx.compose.ui.window.WindowPlacement
import java.awt.Frame
import java.awt.KeyboardFocusManager
import javax.swing.SwingUtilities

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
 * Reached through the window rather than through Compose's `WindowState`, which belongs to the
 * `Window` composable in [org.jellyfin.mobile.MainWindow]: the player is several screens below it
 * with no route to that state, and plumbing one down would put a desktop-shaped parameter through
 * shared navigation. [ComposeWindow.placement] is the same property `WindowState` drives, set on the
 * window that currently has focus — which is the one the player is being drawn in.
 *
 * Compose's own placement rather than AWT's full-screen exclusive mode. `GraphicsDevice`'s version
 * hides and re-shows the window, which recreates the Direct3D swapchain under Skiko and is a
 * different thing on each platform; this is the path Compose Desktop maintains.
 */
@Composable
actual fun ImmersiveMode(controlsVisible: Boolean, fullscreen: Boolean) {
    // Keyed rather than a `SideEffect` for the reason the Android actual gives: the player
    // recomposes as the position ticks, and this is a call out to the windowing system.
    DisposableEffect(fullscreen) {
        val window = activeComposeWindow()

        // Captured before the change so a window the user had already maximised, or full-screened
        // themselves, is handed back the way they left it.
        val previous = window?.placement

        // Queued rather than done here, which is the whole reason this comment exists. A
        // `DisposableEffect` body runs while Compose is applying changes, and applying changes
        // happens inside a render pass; resizing the window from there re-enters the renderer
        // synchronously and Compose stops the build with "Reentry into ignoringRedrawRequests is not
        // allowed". Going through the event queue lets the current frame finish first.
        SwingUtilities.invokeLater {
            window?.placement = if (fullscreen) WindowPlacement.Fullscreen else WindowPlacement.Floating
        }

        onDispose {
            SwingUtilities.invokeLater { window?.placement = previous ?: WindowPlacement.Floating }
        }
    }
}

/**
 * The focused window, or the first one showing.
 *
 * The fallback covers the moment the player is disposed while the app is in the background, when
 * there is no active window and the restore above would otherwise be skipped — leaving a full-screen
 * window behind after the player has gone.
 */
private fun activeComposeWindow(): ComposeWindow? =
    KeyboardFocusManager.getCurrentKeyboardFocusManager().activeWindow as? ComposeWindow
        ?: Frame.getFrames().filterIsInstance<ComposeWindow>().firstOrNull { frame -> frame.isShowing }

/**
 * Empty. A desktop window has no status bar and no navigation bar, and the insets it does have —
 * anything the window manager reserves — are already outside the window Compose draws into, so there
 * is nothing here for the player's controls to keep clear of.
 */
@Composable
actual fun barInsetsIgnoringVisibility(): WindowInsets = WindowInsets(0, 0, 0, 0)
