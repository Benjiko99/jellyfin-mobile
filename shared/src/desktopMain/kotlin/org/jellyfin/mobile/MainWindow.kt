package org.jellyfin.mobile

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.ApplicationScope
import androidx.compose.ui.window.FrameWindowScope
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.rememberWindowState
import org.jellyfin.mobile.resources.Res
import org.jellyfin.mobile.resources.app_name
import org.jellyfin.mobile.storage.dataStoreDirectory
import org.jellyfin.mobile.ui.App
import org.jetbrains.compose.resources.stringResource
import java.awt.Rectangle
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent

/**
 * The window's own state, offered to whatever is drawn inside it.
 *
 * The player is the one thing below here that has to move the window rather than draw in it, and it
 * is several screens down: carrying a parameter to it would put a desktop-shaped argument through
 * shared navigation, and reaching for the focused AWT window instead — which is what this replaced —
 * changes the window behind Compose's back. `WindowState` is the thing Compose watches, so writing
 * to it is the only way a change is followed through: placement, and the size and position to put
 * back when the placement is undone.
 *
 * Null where nothing provides it, which is a test harness rather than the app.
 *
 * A CompositionLocal is an implicit dependency and ktlint asks for a reason before allowing one;
 * this one's is in `.editorconfig` beside the allowlist that names it.
 */
internal val LocalWindowState = staticCompositionLocalOf<WindowState?> { null }

/**
 * The app's window, here rather than in `:desktopApp` for the same reason `MainViewController` is
 * here rather than in `iosApp/`: the entry point module is the platform's `main()` and nothing else,
 * and the title is a translated string that only this module can read (`Res` is internal to it).
 *
 * A window has no modifier and no state to hoist — it is the composition root, the same exemption
 * [org.jellyfin.mobile.ui.App] takes.
 */
@Suppress("ktlint:compose:modifier-missing-check")
@Composable
fun ApplicationScope.MainWindow() {
    // Large enough for the home screen's rows to show more than one card without being told to, and
    // centred because the platform default stacks successive windows down and right from a corner —
    // which for a single-window app just means "not where you were looking".
    val state = rememberWindowState(
        size = DpSize(1280.dp, 800.dp),
        position = WindowPosition(Alignment.Center),
    )

    Window(
        onCloseRequest = ::exitApplication,
        title = stringResource(Res.string.app_name),
        state = state,
    ) {
        // Windows opens a window behind whatever the user was looking at often enough to be worth
        // asking: a launched application that arrives unfocused takes a click before it will accept
        // a keystroke, and the keystroke it most obviously wants is the one that starts playback.
        LaunchedEffect(Unit) {
            window.toFront()
            window.requestFocus()
        }

        RestoreBoundsAfterFullscreen(state)

        CompositionLocalProvider(LocalWindowState provides state) {
            App(dataStoreDirectory())
        }
    }
}

/**
 * Puts the window back where it was when it leaves fullscreen, because Compose puts it back wrong.
 *
 * On a display with fractional scaling, returning from fullscreen applies the remembered size as
 * though it were **physical** pixels. A window that was 1280x800 logical — 1600x1000 as Windows
 * counts them at 125% — comes back 1280x800 as Windows counts them, four fifths of the size it left
 * at. Compose then lays out for the 1280x800 it still believes it has and draws 1578 pixels of
 * content into 1262 pixels of window, so everything is a quarter too large and the bottom of the
 * screen is past the bottom of the frame. The position is lost the same way.
 *
 * Not ours, and not really Compose's either: `WindowPlacement.Fullscreen` reaches Skiko's
 * `PlatformOperations.setFullscreen`, which on Windows and Linux is AWT's full-screen *exclusive*
 * mode — `GraphicsDevice.setFullScreenWindow(window)`, then `(null)` to leave — and it is the JDK's
 * restore of the bounds it saved that loses the scale. macOS goes through `osxSetFullscreenNative`
 * instead, which is a different mechanism with its own animation, so it is left alone here.
 *
 * Filed upstream as [CMP-10649](https://youtrack.jetbrains.com/issue/CMP-10649/). **Delete all of
 * this when that is fixed** — including the tracking of [floatingBounds], which exists only to feed
 * it.
 *
 * Nothing the app can query shows this. `WindowState.size`, the AWT frame, `LocalWindowInfo`'s
 * container and the density all agree with each other and all disagree with the window Windows
 * actually has; it took `GetWindowRect` to see it. Hence a workaround after the fact rather than a
 * correction to whatever computes the size.
 *
 * **And hence the extra pixel.** AWT is one of the things that believes the window is already the
 * right size, so asking it for that size does nothing at all — no native call, no correction. It has
 * to be asked for a size it thinks is new before being asked for the one that is wanted, which is
 * two frames of being a pixel out and then correct.
 */
@Composable
private fun FrameWindowScope.RestoreBoundsAfterFullscreen(state: WindowState) {
    if (System.getProperty("os.name").orEmpty().startsWith("Mac")) return

    // Where the window was before it went full screen. Tracked as it moves rather than read when it
    // is needed, because by the time it is needed the window is already the wrong size.
    var floatingBounds by remember { mutableStateOf(window.bounds) }

    DisposableEffect(window) {
        val listener = object : ComponentAdapter() {
            override fun componentResized(event: ComponentEvent) = record()

            override fun componentMoved(event: ComponentEvent) = record()

            private fun record() {
                if (state.placement == WindowPlacement.Floating) floatingBounds = window.bounds
            }
        }

        window.addComponentListener(listener)
        onDispose { window.removeComponentListener(listener) }
    }

    LaunchedEffect(state.placement) {
        if (state.placement != WindowPlacement.Floating) return@LaunchedEffect

        // After Compose has applied its own idea, so this is the last word rather than the first.
        withFrameNanos { }

        val wanted = floatingBounds
        window.bounds = Rectangle(wanted.x, wanted.y, wanted.width + 1, wanted.height + 1)
        withFrameNanos { }
        window.bounds = Rectangle(wanted)
    }
}
